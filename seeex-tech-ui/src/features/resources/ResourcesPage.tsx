import { Alert, Button, Card, Descriptions, Empty, Input, Message, Modal, Select, Space, Spin, Tag, Tree } from '@arco-design/web-react'
import { IconFolderAdd, IconPlus, IconRefresh } from '@arco-design/web-react/icon'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { api, getApiMessage, notifyApiError } from '../../lib/api'
import { loadAll, treeNodes, usePermissions, type TreeRow } from '../../lib/management'
import { EntityDrawer, type FieldSpec } from '../../components/EntityDrawer'

type EntryType = 'WEB' | 'ADMIN'
interface Resource extends TreeRow, Record<string, unknown> { code: string; permissionCode: string; resourceType: string; applicationCode: string; entryType?: EntryType; menuCategory?: string; status: string; visible: boolean; publicResource: boolean }
const types: Record<string, string> = { APPLICATION: '应用', MODULE: '模块', PAGE: '页面', MENU: '菜单', ACTION: '操作', API: '接口', DATA_SCOPE: '数据范围' }
const entryOptions = [{ label: 'Web 端', value: 'WEB' }, { label: '管理后台', value: 'ADMIN' }]
const entryOf = (row: { applicationCode?: unknown; entryType?: unknown }): EntryType => row.applicationCode === 'platform' ? 'ADMIN' : row.entryType === 'ADMIN' ? 'ADMIN' : 'WEB'
const categoryOf = (row: { menuCategory?: unknown }) => typeof row.menuCategory === 'string' && row.menuCategory.trim() ? row.menuCategory.trim() : '常用功能'
function EntrySelector({ value, onChange, onEntryChange, disabled }: { value?: EntryType; onChange?: (value: EntryType) => void; onEntryChange: (value: EntryType) => void; disabled: boolean }) {
  return <Select value={value} disabled={disabled} options={entryOptions} onChange={(next: EntryType) => { onChange?.(next); onEntryChange(next) }} />
}
function CategoryInput({ value, onChange, onCategoryChange, disabled }: { value?: string; onChange?: (value: string) => void; onCategoryChange: (value: string) => void; disabled: boolean }) {
  return <Input value={value} maxLength={64} disabled={disabled} placeholder="如：常用功能、业务管理" onChange={(next) => { onChange?.(next); onCategoryChange(next.trim() || '常用功能') }} />
}

