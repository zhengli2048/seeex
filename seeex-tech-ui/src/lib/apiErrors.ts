import axios from 'axios'
import { Message } from '@arco-design/web-react'

export type ErrorType = 'VALIDATION' | 'AUTHENTICATION' | 'AUTHORIZATION' | 'NOT_FOUND' | 'CONFLICT' | 'RATE_LIMITED' | 'BUSINESS' | 'INTERNAL' | 'NETWORK_ERROR'
export type FieldErrors = Record<string, string[]>

interface ApiErrorDetails {
  code?: string
  errorType?: ErrorType
  status?: number
  requestId?: string
  fieldErrors?: FieldErrors
  suppressNotification?: boolean
}

/** HTTP 失败与 HTTP 200 中的业务失败使用相同的错误结构。 */
export class ApiError extends Error {
  readonly code?: string
  readonly errorType?: ErrorType
  readonly status?: number
  readonly requestId?: string
  readonly fieldErrors?: FieldErrors
  suppressNotification: boolean

  constructor(message: string, details: ApiErrorDetails = {}) {
    super(message)
    this.name = 'ApiError'
    this.code = details.code
    this.errorType = details.errorType
    this.status = details.status
    this.requestId = details.requestId
    this.fieldErrors = details.fieldErrors
    this.suppressNotification = details.suppressNotification ?? false
  }
}

const defaultMessage = '请求失败，请稍后重试'
const normalizedErrors = new WeakMap<object, ApiError>()
const notifiedErrors = new WeakSet<ApiError>()
const recentNotifications = new Map<string, number>()
const notificationInterval = 4000
const errorTypes: ErrorType[] = ['VALIDATION', 'AUTHENTICATION', 'AUTHORIZATION', 'NOT_FOUND', 'CONFLICT', 'RATE_LIMITED', 'BUSINESS', 'INTERNAL', 'NETWORK_ERROR']

function textValue(value: unknown): string | undefined {
  return typeof value === 'string' && value.trim() ? value.trim() : undefined
}

function objectValue(value: unknown): Record<string, unknown> | undefined {
  return value && typeof value === 'object' && !Array.isArray(value) ? value as Record<string, unknown> : undefined
}

function readFieldErrors(value: unknown): FieldErrors | undefined {
  const fields = objectValue(value)
  if (!fields) return undefined
  const result: FieldErrors = {}
  Object.entries(fields).forEach(([field, messages]) => {
    const values = (Array.isArray(messages) ? messages : [messages]).map(textValue).filter((message): message is string => Boolean(message))
    if (values.length) result[field] = values
  })
  return Object.keys(result).length ? result : undefined
}

function statusMessage(status: number | undefined, code?: string): string {
  if (code === 'ECONNABORTED' || code === 'ETIMEDOUT') return '请求超时，请稍后重试'
  if (status === undefined) return '网络连接失败，请检查网络'
  if (status === 401) return '登录状态已失效，请重新登录'
  if (status === 403) return '暂无权限执行此操作'
  if (status === 404) return '请求的内容不存在或已删除'
  if (status === 409) return '数据已发生变化，请刷新后重试'
  if (status === 413) return '提交的内容过大，请调整后重试'
  if (status === 429) return '操作过于频繁，请稍后重试'
  if (status >= 500) return '服务暂时不可用，请稍后重试'
  return defaultMessage
}

export function normalizeApiError(error: unknown): ApiError {
  if (error instanceof ApiError) return error
  if (error && typeof error === 'object') {
    const existing = normalizedErrors.get(error)
    if (existing) return existing
  }

  let normalized: ApiError
  if (axios.isCancel(error)) {
    normalized = new ApiError('请求已取消', { code: 'ERR_CANCELED', suppressNotification: true })
  } else if (axios.isAxiosError(error)) {
    const body = objectValue(error.response?.data)
    const type = textValue(body?.error_type)
    const headers = error.response?.headers
    normalized = new ApiError(textValue(body?.message) ?? statusMessage(error.response?.status, error.code), {
      code: textValue(body?.code) ?? error.code,
      errorType: type && errorTypes.includes(type as ErrorType) ? type as ErrorType : error.response ? undefined : 'NETWORK_ERROR',
      status: error.response?.status,
      requestId: textValue(body?.request_id) ?? textValue(body?.requestId) ?? textValue(headers?.['x-request-id']) ?? textValue(headers?.['x-correlation-id']),
      // 当前后端尚未返回字段级错误；只在响应明确提供该结构时消费。
      fieldErrors: readFieldErrors(body?.field_errors ?? body?.fieldErrors),
    })
  } else {
    normalized = new ApiError(error instanceof Error ? error.message || defaultMessage : defaultMessage)
  }

  if (error && typeof error === 'object') normalizedErrors.set(error, normalized)
  return normalized
}

export function getApiMessage(error: unknown, fallback = defaultMessage): string {
  const message = normalizeApiError(error).message
  return message === defaultMessage ? fallback : message
}

/** Query/Mutation 全局回调与页面局部回调可重复调用，用户只收到一条提示。 */
export function notifyApiError(error: unknown, fallback = defaultMessage): ApiError {
  const normalized = normalizeApiError(error)
  if (normalized.suppressNotification || notifiedErrors.has(normalized)) return normalized
  notifiedErrors.add(normalized)
  const message = getApiMessage(normalized, fallback)
  const signature = `${normalized.status ?? ''}:${normalized.code ?? ''}:${message}`
  const now = Date.now()
  for (const [key, timestamp] of recentNotifications) {
    if (now - timestamp >= notificationInterval) recentNotifications.delete(key)
  }
  if (recentNotifications.has(signature)) return normalized
  recentNotifications.set(signature, now)
  Message.error({ content: normalized.requestId ? `${message}（请求编号：${normalized.requestId}）` : message, duration: 5000, closable: true })
  return normalized
}

export const SESSION_EXPIRED_EVENT = 'seeex:session-expired'
const sessionNoticeKey = 'seeex.session-expired-notice'

export function rememberSessionError(error: ApiError): boolean {
  try {
    sessionStorage.setItem(sessionNoticeKey, JSON.stringify({ message: error.message, code: error.code, requestId: error.requestId }))
    return true
  } catch {
    return false
  }
}

export function consumeSessionError(): ApiError | undefined {
  try {
    const stored = sessionStorage.getItem(sessionNoticeKey)
    sessionStorage.removeItem(sessionNoticeKey)
    if (!stored) return undefined
    const value = objectValue(JSON.parse(stored))
    const message = textValue(value?.message)
    if (!message) return undefined
    return new ApiError(message, { code: textValue(value?.code), requestId: textValue(value?.requestId), status: 401, errorType: 'AUTHENTICATION' })
  } catch {
    return undefined
  }
}
