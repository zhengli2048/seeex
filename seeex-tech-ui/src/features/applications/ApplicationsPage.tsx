import { Alert, Avatar, Button, Card, Descriptions, Drawer, Empty, Grid, Input, InputNumber, Message, Modal, Select, Space, Spin, Switch, Table, Tabs, Tag, Tree, TreeSelect } from '@arco-design/web-react'
import { IconPlus, IconRefresh } from '@arco-design/web-react/icon'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { api, getApiMessage, notifyApiError } from '../../lib/api'
import { loadAll, openApplication, usePermissions } from '../../lib/management'
import { EntityDrawer, type FieldSpec } from '../../components/EntityDrawer'

import { ApplicationGovernance } from './ApplicationGovernance'

interface Route { routeCode: string; name: string; path: string; permissionCode: string; icon: string; openType: string; visible: boolean; sortOrder: number; entry: 'WEB' | 'ADMIN'; category: string; parentRouteCode?: string | null }
interface Release { id: string; version: string; environment: string; published: boolean; approvalStatus: string; rolloutPercent: number; routes: Route[] }
interface App extends Record<string, unknown> { appCode: string; name: string; description?: string; type: string; logo?: string; status: string; ownerEmployeeId?: number; baseUrl: string; adminUrl?: string; apiUrl?: string; repositoryUrl?: string; ssoCallbackPath: string; release?: Release }
interface RouteNode { key: string; title: string; children: RouteNode[] }
interface Credentials { ciToken?: string; clientSecret?: string }
const blankRoute = (): Route => ({ routeCode: '', name: '', path: '/', permissionCode: '', icon: '', openType: 'NEW_TAB', visible: true, sortOrder: 0, entry: 'WEB', category: '常用功能', parentRouteCode: null })
const entryLabel = (entry: string) => entry === 'ADMIN' ? '管理后台' : 'Web 业务端'
const types = ['WEB', 'ADMIN', 'AI', 'OTHER']
async function copyCredential(value: string) {
  try { if (!navigator.clipboard) throw new Error('剪贴板不可用'); await navigator.clipboard.writeText(value); Message.success('已复制') } catch { Message.error('复制失败，请手动复制') }
}

