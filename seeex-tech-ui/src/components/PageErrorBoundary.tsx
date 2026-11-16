import { Button, Result, Space } from '@arco-design/web-react'
import { useState } from 'react'
import { isRouteErrorResponse, useLocation, useNavigate, useRevalidator, useRouteError } from 'react-router-dom'
import { ApiError } from '../lib/apiErrors'

/** 路由加载和组件渲染失败使用受控错误页，不向用户暴露异常内容或堆栈。 */
export function PageErrorBoundary({ notFound = false }: { notFound?: boolean }) {
  const error = useRouteError()
  const location = useLocation()
  const navigate = useNavigate()
  const revalidator = useRevalidator()
  const [retrying, setRetrying] = useState(false)
  const [retryFailed, setRetryFailed] = useState(false)
  const status = notFound ? 404 : isRouteErrorResponse(error) ? error.status : error instanceof ApiError ? error.status : undefined
  const requestId = error instanceof ApiError && error.requestId && /^[A-Za-z0-9._:-]{1,128}$/.test(error.requestId) ? error.requestId : undefined
  const title = status === 404 ? '页面不存在' : status === 401 ? '登录状态已失效' : status === 403 ? '暂无页面访问权限' : '页面暂时无法显示'
  const message = status === 404 ? '页面可能已移动，或访问地址有误。' : status === 401 ? '请重新登录后继续访问。' : status === 403 ? '请联系管理员确认当前账号的访问权限。' : '请重试加载；如仍无法恢复，可以刷新页面或返回工作台。'

  async function retry() {
    setRetrying(true)
    setRetryFailed(false)
    try {
      await revalidator.revalidate()
      // 同址导航生成新 location，使纯组件渲染错误也可以重新挂载。
      await navigate(location.pathname + location.search + location.hash, { replace: true })
    } catch {
      setRetryFailed(true)
    } finally {
      setRetrying(false)
    }
  }

  return <main role={status === 404 ? 'status' : 'alert'} aria-live="polite" style={{ minHeight: '70vh', display: 'grid', placeItems: 'center', padding: 24 }}>
    <Result status={status === 404 ? '404' : status === 403 ? '403' : status === 401 ? 'warning' : '500'} title={title} subTitle={<Space direction="vertical">
      <span>{message}</span>
      {retryFailed && <span>重试未能完成，请刷新当前页面。</span>}
      {requestId && <span>请求编号：{requestId}</span>}
    </Space>} extra={<Space wrap>
      {status !== 404 && <Button type="primary" loading={retrying || revalidator.state === 'loading'} onClick={() => void retry()}>重试加载</Button>}
      <Button onClick={() => window.location.reload()}>刷新当前页</Button>
      <Button type={status === 404 ? 'primary' : 'secondary'} onClick={() => void navigate('/', { replace: true })}>返回工作台</Button>
      <Button onClick={() => void navigate('/login', { replace: true })}>前往登录</Button>
    </Space>} />
  </main>
}
