import {Alert, Button, Card, Empty, Grid, Space, Spin, Tag} from '@arco-design/web-react'
import {useQuery} from '@tanstack/react-query'
import {api, getApiMessage, notifyApiError} from '../../lib/api'
import {openApplication} from '../../lib/management'
interface App { appCode: string; name: string; description?: string; release?: { version: string; routes: { routeCode: string; name: string; path: string; entry: 'WEB' | 'ADMIN'; category: string; openType: string }[] } }
export function DashboardPage() {
  const apps = useQuery({ queryKey: ['my-apps'], queryFn: async () => (await api.get<{ data: App[] }>('/me/apps')).data.data })
  const me = useQuery({ queryKey: ['me'], queryFn: async () => (await api.get<{ data: { displayName: string; permissions: string[] } }>('/me')).data.data })
  return <div>
    <div className="page-heading">
    <div>
    <h1>{me.data?.displayName ? `${me.data.displayName}，你好` : '工作台'}</h1>
    <p className="muted">从已授权的应用继续工作。</p>
    </div>
    <Button onClick={() => { void apps.refetch(); void me.refetch() }}>刷新</Button>
    </div>{me.isError && <Alert type="error" content={getApiMessage(me.error)} />}<Card>
    <Space>
    <Tag color="arcoblue">有效权限 {me.data?.permissions.length ?? 0}</Tag>
    <Tag>可访问应用 {apps.data?.length ?? 0}</Tag>
    </Space>
    </Card>{apps.isLoading ? <Spin /> : apps.isError ? <Alert className="section-card" type="error" content={getApiMessage(apps.error)} /> : !apps.data?.length ? <Card className="section-card">
    <Empty description="暂无已授权应用，请联系管理员分配资源权限" />
    </Card> : <Grid.Row gutter={[16, 16]} className="section-card">{apps.data.map((app) => <Grid.Col key={app.appCode} xs={24} md={12} xl={8}>
    <Card className="application-card" title={app.name} extra={<Tag>{app.release?.version}</Tag>}>
    <p className="muted">{app.description || '选择入口访问应用'}</p>
    <Space wrap>{app.release?.routes.map((route) => <Button key={route.routeCode} onClick={() => void openApplication(app.appCode, route.path, route.entry, route.openType).catch((error) => notifyApiError(error))}>{route.entry === 'ADMIN' ? '后台' : 'Web'} · {route.name}</Button>)}</Space>
    </Card>
    </Grid.Col>)}</Grid.Row>}</div>
}