/** 选择上级时排除自身和后代，使用字符串 key。 */
function parentTree(routes: Route[], current: Route): RouteNode[] {
  const blocked = new Set([current.routeCode])
  let changed = true
  while (changed) {
    changed = false
    routes.forEach((route) => { if (route.parentRouteCode && blocked.has(route.parentRouteCode) && !blocked.has(route.routeCode)) { blocked.add(route.routeCode); changed = true } })
  }
  const eligible = routes.filter((route) => route.routeCode && !blocked.has(route.routeCode) && route.entry === current.entry && route.category === current.category)
  if (new Set(eligible.map((route) => route.routeCode)).size !== eligible.length) return []
  for (const route of eligible) {
    const visited = new Set([route.routeCode])
    let parent = route.parentRouteCode
    while (parent) {
      if (visited.has(parent)) return []
      visited.add(parent); parent = eligible.find((item) => item.routeCode === parent)?.parentRouteCode
    }
  }
  const nodes = new Map(eligible.map((route) => [route.routeCode, { key: route.routeCode, title: route.name || route.routeCode, children: [] as RouteNode[] }]))
  const roots: RouteNode[] = []
  eligible.forEach((route) => { const node = nodes.get(route.routeCode)!; const parent = route.parentRouteCode ? nodes.get(route.parentRouteCode) : undefined; if (parent) parent.children.push(node); else roots.push(node) })
  return roots
}
function routePreview(routes: Route[]): RouteNode[] {
  if (new Set(routes.map((route) => route.routeCode)).size !== routes.length) return [{ key: 'invalid', title: '路由编码重复，请先修改', children: [] }]
  return (['WEB', 'ADMIN'] as const).flatMap((entry) => {
    const matching = routes.filter((route) => (route.entry ?? 'WEB') === entry)
    if (!matching.length) return []
    const categories = [...new Set(matching.map((route) => route.category || '常用功能'))]
    return [{ key: `entry:${entry}`, title: entryLabel(entry), children: categories.map((category) => {
      const grouped = matching.filter((route) => (route.category || '常用功能') === category).sort((a, b) => a.sortOrder - b.sortOrder)
      const codes = new Set(grouped.map((route) => route.routeCode))
      const build = (route: Route, visited: Set<string>): RouteNode => ({ key: `route:${route.routeCode}`, title: `${route.name} · ${route.path} · ${route.permissionCode || '自动生成权限'}${route.visible ? '' : '（菜单隐藏）'}`, children: grouped.filter((child) => child.parentRouteCode === route.routeCode && !visited.has(child.routeCode)).map((child) => build(child, new Set([...visited, child.routeCode]))) })
      return { key: `category:${entry}:${category}`, title: category, children: grouped.filter((route) => !route.parentRouteCode || !codes.has(route.parentRouteCode)).map((route) => build(route, new Set([route.routeCode]))) }
    }) }]
  })
}
function validateRoutes(version: string, routes: Route[], app: App | null): string | undefined {
  if (!/^[A-Za-z0-9._-]{1,128}$/.test(version)) return '版本号必填，仅允许字母、数字、点、下划线和连字符'
  const codes = new Set<string>(), paths = new Set<string>(), permissions = new Set<string>()
  for (const [index, route] of routes.entries()) {
    const prefix = `路由 ${index + 1}：`
    if (!/^[A-Za-z0-9._-]{1,64}$/.test(route.routeCode) || !route.name.trim() || !route.category.trim()) return prefix + '请填写有效编码、名称和菜单分类'
    if (!route.path.startsWith('/') || route.path.startsWith('//') || /[\\\r\n]/.test(route.path) || route.path.includes('..')) return prefix + '页面路径须为安全的站内路径'
    if (route.entry === 'ADMIN' && !app?.adminUrl) return prefix + '请先在应用基本信息中配置后台入口'
    if (codes.has(route.routeCode) || paths.has(`${route.entry}:${route.path}`)) return prefix + '编码或同端页面路径重复'
    const permission = route.permissionCode.trim() || `app:${app?.appCode}:${route.entry.toLowerCase()}:${route.routeCode}:view`
    if (permissions.has(permission)) return prefix + 'Web 与后台路由须使用不同权限编码'
    codes.add(route.routeCode); paths.add(`${route.entry}:${route.path}`); permissions.add(permission)
    const visited = new Set([route.routeCode])
    let parentCode = route.parentRouteCode
    while (parentCode) {
      const parent = routes.find((item) => item.routeCode === parentCode)
      if (!parent || visited.has(parentCode) || parent.entry !== route.entry || parent.category !== route.category) return prefix + '上级路由须存在、同端、同分类且不能成环'
      visited.add(parentCode); parentCode = parent.parentRouteCode
    }
  }
}

