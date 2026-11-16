import { Card, Spin, Button } from '@arco-design/web-react'
import { IconRefresh } from '@arco-design/web-react/icon'
import type { ReactNode } from 'react'

/** 通用页头容器：标题 + 描述 + 操作区 + loading/error/forbidden 态。 */
export function PageShell(props: {
  title: string
  description?: string
  breadcrumb?: string
  actions?: ReactNode
  loading?: boolean
  error?: string
  forbidden?: boolean
  onRetry?: () => void
  children: ReactNode
}) {
  const { title, description, breadcrumb, actions, loading, error, forbidden, onRetry, children } = props
  return (
    <div>
      <div className="page-heading">
        <div>
          {breadcrumb && <div className="eyebrow">{breadcrumb}</div>}
          <h1>{title}</h1>
          {description && <p className="muted">{description}</p>}
        </div>
        <div style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
          {onRetry && <Button icon={<IconRefresh />} onClick={onRetry}>重试</Button>}
          {actions}
        </div>
      </div>
      {forbidden
        ? <Card><div className="state error">暂无权限访问此页面</div></Card>
        : loading
          ? <Card><div className="state"><Spin /></div></Card>
          : error
            ? <Card><div className="state error">{error}</div></Card>
            : children}
    </div>
  )
}
