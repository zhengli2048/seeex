import axios, { AxiosError, type AxiosRequestConfig, type AxiosResponse } from 'axios'
import { clearAccessToken, getAccessToken, getRefreshToken, getUserContext, saveTokens } from './auth'
import { ApiError, normalizeApiError, notifyApiError, rememberSessionError, SESSION_EXPIRED_EVENT, type ErrorType, type FieldErrors } from './apiErrors'

export { ApiError, getApiMessage, normalizeApiError, notifyApiError } from './apiErrors'
export type { ErrorType, FieldErrors } from './apiErrors'
export interface ApiResponse<T> { code: string; message: string; data?: T; error_type?: ErrorType; request_id?: string; timestamp?: string; field_errors?: FieldErrors }
export interface PageResult<T> { items: T[]; total: number }
export interface DynamicMenu { appCode: string; appName: string; baseUrl: string; route: { routeCode: string; name: string; path: string; permissionCode?: string; icon?: string; openType: string; visible: boolean; sortOrder: number } }

const baseURL = import.meta.env.VITE_API_BASE_URL ?? '/api'
export const api = axios.create({ baseURL, timeout: 20_000, headers: { 'Content-Type': 'application/json' } })
const refreshClient = axios.create({ baseURL, timeout: 20_000, headers: { 'Content-Type': 'application/json' } })
let refreshPromise: Promise<string> | null = null
let terminalSessionError: ApiError | null = null
type RetryConfig = AxiosRequestConfig & { _retry?: boolean; _sessionToken?: string | null }

function responseError(response: AxiosResponse): ApiError | undefined {
  const body = response.data as Partial<ApiResponse<unknown>> | null | undefined
  if (!body || typeof body.code !== 'string' || body.code === 'OK') return undefined
  return normalizeApiError(new AxiosError(body.message, body.code, response.config, response.request, response))
}

async function refreshAccessToken(): Promise<string> {
  const refreshToken = getRefreshToken()
  if (!refreshToken) throw new ApiError('登录状态已失效，请重新登录', { status: 401, errorType: 'AUTHENTICATION' })
  if (!refreshPromise) {
    refreshPromise = refreshClient.post('/auth/refresh', { refreshToken })
      .then((response) => {
        const failure = responseError(response)
        if (failure) throw failure
        const body = response.data as ApiResponse<{ accessToken: string; refreshToken?: string }> | { accessToken: string; refreshToken?: string }
        const tokenBody = (body && 'data' in body ? body.data : body) as { accessToken?: string; refreshToken?: string } | undefined
        if (!tokenBody?.accessToken) throw new ApiError('登录状态已失效，请重新登录', { status: 401, errorType: 'AUTHENTICATION' })
        // 登出或重新登录期间到达的刷新响应不能恢复旧会话。
        if (getRefreshToken() !== refreshToken) throw new ApiError('登录状态已变更', { code: 'SESSION_CHANGED', suppressNotification: true })
        saveTokens(tokenBody.accessToken, tokenBody.refreshToken)
        return tokenBody.accessToken
      })
      .catch((failure: unknown) => {
        if (getRefreshToken() !== refreshToken) throw new ApiError('登录状态已变更', { code: 'SESSION_CHANGED', suppressNotification: true })
        throw failure
      })
      .finally(() => { refreshPromise = null })
  }
  return refreshPromise
}

function expireSession(error: ApiError): ApiError {
  error.suppressNotification = true
  if (terminalSessionError) return terminalSessionError
  terminalSessionError = error
  const notice = new ApiError('登录状态已失效，请重新登录', { code: error.code, requestId: error.requestId, status: 401, errorType: 'AUTHENTICATION' })
  clearAccessToken()
  window.dispatchEvent(new Event(SESSION_EXPIRED_EVENT))
  if (window.location.pathname === '/login') {
    notifyApiError(notice)
  } else {
    if (!rememberSessionError(notice)) notifyApiError(notice)
    window.location.assign('/login')
  }
  return error
}

async function handleFailure(error: ApiError, config?: RetryConfig): Promise<AxiosResponse> {
  const isAuthRequest = /(^|\/)auth(?:\/|$)/.test(config?.url?.split(/[?#]/)[0] ?? '')
  const isUnauthorized = error.status === 401 || error.errorType === 'AUTHENTICATION'
  if (!isUnauthorized || isAuthRequest) throw error
  if (!config || terminalSessionError || (config._retry && config._sessionToken === getAccessToken())) throw expireSession(error)
  config._retry = true
  let accessToken: string
  try {
    // 较晚返回的旧 token 401 直接复用已刷新的 token，避免重复轮换。
    const currentToken = getAccessToken()
    accessToken = currentToken && currentToken !== config._sessionToken ? currentToken : await refreshAccessToken()
  } catch (failure) {
    const normalized = normalizeApiError(failure)
    if (normalized.code === 'SESSION_CHANGED') throw normalized
    throw expireSession(normalized)
  }
  config.headers = config.headers ?? {}
  config.headers.Authorization = `Bearer ${accessToken}`
  // 重试后的 401 会在下一次拦截时终止会话；其他错误保留原始反馈。
  return api.request(config)
}

api.interceptors.request.use((config) => {
  const token = getAccessToken()
  const context = getUserContext()
  if (token && terminalSessionError) terminalSessionError = null
  const sessionConfig = config as RetryConfig
  sessionConfig._sessionToken = token
  if (token) config.headers.Authorization = `Bearer ${token}`
  if (context.employeeId) config.headers['X-User-Id'] = context.employeeId
  if (context.tenantId) config.headers['X-Tenant-Id'] = context.tenantId
  return config
})

api.interceptors.response.use((response) => {
  const failure = responseError(response)
  return failure ? handleFailure(failure, response.config as RetryConfig) : response
}, async (error: unknown) => {
  // CSV 下载失败仍可返回 JSON 错误；先解码，保留后端的导出上限等具体原因。
  if (axios.isAxiosError(error) && error.response?.data instanceof Blob && error.response.data.type.includes('json')) {
    try { error.response.data = JSON.parse(await error.response.data.text()) as unknown } catch { /* 非JSON内容继续使用状态码提示。 */ }
  }
  return handleFailure(normalizeApiError(error), axios.isAxiosError(error) ? error.config as RetryConfig | undefined : undefined)
})

export async function request<T>(promise: Promise<{ data: ApiResponse<T> }>) {
  const response = await promise
  return response.data.data as T
}