export function ResourcesPage() {
  const permissions = usePermissions()
  const client = useQueryClient()
  const [params, setParams] = useSearchParams()
  const appCode = params.get('applicationCode') ?? 'platform'
  const selectedId = Number(params.get('node')) || undefined
  const keyword = params.get('keyword') ?? ''
  const entryFilter = params.get('entryType') ?? ''
  const categoryFilter = params.get('menuCategory') ?? ''
  const [editing, setEditing] = useState<Record<string, unknown> | null>(null)
  const [expanded, setExpanded] = useState<string[] | undefined>()
  const [draftEntry, setDraftEntry] = useState<EntryType>('ADMIN')
  const [draftCategory, setDraftCategory] = useState('常用功能')
  const [entryError, setEntryError] = useState<string>()
  const apps = useQuery({ queryKey: ['resource-app-options'], queryFn: async () => (await api.get<{ data: { appCode: string; name: string }[] }>('/rbac/applications')).data.data, enabled: permissions.can('rbac:resources:view') })
  const query = useQuery({ queryKey: ['resource-tree', appCode], queryFn: () => loadAll<Resource>('/rbac/resources', { applicationCode: appCode }), enabled: permissions.can('rbac:resources:view') })
  const usage = useQuery({ queryKey: ['resource-usage', selectedId], queryFn: async () => (await api.get<{ data: { name: string; subjectType: string; effect: string }[] }>(`/rbac/resources/${selectedId}/usage`)).data.data, enabled: selectedId !== undefined && permissions.can('rbac:resources:view') })
  const save = useMutation({ mutationFn: (values: Record<string, unknown>) => editing?.id ? api.put(`/rbac/resources/${editing.id}`, values) : api.post('/rbac/resources', values), onSuccess: async () => { setEditing(null); Message.success('资源已保存'); await client.invalidateQueries() }, onError: (error) => notifyApiError(error) })
  const remove = useMutation({ mutationFn: (id: number) => api.delete('/rbac/resources', { data: [id] }), onSuccess: async () => { const next = new URLSearchParams(params); next.delete('node'); setParams(next); Message.success('资源已删除'); await client.invalidateQueries() }, onError: (error) => notifyApiError(error) })
  const rows = query.data ?? []
  const detail = rows.find((row) => row.id === selectedId)
  const filteredRows = rows.filter((row) => (!entryFilter || entryOf(row) === entryFilter) && (!categoryFilter || categoryOf(row) === categoryFilter))
  const visibleIds = new Set(filteredRows.filter((row) => `${row.name} ${row.code} ${row.permissionCode}`.toLowerCase().includes(keyword.toLowerCase())).map((row) => row.id))
  for (const id of [...visibleIds]) { let row = filteredRows.find((item) => item.id === id); const seen = new Set<number>(); while (row?.parentId && !seen.has(row.parentId)) { seen.add(row.parentId); visibleIds.add(row.parentId); row = filteredRows.find((item) => item.id === row?.parentId) } }
  const nodes = treeNodes(filteredRows.filter((row) => visibleIds.has(row.id)).map((row) => ({ ...row, name: `${row.name} · ${entryOf(row) === 'ADMIN' ? '管理后台' : 'Web 端'} · ${categoryOf(row)} · ${types[row.resourceType] ?? row.resourceType}${row.status !== 'ACTIVE' ? '（停用）' : ''}` })))
  const editorBlocked = !apps.isSuccess || !query.isSuccess || apps.isFetching || query.isFetching
  const editorMessage = apps.isError || query.isError ? `关联资源读取失败：${getApiMessage(apps.error ?? query.error)}` : editorBlocked ? '正在读取应用和父资源，请稍候再保存' : undefined
  const fields: FieldSpec[] = [
    { name: 'name', label: '资源名称', required: true }, { name: 'resourceType', label: '资源类型', required: true, readOnly: Boolean(editing?.id), type: 'select', options: Object.entries(types).map(([value, label]) => ({ value, label })) },
    { name: 'applicationCode', label: '所属应用', type: 'select', options: [{ label: '基础平台', value: 'platform' }, ...(apps.data ?? []).filter((app) => app.appCode !== 'platform').map((app) => ({ label: app.name, value: app.appCode }))], readOnly: true, required: true },
    { name: 'entryType', label: '入口类型', required: true, type: 'select', render: <EntrySelector disabled={Boolean(editing?.id) || appCode === 'platform' || save.isPending} onEntryChange={(value) => { setDraftEntry(value); setEntryError(undefined) }} /> },
    { name: 'menuCategory', label: '菜单分类', required: true, render: <CategoryInput disabled={appCode === 'platform' || save.isPending} onCategoryChange={(value) => { setDraftCategory(value); setEntryError(undefined) }} /> },
    { name: 'parentId', label: '父资源（同一入口和分类）', type: 'treeSelect', treeData: treeNodes(rows.filter((row) => entryOf(row) === draftEntry && (appCode === 'platform' || categoryOf(row) === draftCategory)), editing?.id ? Number(editing.id) : undefined) },
    { name: 'routePath', label: '页面路由（菜单、页面必填）', placeholder: '/dashboard' }, { name: 'icon', label: '图标标识' }, { name: 'sortOrder', label: '排序值', type: 'number', precision: 0 },
    { name: 'visible', label: '菜单显示', type: 'switch' }, { name: 'publicResource', label: '公开资源', type: 'switch' },
    { name: 'status', label: '状态', type: 'select', required: true, options: [{ label: '启用', value: 'ACTIVE' }, { label: '停用', value: 'INACTIVE' }] },
  ]
  function create(parentId?: number) { const parent = rows.find((row) => row.id === parentId); const entryType: EntryType = parent ? entryOf(parent) : appCode === 'platform' || entryFilter === 'ADMIN' ? 'ADMIN' : 'WEB'; const menuCategory = parent ? categoryOf(parent) : categoryFilter || '常用功能'; setDraftEntry(entryType); setDraftCategory(menuCategory); setEntryError(undefined); setEditing({ applicationCode: appCode, entryType, menuCategory, parentId: parentId === undefined ? undefined : String(parentId), name: '', resourceType: parentId ? 'MENU' : 'MODULE', visible: true, publicResource: false, sortOrder: 0, status: 'ACTIVE' }) }
  if (permissions.isLoading) return <Spin />
  if (permissions.isError) return <Alert type="error" content={`权限读取失败：${getApiMessage(permissions.error)}`} action={<Button onClick={() => void permissions.refetch()}>重试</Button>} />
  if (!permissions.can('rbac:resources:view')) return <Alert type="error" content="暂无资源管理权限" />
  return <div>
    <div className="page-heading">
    <div>
    <h1>资源树</h1>
    <p className="muted">按应用、入口和分类组织资源层级；应用发布会同步并覆盖路由名称、分类、图标和排序等展示字段。菜单和模块参与导航；PAGE 用于页面授权，不注入菜单。</p>
    </div>
    <Space>
    <Button icon={<IconRefresh />} onClick={() => void client.invalidateQueries()}>刷新</Button>{permissions.can('rbac:resources:create') && <Button type="primary" disabled={editorBlocked} icon={<IconPlus />} onClick={() => create()}>新增根资源</Button>}</Space>
    </div>
    <Card>
    <Space wrap>
    <Select showSearch value={appCode} style={{ width: 240 }} options={[{ label: '基础平台', value: 'platform' }, ...(apps.data ?? []).filter((app) => app.appCode !== 'platform').map((app) => ({ label: app.name, value: app.appCode }))]} onChange={(value) => { const change = () => { setParams({ applicationCode: value }); setExpanded(undefined); setEditing(null) }; if (editing) Modal.confirm({ title: '切换应用并放弃当前编辑？', onOk: change }); else change() }} />
    <Select allowClear placeholder="全部入口" style={{ width: 160 }} value={entryFilter || undefined} options={entryOptions} onChange={(value) => { const next = new URLSearchParams(params); value ? next.set('entryType', value) : next.delete('entryType'); next.delete('node'); setExpanded(undefined); setParams(next) }} />
    <Select allowClear showSearch placeholder="全部菜单分类" style={{ width: 180 }} value={categoryFilter || undefined} options={[...new Set(rows.map(categoryOf))]} onChange={(value) => { const next = new URLSearchParams(params); value ? next.set('menuCategory', value) : next.delete('menuCategory'); next.delete('node'); setExpanded(undefined); setParams(next) }} />
    <Input.Search placeholder="搜索名称或权限编码" allowClear value={keyword} onChange={(value) => { const next = new URLSearchParams(params); value ? next.set('keyword', value) : next.delete('keyword'); setParams(next) }} />
    <Button onClick={() => setExpanded(rows.map((row) => String(row.id)))}>展开全部</Button>
    <Button onClick={() => setExpanded([])}>收起全部</Button>
    </Space>
    </Card>
    {query.isError || apps.isError ? <Alert type="error" content={getApiMessage(query.error ?? apps.error)} action={<Button onClick={() => { void query.refetch(); void apps.refetch() }}>重试</Button>} /> : query.isLoading || apps.isLoading ? <Spin /> : <div className="resource-workspace section-card">
    <Card className="tree-pane" title="资源层级">{!nodes.length ? <Empty description={!rows.length ? '该应用暂无资源' : '没有匹配的资源'} /> : <Tree blockNode treeData={nodes} expandedKeys={expanded} onExpand={setExpanded} selectedKeys={selectedId ? [String(selectedId)] : []} onSelect={(keys) => { const next = new URLSearchParams(params); if (keys[0]) next.set('node', keys[0]); else next.delete('node'); setParams(next) }} />}</Card>
    <Card title={detail?.name ?? '资源详情'}>{detail ? <>
    <Space>
    <Tag>{types[detail.resourceType]}</Tag>
    <Tag>{entryOf(detail) === 'ADMIN' ? '管理后台' : 'Web 端'}</Tag>
    <Tag>{categoryOf(detail)}</Tag>
    <Tag color={detail.status === 'ACTIVE' ? 'green' : 'gray'}>{detail.status === 'ACTIVE' ? '启用' : '停用'}</Tag>
    </Space>
    <Descriptions className="section-card" column={1} border data={[{ label: '资源编码', value: detail.code }, { label: '权限编码', value: detail.permissionCode }, { label: '所属应用', value: detail.applicationCode === 'platform' ? '基础平台' : apps.data?.find((app) => app.appCode === detail.applicationCode)?.name ?? '应用名称不可用' }, { label: '父资源', value: rows.find((row) => row.id === detail.parentId)?.name ?? '根节点' }, { label: '路由', value: String(detail.routePath ?? '-') }, { label: '排序', value: String(detail.sortOrder ?? 0) }, { label: '菜单显示', value: detail.visible ? '显示' : '隐藏' }]} />
    <Space className="section-card" wrap>{permissions.can('rbac:resources:update') && <Button type="primary" disabled={editorBlocked} onClick={() => { setDraftEntry(entryOf(detail)); setDraftCategory(categoryOf(detail)); setEntryError(undefined); setEditing({ ...detail, entryType: entryOf(detail), menuCategory: categoryOf(detail), parentId: detail.parentId == null ? undefined : String(detail.parentId) }) }}>编辑与移动</Button>}{permissions.can('rbac:resources:create') && <Button disabled={editorBlocked} icon={<IconFolderAdd />} onClick={() => create(detail.id)}>新增子资源</Button>}{permissions.can('rbac:resources:delete') && <Button status="danger" loading={remove.isPending} onClick={() => Modal.confirm({ title: `删除「${detail.name}」？`, content: '存在子资源或授权关联时将被阻止。', onOk: () => remove.mutateAsync(detail.id) })}>删除</Button>}</Space>
    <h3>授权关联</h3>{usage.isError ? <Alert type="error" content={getApiMessage(usage.error)} action={<Button onClick={() => void usage.refetch()}>重试</Button>} /> : usage.isLoading ? <Spin /> : usage.data?.length ? <Descriptions column={1} data={usage.data.map((row, index) => ({ key: String(index), label: row.subjectType, value: `${row.name} · ${row.effect}` }))} /> : <Empty description="暂无授权关联" />}</> : <Empty description="选择左侧节点查看详情" />}</Card>
    </div>}
    <EntityDrawer mode={editing?.id ? 'edit' : 'create'} title={editing?.id ? '编辑资源' : '新增资源'} visible={editing !== null} initialValues={editing ?? undefined} fields={fields} loading={save.isPending} submitDisabled={editorBlocked} topError={editorMessage ?? entryError ?? (save.isError ? getApiMessage(save.error) : undefined)} onClose={() => setEditing(null)} onSubmit={(values) => { if (editorBlocked) return; const entryType = appCode === 'platform' ? 'ADMIN' : editing?.id ? entryOf(editing) : values.entryType === 'ADMIN' ? 'ADMIN' : 'WEB'; const menuCategory = categoryOf(values); const parentId = values.parentId ? Number(values.parentId) : null; if (parentId && !rows.some((row) => row.id === parentId && entryOf(row) === entryType && (appCode === 'platform' || categoryOf(row) === menuCategory))) { setEntryError('父资源不属于当前入口或菜单分类，请重新选择父资源或清空为根节点'); return } const routePath = typeof values.routePath === 'string' ? values.routePath.trim() : ''; if (['MENU', 'PAGE'].includes(String(values.resourceType)) && !routePath) { setEntryError('菜单和页面资源必须填写站内路由'); return } if (routePath && (!routePath.startsWith('/') || routePath.startsWith('//') || /\\|\.\.|[\r\n]/.test(routePath))) { setEntryError('路由必须是以单个 / 开头的站内路径，不能包含反斜杠、换行或 ..'); return } setEntryError(undefined); save.mutate({ ...values, entryType, menuCategory, parentId, routePath: routePath || null, icon: values.icon ?? null }) }} />
  </div>
}
