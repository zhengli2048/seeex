import { Alert, Button, Card, Descriptions, Drawer, Empty, Message, Modal, Select, Space, Spin, Table, Tag } from '@arco-design/web-react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { api, getApiMessage, notifyApiError } from '../../lib/api'
import { loadAll, usePermissions } from '../../lib/management'
import { ResourceGrantDrawer } from '../../components/ResourceGrantDrawer'

interface Employee { id: number; displayName: string; employeeNo: string; status: string }
interface Resource { id: number; name: string; applicationCode: string; permissionCode: string }
interface Snapshot { allowCodes: string[]; denyCodes: string[]; effectiveCodes: string[]; sources: Record<string, string[]> }
interface Assignment { roleId: number; roleName: string }

export function PermissionAnalysisPage() {
  const permissions = usePermissions()
  const client = useQueryClient()
  const [params, setParams] = useSearchParams()
  const employeeId = Number(params.get('employeeId')) || undefined
  const app = params.get('applicationCode') ?? ''
  const [roleEditor, setRoleEditor] = useState(false)
  const [grantEditor, setGrantEditor] = useState(false)
  const [roleDraft, setRoleDraft] = useState<number[] | null>(null)
  const employees = useQuery({ queryKey: ['analysis-employees'], queryFn: () => loadAll<Employee>('/employees'), enabled: permissions.can('employees:view') })
  const resources = useQuery({ queryKey: ['analysis-resources'], queryFn: () => loadAll<Resource>('/rbac/resources'), enabled: permissions.can('rbac:resources:view') })
  const roles = useQuery({ queryKey: ['analysis-roles'], queryFn: () => loadAll<{ id: number; name: string }>('/rbac/roles', { status: 'ACTIVE' }), enabled: permissions.can('rbac:roles:view') })
  const snapshot = useQuery({ queryKey: ['analysis-snapshot', employeeId], queryFn: async () => (await api.get<{ data: Snapshot }>(`/rbac/employees/${employeeId}/permissions`)).data.data, enabled: employeeId !== undefined && permissions.can('employees:view') })
  const assignments = useQuery({ queryKey: ['analysis-assignments', employeeId], queryFn: async () => (await api.get<{ data: Assignment[] }>(`/rbac/assignments/EMPLOYEE/${employeeId}`)).data.data, enabled: employeeId !== undefined && permissions.can('rbac:roles:view') })
  const grants = useQuery({ queryKey: ['analysis-direct', employeeId], queryFn: async () => (await api.get<{ data: { resourceId: number; permissionCode: string; effect: string }[] }>(`/rbac/employees/${employeeId}/grants`)).data.data, enabled: employeeId !== undefined && permissions.can('employees:view') })
  const save = useMutation({ mutationFn: () => { if (roleBlocked) return Promise.reject(new Error('请先成功读取角色及原有绑定后再保存')); return api.put(`/rbac/assignments/EMPLOYEE/${employeeId}`, roleDraft ?? (assignments.data ?? []).map((row) => row.roleId)) }, onSuccess: async () => { setRoleEditor(false); Message.success('角色已保存'); await client.invalidateQueries() }, onError: (error) => notifyApiError(error) })
  const selected = employees.data?.find((row) => row.id === employeeId)
  const appOptions = [...new Set((resources.data ?? []).map((row) => row.applicationCode).filter(Boolean))]
  const allowedCodes = new Set((resources.data ?? []).filter((row) => !app || row.applicationCode === app).map((row) => row.permissionCode))
  const inApp = (code: string) => !app || allowedCodes.has(code)
  const rows = Object.entries(snapshot.data?.sources ?? {}).filter(([code]) => inApp(code)).map(([code, sources]) => ({ code, name: resources.data?.find((row) => row.permissionCode === code)?.name ?? code, sources: sources.join('；'), denied: snapshot.data?.denyCodes.includes(code), effective: snapshot.data?.effectiveCodes.includes(code) }))
  const error = employees.error ?? snapshot.error ?? resources.error ?? grants.error
  const roleBlocked = !employeeId || !permissions.can('rbac:roles:view') || !roles.isSuccess || !assignments.isSuccess || roles.isFetching || assignments.isFetching
  const roleError = !permissions.can('rbac:roles:view') ? '缺少角色读取权限，无法读取和编辑原有绑定' : roles.isError || assignments.isError ? getApiMessage(roles.error ?? assignments.error) : undefined
  const roleOptions = [...(roles.data ?? []).map((row) => ({ label: row.name, value: row.id })), ...(assignments.data ?? []).filter((row) => !roles.data?.some((role) => role.id === row.roleId)).map((row) => ({ label: row.roleName, value: row.roleId }))]
  const grantBlocked = !resources.isSuccess || resources.isFetching || !permissions.can('rbac:resources:view')
  const appFilterError = app && !permissions.can('rbac:resources:view') ? '缺少资源读取权限，无法按应用筛选权限；请清空应用筛选' : undefined
  if (permissions.isLoading) return <Spin />
  if (permissions.isError) return <Alert type="error" content={`权限读取失败：${getApiMessage(permissions.error)}`} action={<Button onClick={() => void permissions.refetch()}>重试</Button>} />
  if (!permissions.can('employees:view')) return <Alert type="error" content="暂无人员权限分析权限" />
  return <div>
    <div className="page-heading">
    <div>
    <h1>权限分析</h1>
    <p className="muted">查看人员、部门和职级带来的权限，拒绝优先于允许。</p>
    </div>
    <Button onClick={() => void client.invalidateQueries()}>刷新</Button>
    </div>
    <Card>
    <Space wrap>
    <Select style={{ width: 300 }} showSearch allowClear placeholder="选择员工" value={employeeId} loading={employees.isLoading} options={(employees.data ?? []).map((row) => ({ label: `${row.displayName}（${row.employeeNo}）`, value: row.id }))} onChange={(id) => { setParams(id ? { employeeId: String(id) } : {}); setRoleEditor(false); setGrantEditor(false); setRoleDraft(null) }} />
    <Select style={{ width: 200 }} allowClear placeholder="全部应用" value={app || undefined} loading={resources.isFetching} disabled={!resources.isSuccess && !app} options={appOptions.map((value) => ({ label: value === 'platform' ? '基础平台' : value, value }))} onChange={(value) => { const next = new URLSearchParams(params); value ? next.set('applicationCode', value) : next.delete('applicationCode'); setParams(next) }} />{selected && <Tag>{selected.status}</Tag>}</Space>
    </Card>
    {appFilterError ? <Alert type="error" content={appFilterError} action={<Button onClick={() => { const next = new URLSearchParams(params); next.delete('applicationCode'); setParams(next) }}>清空应用筛选</Button>} /> : error ? <Alert type="error" content={getApiMessage(error)} action={<Button onClick={() => void client.invalidateQueries()}>重试</Button>} /> : !employeeId ? <Card className="section-card">
    <Empty description="选择员工查看权限" />
    </Card> : snapshot.isLoading || grants.isLoading || (Boolean(app) && resources.isFetching) ? <Spin /> : <>
    <Card className="section-card">
    <Space wrap>
    <Tag color="green">有效 {(snapshot.data?.effectiveCodes ?? []).filter(inApp).length}</Tag>
    <Tag color="orange">允许 {(snapshot.data?.allowCodes ?? []).filter(inApp).length}</Tag>
    <Tag color="red">拒绝 {(snapshot.data?.denyCodes ?? []).filter(inApp).length}</Tag>
    <Tag>直接授权 {(grants.data ?? []).filter((row) => inApp(row.permissionCode)).length}</Tag>{permissions.can('rbac:roles:assign') && <Button disabled={roleBlocked} onClick={() => { setRoleDraft(null); setRoleEditor(true) }}>管理人员角色</Button>}{permissions.can('employees:grant') && <Button disabled={grantBlocked} onClick={() => setGrantEditor(true)}>管理直接权限</Button>}</Space>
    {permissions.can('employees:grant') && !permissions.can('rbac:resources:view') && <Alert type="warning" content="缺少资源读取权限，无法编辑直接授权；已有权限分析结果仍可查看。" />}
    </Card>
    <Card title="直接绑定角色" className="section-card">{roleError ? <Alert type="error" content={roleError} action={permissions.can('rbac:roles:view') ? <Button onClick={() => { void roles.refetch(); void assignments.refetch() }}>重试</Button> : undefined} /> : assignments.isLoading ? <Spin /> : !assignments.data?.length ? <Empty description="暂无人工绑定角色" /> : <Descriptions column={1} data={assignments.data.map((row) => ({ key: String(row.roleId), label: '角色', value: row.roleName }))} />}</Card>
    <Card title="权限结果与来源" className="section-card">
    <Table rowKey="code" data={rows} pagination={{ pageSize: 20 }} scroll={{ x: 850 }} columns={[{ title: '资源', dataIndex: 'name', width: 160 }, { title: '权限编码', dataIndex: 'code', width: 240 }, { title: '来源名称', dataIndex: 'sources' }, { title: '计算结果', width: 150, render: (_, row) => row.denied ? <Tag color="red">显式拒绝覆盖允许</Tag> : row.effective ? <Tag color="green">生效</Tag> : <Tag>未生效</Tag> }]} />
    </Card>
    </>}
    <Drawer width={500} visible={roleEditor} title="管理人员角色" confirmLoading={save.isPending} onCancel={() => { if (save.isPending) return; if (roleDraft !== null) Modal.confirm({ title: '放弃角色修改？', onOk: () => setRoleEditor(false) }); else setRoleEditor(false) }} onOk={() => { if (roleBlocked || save.isPending) return; Modal.confirm({ title: '保存人工角色绑定？', content: '可清空全部人工角色，系统来源保留。', onOk: () => save.mutateAsync() }) }} okButtonProps={{ disabled: roleBlocked || save.isPending }}>{roleError && <Alert type="error" content={roleError} action={permissions.can('rbac:roles:view') ? <Button onClick={() => { void roles.refetch(); void assignments.refetch() }}>重试</Button> : undefined} />}<Select mode="multiple" showSearch disabled={roleBlocked || save.isPending} loading={roles.isFetching || assignments.isFetching} style={{ width: '100%' }} value={roleDraft ?? (assignments.data ?? []).map((row) => row.roleId)} options={roleOptions} onChange={setRoleDraft} /></Drawer>
    {grantEditor && employeeId && <ResourceGrantDrawer endpoint={`/rbac/employees/${employeeId}/grants`} title={`${selected?.displayName ?? '员工'}的直接权限`} onClose={() => setGrantEditor(false)} />}
  </div>
}