export function ApplicationsPage() {
  const permissions = usePermissions()
  const client = useQueryClient()
  const managed = useQuery({ queryKey: ['managed-applications'], queryFn: async () => (await api.get<{ data: string[] }>('/me/application-management')).data.data })
  const isAppAdmin = (code?: string) => Boolean(code && managed.data?.includes(code))
  const canManageApp = (code?: string) => permissions.can('platform:apps:update') || isAppAdmin(code)
  const [keyword, setKeyword] = useState('')
  const [status, setStatus] = useState<string>()
  const [editing, setEditing] = useState<Partial<App> | null>(null)
  const [detail, setDetail] = useState<App | null>(null)
  const [credentials, setCredentials] = useState<Credentials | null>(null)
  const [importing, setImporting] = useState(false)
  const [version, setVersion] = useState('')
  const [gitCommit, setGitCommit] = useState('')
  const [environment, setEnvironment] = useState('PROD')
  const [routes, setRoutes] = useState<Route[]>([blankRoute()])
  const [routeError, setRouteError] = useState<string>()
  const [rollouts, setRollouts] = useState<Record<string, number>>({})
  const apps = useQuery({ queryKey: ['platform-apps'], queryFn: async () => (await api.get<{ data: App[] }>('/platform/apps')).data.data, enabled: true })
  const owners = useQuery({ queryKey: ['app-owner-options'], queryFn: () => loadAll<{ id: number; displayName: string }>('/employees'), enabled: editing !== null && permissions.can('employees:view') })
  const versions = useQuery({ queryKey: ['app-versions', detail?.appCode], queryFn: async () => (await api.get<{ data: Release[] }>(`/platform/apps/${detail?.appCode}/route-metadata`)).data.data, enabled: detail !== null })
  // 保存已成功时，刷新失败单独反馈，避免误报成保存失败并重复提交。
  const refresh = async () => {
    await client.invalidateQueries()
    if (detail) { try { const r = await api.get<{ data: App }>(`/platform/apps/${detail.appCode}`); setDetail(r.data.data) } catch (error) { notifyApiError(error) } }
  }
  const save = useMutation({
    mutationFn: async (values: Record<string, unknown>) => {
      if (editing?.appCode) await api.put(`/platform/apps/${editing.appCode}`, values)
      else { const r = await api.post<{ data: Credentials }>('/platform/apps', values); setCredentials(r.data.data) }
    },
    onSuccess: async () => { setEditing(null); Message.success('应用已保存'); await refresh() },
    onError: (error) => notifyApiError(error),
  })
  const operation = useMutation({ mutationFn: ({ path, body }: { path: string; body?: unknown }) => api.post(`/platform/apps/${detail?.appCode}/${path}`, body),
    onSuccess: async (response) => { const result = (response.data as { data?: Credentials }).data; if (result?.ciToken || result?.clientSecret) setCredentials(result); setRollouts({}); Message.success('操作已保存'); await refresh() }, onError: (error) => notifyApiError(error) })
  const importRoutes = useMutation({ mutationFn: () => api.post(`/platform/apps/${detail?.appCode}/route-metadata/manual`, { version, gitCommit: gitCommit || null, environment, baseUrl: detail?.baseUrl, routes: routes.map((route) => ({ ...route, permissionCode: route.permissionCode.trim(), category: route.category.trim() })) }),
    onSuccess: async () => { setImporting(false); Message.success('版本已导入，请审核后发布'); await refresh() }, onError: (error) => notifyApiError(error) })
  const ownerOptions = (owners.data ?? []).map((row) => ({ label: row.displayName, value: row.id }))
  if (editing?.ownerEmployeeId && !ownerOptions.some((option) => option.value === editing.ownerEmployeeId)) ownerOptions.push({ label: `当前负责人 #${editing.ownerEmployeeId}`, value: editing.ownerEmployeeId })
  const fields: FieldSpec[] = [
    { name: 'appCode', label: '应用编码', required: true, readOnly: Boolean(editing?.appCode), placeholder: '如 recruitment（小写字母、数字、连字符）' },
    { name: 'name', label: '应用名称', required: true }, { name: 'description', label: '描述', type: 'textarea' },
    { name: 'type', label: '应用类型', type: 'select', required: true, options: types.map((value) => ({ label: value, value })) },
    { name: 'baseUrl', label: 'Web 页面入口', required: true, placeholder: 'https://app.example.com' },
    { name: 'adminUrl', label: '后台管理入口', placeholder: 'https://admin.example.com' },
    { name: 'apiUrl', label: '后端 API 地址', placeholder: 'https://api.example.com' },
    { name: 'ssoCallbackPath', label: 'SSO 回调路径', required: true, placeholder: '/sso/callback' },
    { name: 'repositoryUrl', label: '代码仓库地址' }, { name: 'logo', label: 'Logo 图片地址' },
    { name: 'ownerEmployeeId', label: '负责人', type: 'select', readOnly: !permissions.can('employees:view'), options: ownerOptions },
    { name: 'status', label: '状态', type: 'select', required: true, options: [{ label: '启用', value: 'ACTIVE' }, { label: '停用', value: 'INACTIVE' }] },
  ]
  function confirm(path: string, title: string, body?: unknown) { Modal.confirm({ title, content: '确认后立即生效。', onOk: () => operation.mutateAsync({ path, body }) }) }
  function changeRoute(index: number, changes: Partial<Route>) { setRouteError(undefined); setRoutes((previous) => previous.map((route, i) => i === index ? { ...route, ...changes } : route)) }
  if (permissions.isLoading) return <Spin />
  if (permissions.isError) return <Alert type="error" content={getApiMessage(permissions.error)} action={<Button onClick={() => void permissions.refetch()}>重试</Button>} />
  if (managed.isError) return <Alert type="error" content={getApiMessage(managed.error)} action={<Button onClick={() => void managed.refetch()}>重试</Button>} />
  if (!permissions.can('platform:apps:view') && !permissions.can('platform:apps:update') && !managed.isLoading && !managed.data?.length) return <Alert type="warning" content="暂无应用管理权限" />
  const rows = (apps.data ?? []).filter((app) => (!status || app.status === status) && `${app.name} ${app.appCode}`.toLowerCase().includes(keyword.toLowerCase()))
  const ownerError = permissions.can('employees:view') && owners.isError ? getApiMessage(owners.error) : undefined
  const dictionaryBusy = permissions.can('employees:view') && (owners.isLoading || owners.isError)
  const routeBusy = importRoutes.isPending
  return <div>
    <div className="page-heading"><div><h1>应用中心</h1><p className="muted">统一接入业务应用，分别管理 Web、后台菜单及服务端授权。</p></div><Space>
      <Button icon={<IconRefresh />} onClick={() => void refresh()}>刷新</Button>{permissions.can('platform:apps:create') && <Button type="primary" icon={<IconPlus />} onClick={() => { save.reset(); setEditing({ type: 'WEB', status: 'ACTIVE', ssoCallbackPath: '/sso/callback' }) }}>新增应用</Button>}
    </Space></div>
    <Card className="section-card"><Space wrap><Input.Search allowClear placeholder="搜索应用名称或编码" value={keyword} onChange={setKeyword} /><Select allowClear placeholder="应用状态" style={{ width: 150 }} value={status} onChange={setStatus} options={[{ label: '启用', value: 'ACTIVE' }, { label: '停用', value: 'INACTIVE' }]} /><Button onClick={() => void apps.refetch()}>查询</Button><Button onClick={() => { setKeyword(''); setStatus(undefined) }}>重置</Button></Space></Card>
    {apps.isLoading ? <Spin /> : apps.isError ? <Alert type="error" content={getApiMessage(apps.error)} action={<Button onClick={() => void apps.refetch()}>重试</Button>} /> : rows.length === 0 ? <Card className="section-card"><Empty description="暂无符合条件的应用" /></Card> : <Grid.Row gutter={[16, 16]} className="section-card">{rows.map((app) => <Grid.Col key={app.appCode} xs={24} md={12} xl={8}>
      <Card className="application-card" title={<Space><Avatar>{app.logo ? <img src={app.logo} alt="" /> : app.name.slice(0, 1)}</Avatar><span>{app.name}</span></Space>} extra={<Tag color={app.status === 'ACTIVE' ? 'green' : 'gray'}>{app.status === 'ACTIVE' ? '启用' : '停用'}</Tag>}>
        <p className="muted">{app.description || '暂无描述'}</p><p className="application-url">{app.baseUrl}</p><Space wrap><Tag>Web</Tag>{app.adminUrl && <Tag color="purple">管理后台</Tag>}<Tag>{app.release?.version ?? '未发布'}</Tag></Space>
        <Space className="section-card" wrap><Button onClick={() => { setRollouts({}); setDetail(app) }}>详情与接入</Button>{canManageApp(app.appCode) && <Button onClick={() => { save.reset(); setEditing(app) }}>编辑</Button>}</Space>
      </Card>
    </Grid.Col>)}</Grid.Row>}
    {editing && ownerError && <Alert type="error" content={`负责人选项加载失败：${ownerError}`} action={<Button onClick={() => void owners.refetch()}>重试</Button>} />}
    <EntityDrawer mode={editing?.appCode ? 'edit' : 'create'} title={editing?.appCode ? '编辑应用' : '新增应用'} visible={editing !== null} initialValues={editing ?? undefined} fields={fields} loading={save.isPending} submitDisabled={dictionaryBusy} topError={ownerError ?? (save.isError ? getApiMessage(save.error) : undefined)} onClose={() => setEditing(null)} onSubmit={(values) => { const submit = () => save.mutate(values); if (values.status === 'INACTIVE') Modal.confirm({ title: '停用此应用？', content: '停用后用户无法通过平台访问，应用服务鉴权也会拒绝。', onOk: submit }); else submit() }} />
    <Drawer width={960} title={detail?.name ?? '应用详情'} visible={detail !== null} footer={null} onCancel={() => { if (!operation.isPending) setDetail(null) }} maskClosable={!operation.isPending} closable={!operation.isPending} unmountOnExit>
      <Tabs defaultActiveTab="basic">
        <Tabs.TabPane key="basic" title="基本信息">
          <Descriptions column={1} border data={fields.map((field) => ({ label: field.label, value: String(detail?.[field.name as keyof App] ?? '-') }))} />
          <Alert className="section-card" content="发布路由后，在资源中心为用户分配对应端权限。入口按钮仍会按当前用户权限进行 SSO 校验。" />
          <Space className="section-card" wrap>{detail?.release?.routes.map((route) => <Button key={route.routeCode} onClick={() => void openApplication(detail.appCode, route.path, route.entry, route.openType).catch(notifyApiError)}>{entryLabel(route.entry)} · {route.name}</Button>)}</Space>
        </Tabs.TabPane>
        <Tabs.TabPane key="versions" title="路由与版本">
          {(permissions.can('platform:apps:update') || canManageApp(detail?.appCode)) && <Button type="primary" onClick={() => { importRoutes.reset(); setRoutes([blankRoute()]); setVersion(''); setGitCommit(''); setRouteError(undefined); setImporting(true) }}>添加路由版本</Button>}
          {versions.isError ? <Alert type="error" content={getApiMessage(versions.error)} action={<Button onClick={() => void versions.refetch()}>重试</Button>} /> : <Table<Release> rowKey="id" loading={versions.isLoading} data={versions.data ?? []} pagination={false} expandedRowRender={(release) => <Tree treeData={routePreview(release.routes)} blockNode defaultExpandAll />} columns={[
            { title: '版本', dataIndex: 'version', render: (value, row) => <Space>{value}{detail?.release?.version === row.version && <Tag color="green">当前</Tag>}</Space> }, { title: '环境', dataIndex: 'environment' }, { title: '审核', dataIndex: 'approvalStatus' },
            { title: '灰度', dataIndex: 'rolloutPercent', render: (value, row) => (permissions.can('platform:apps:review') || isAppAdmin(detail?.appCode)) ? <Space><InputNumber aria-label={`${row.version} 灰度比例`} min={0} max={100} precision={0} disabled={operation.isPending} value={rollouts[row.version] ?? value} style={{ width: 80 }} onChange={(percent) => setRollouts((previous) => ({ ...previous, [row.version]: percent ?? 0 }))} /><Button size="mini" disabled={operation.isPending || (rollouts[row.version] ?? value) === value} onClick={() => confirm(`route-metadata/${encodeURIComponent(row.version)}/rollout`, `调整灰度为 ${rollouts[row.version]}%？`, { percent: rollouts[row.version] })}>保存</Button></Space> : `${value}%` },
            { title: '操作', width: 240, render: (_, row) => <Space wrap>{(permissions.can('platform:apps:review') || isAppAdmin(detail?.appCode)) && <><Button type="text" disabled={operation.isPending || row.approvalStatus === 'APPROVED'} onClick={() => confirm(`route-metadata/${encodeURIComponent(row.version)}/review`, '审核通过？', { approved: true })}>通过</Button><Button type="text" status="danger" disabled={detail?.release?.version === row.version || operation.isPending} onClick={() => confirm(`route-metadata/${encodeURIComponent(row.version)}/review`, '拒绝此版本？', { approved: false })}>拒绝</Button></>}{(permissions.can('platform:apps:publish') || isAppAdmin(detail?.appCode)) && <><Button type="text" disabled={row.approvalStatus !== 'APPROVED' || operation.isPending || detail?.release?.version === row.version} onClick={() => confirm(`route-metadata/${encodeURIComponent(row.version)}/publish`, '发布此版本？')}>发布</Button><Button type="text" disabled={!row.published || detail?.release?.version === row.version || operation.isPending} onClick={() => confirm(`route-metadata/${encodeURIComponent(row.version)}/rollback`, '回滚至此版本？')}>回滚</Button></>}</Space> },
          ]} />}
        </Tabs.TabPane>
        <Tabs.TabPane key="governance" title="管理员与权限策略">{detail && canManageApp(detail.appCode) ? <ApplicationGovernance appCode={detail.appCode} /> : <Alert content="需要本应用管理权限" />}</Tabs.TabPane>
        <Tabs.TabPane key="auth" title="认证与接入">
          <Alert content="CI Token 仅用于路由上报；应用服务凭证用于 SSO 交换和服务端鉴权。两者分别轮换，明文仅展示一次。" />
          <Descriptions className="section-card" column={1} border data={[
            { label: '路由上报', value: `POST /api/platform/apps/${detail?.appCode}/route-metadata（X-CI-Token）` },
            { label: 'SSO 交换', value: 'POST /api/sso/token/exchange（X-App-Secret；code、appCode、entry）' },
            { label: '当前权限', value: 'POST /api/app-auth/permissions（X-App-Code、X-App-Secret；subjectId、entry）' },
            { label: '权限判定', value: 'POST /api/app-auth/check（另含 permissionCode）' },
          ]} />
          <Alert className="section-card" type="warning" content="服务凭证只保存在应用后端。应用需在受保护 API 中执行权限校验，并从自身已验证会话取得 subjectId。SSO 交换码 60 秒有效且仅可使用一次；返回的权限快照不会自动更新应用自身会话。" />
          <Space className="section-card" wrap>{(permissions.can('platform:apps:ci') || isAppAdmin(detail?.appCode)) && <Button status="danger" loading={operation.isPending} onClick={() => confirm('ci-credentials/rotate', '轮换后旧 CI 凭证立即失效，继续？')}>轮换 CI 凭证</Button>}{(permissions.can('platform:apps:auth') || isAppAdmin(detail?.appCode)) && <Button status="danger" loading={operation.isPending} onClick={() => confirm('service-credentials/rotate', '轮换后旧应用服务凭证立即失效，继续？')}>轮换服务凭证</Button>}</Space>
        </Tabs.TabPane>
      </Tabs>
    </Drawer>
    <Drawer width={860} title="添加路由版本" visible={importing} confirmLoading={routeBusy} maskClosable={!routeBusy} closable={!routeBusy} okButtonProps={{ disabled: routeBusy }} onCancel={() => { if (!routeBusy) Modal.confirm({ title: '放弃本次路由录入？', onOk: () => setImporting(false) }) }} onOk={() => { const error = validateRoutes(version, routes, detail); setRouteError(error); if (!error && !routeBusy) importRoutes.mutate() }}>
      <Alert content="同一应用可同时接入 Web 与管理后台。菜单分类和上级路由共同决定导航树，Web 与后台使用独立权限。" />
      <Space className="section-card" wrap><Input aria-label="版本号" placeholder="版本号（必填）" disabled={routeBusy} value={version} onChange={setVersion} /><Select aria-label="环境" disabled={routeBusy} value={environment} onChange={setEnvironment} options={['DEV', 'TEST', 'PROD']} /><Input aria-label="提交标识" placeholder="Git Commit（可选）" maxLength={128} disabled={routeBusy} value={gitCommit} onChange={setGitCommit} /></Space>
      {(routeError || importRoutes.isError) && <Alert className="section-card" type="error" content={routeError ?? getApiMessage(importRoutes.error)} />}
      {routes.map((route, index) => <Card key={index} className="section-card" title={`路由 ${index + 1}`} extra={<Button type="text" status="danger" disabled={routes.length === 1 || routeBusy} onClick={() => setRoutes((previous) => previous.filter((_, i) => i !== index))}>移除</Button>}>
        <div className="route-form-grid">
          <label>所属端<Select disabled={routeBusy} value={route.entry} onChange={(value) => changeRoute(index, { entry: value, parentRouteCode: null })} options={[{ label: 'Web 业务端', value: 'WEB' }, { label: '管理后台', value: 'ADMIN', disabled: !detail?.adminUrl }]} /></label>
          <label>菜单分类<Input disabled={routeBusy} maxLength={64} value={route.category} onChange={(value) => changeRoute(index, { category: value, parentRouteCode: null })} /></label>
          <label>路由编码<Input disabled={routeBusy} maxLength={64} placeholder="必填，如 order-list" value={route.routeCode} onChange={(value) => changeRoute(index, { routeCode: value })} /></label>
          <label>页面名称<Input disabled={routeBusy} maxLength={128} value={route.name} onChange={(value) => changeRoute(index, { name: value })} /></label>
          <label>上级路由<TreeSelect disabled={routeBusy} allowClear showSearch placeholder="留空作为分类下的一级菜单" value={route.parentRouteCode ?? undefined} treeData={parentTree(routes, route)} onChange={(value) => changeRoute(index, { parentRouteCode: value ? String(value) : null })} /></label>
          <label>页面路径<Input disabled={routeBusy} placeholder="如 /orders" value={route.path} onChange={(value) => changeRoute(index, { path: value })} /></label>
          <label>权限编码<Input disabled={routeBusy} maxLength={160} placeholder="留空由系统按应用和所属端生成" value={route.permissionCode} onChange={(value) => changeRoute(index, { permissionCode: value })} /></label>
          <label>图标标识<Input disabled={routeBusy} maxLength={128} value={route.icon} onChange={(value) => changeRoute(index, { icon: value })} /></label>
          <label>打开方式<Select disabled={routeBusy} value={route.openType} onChange={(value) => changeRoute(index, { openType: value })} options={[{ label: '新窗口', value: 'NEW_TAB' }, { label: '当前窗口', value: 'CURRENT_TAB' }, { label: '外部应用（新窗口）', value: 'EXTERNAL' }]} /></label>
          <label>排序<InputNumber disabled={routeBusy} value={route.sortOrder} precision={0} onChange={(value) => changeRoute(index, { sortOrder: value ?? 0 })} /></label>
          <label>菜单可见<Switch disabled={routeBusy} checked={route.visible} onChange={(value) => changeRoute(index, { visible: value })} /></label>
        </div>
      </Card>)}
      <Button className="section-card" disabled={routeBusy} icon={<IconPlus />} onClick={() => setRoutes((previous) => [...previous, blankRoute()])}>新增路由</Button>
      <Card className="section-card" title="菜单层级预览"><Tree treeData={routePreview(routes.filter((route) => route.routeCode))} blockNode /></Card>
    </Drawer>
    <Modal title="接入凭证仅展示一次" visible={credentials !== null} footer={null} maskClosable={false} onCancel={() => setCredentials(null)}>
      <Alert type="warning" content="关闭后无法再次查看。分别保存到 CI Secret 与应用后端的 Secret 配置。" />
      {(['ciToken', 'clientSecret'] as const).map((key) => credentials?.[key] ? <div className="section-card" key={key}><strong>{key === 'ciToken' ? 'CI Token（仅路由上报）' : '应用服务凭证（仅后端使用）'}</strong><Input.TextArea value={credentials[key]} readOnly autoSize /><Button onClick={() => void copyCredential(credentials[key]!)}>复制{key === 'ciToken' ? ' CI Token' : '服务凭证'}</Button></div> : null)}
    </Modal>
  </div>
}
