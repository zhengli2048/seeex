const ACCESS_TOKEN_KEY = 'seeex.access_token'
const REFRESH_TOKEN_KEY = 'seeex.refresh_token'
const EMPLOYEE_ID_KEY = 'seeex.employee_id'
const TENANT_ID_KEY = 'seeex.tenant_id'
export function getAccessToken() { return localStorage.getItem(ACCESS_TOKEN_KEY) }
export function getRefreshToken() { return localStorage.getItem(REFRESH_TOKEN_KEY) }
export function saveTokens(accessToken: string, refreshToken?: string) { localStorage.setItem(ACCESS_TOKEN_KEY, accessToken); if (refreshToken) localStorage.setItem(REFRESH_TOKEN_KEY, refreshToken) }
export function saveUserContext(employeeId: string, tenantId: string) { localStorage.setItem(EMPLOYEE_ID_KEY, employeeId); localStorage.setItem(TENANT_ID_KEY, tenantId) }
export function getUserContext() { return { employeeId: localStorage.getItem(EMPLOYEE_ID_KEY), tenantId: localStorage.getItem(TENANT_ID_KEY) } }
export function clearAccessToken() { localStorage.removeItem(ACCESS_TOKEN_KEY); localStorage.removeItem(REFRESH_TOKEN_KEY); localStorage.removeItem(EMPLOYEE_ID_KEY); localStorage.removeItem(TENANT_ID_KEY) }
export function isAuthenticated() { return Boolean(getAccessToken()) }
