import { Alert, Button, Card, Descriptions, Drawer, Empty, Input, Message, Modal, Select, Space, Spin, Table, Tag, Tree, TreeSelect } from '@arco-design/web-react'
import { IconPlus, IconRefresh } from '@arco-design/web-react/icon'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { api, getApiMessage, type PageResult, notifyApiError } from '../../lib/api'
import { loadAll, treeNodes, usePermissions, type TreeRow } from '../../lib/management'
import { EntityDrawer, type FieldSpec } from '../../components/EntityDrawer'
import { DepartmentMemberships, type Membership } from '../../components/DepartmentMemberships'
import { ResourceGrantDrawer } from '../../components/ResourceGrantDrawer'

import { RoleDataScopeDrawer } from '../../components/RoleDataScopeDrawer'

type Kind = 'employees' | 'departments' | 'job-grades' | 'roles'
interface Row extends Record<string, unknown> { id: number; status: string; name?: string; displayName?: string; memberships?: Membership[] }
interface Assignment { roleId: number; roleName: string }
const config = { employees: ['员工管理', '/employees', 'employees'], departments: ['部门管理', '/departments', 'departments'], 'job-grades': ['职级管理', '/job-grades', 'job-grades'], roles: ['角色管理', '/rbac/roles', 'rbac:roles'] } as const

/** 组织实体共用列表、抽屉和状态处理，角色使用专门的双树授权抽屉。 */
export function EntityManagementPage({ kind }: { kind: Kind }) {
  const [title, endpoint, scope] = config[kind]
  const permissions = usePermissions()
  const client = useQueryClient()
  const navigate = useNavigate()
  const [params, setParams] = useSearchParams()
  const page = Math.max(1, Number(params.get('page')) || 1)
  const keyword = params.get('keyword') ?? ''
  const status = params.get('status') ?? undefined
  const [selected, setSelected] = useState<number[]>([])
  const [editor, setEditor] = useState<{ mode: 'create' | 'edit' | 'view'; values: Row | Record<string, unknown> } | null>(null)
  const [grant, setGrant] = useState<Row | null>(null)
  const [assignment, setAssignment] = useState<Row | null>(null)
  const [roleIds, setRoleIds] = useState<number[] | null>(null)
  const [scopeRole, setScopeRole] = useState<number>()
  const [usage, setUsage] = useState<Row | null>(null)
  const [accountEditor, setAccountEditor] = useState<{ id: number; values: Record<string, unknown> } | null>(null)
  function filter(name: string, value?: string) { const next = new URLSearchParams(params); if (value) next.set(name, value); else next.delete(name); next.delete('page'); setParams(next); setSelected([]) }
  const rows = useQuery({ queryKey: ['entities', kind, params.toString()], queryFn: async () => (await api.get<{ data: PageResult<Row> }>(endpoint, { params: { page, pageSize: 10, keyword: keyword || undefined, status, departmentId: params.get('departmentId') || undefined, jobGradeId: params.get('jobGradeId') || undefined } })).data.data, enabled: permissions.can(`${scope}:view`) })
  const departments = useQuery({ queryKey: ['organization-options', 'departments'], queryFn: () => loadAll<TreeRow>('/departments'), enabled: (kind === 'employees' || kind === 'departments') && permissions.can('departments:view') })
  const grades = useQuery({ queryKey: ['organization-options', 'grades'], queryFn: () => loadAll<{ id: number; name: string }>('/job-grades'), enabled: kind === 'employees' && permissions.can('job-grades:view') })
  const roles = useQuery({ queryKey: ['organization-options', 'roles'], queryFn: () => loadAll<{ id: number; name: string }>('/rbac/roles', { status: 'ACTIVE' }), enabled: permissions.can('rbac:roles:view') && (kind === 'job-grades' || assignment !== null) })
  const employees = useQuery({ queryKey: ['organization-options', 'employees'], queryFn: () => loadAll<{ id: number; displayName: string }>('/employees'), enabled: kind === 'departments' && editor !== null && permissions.can('employees:view') })
  const assignments = useQuery({ queryKey: ['department-roles', assignment?.id], queryFn: async () => (await api.get<{ data: Assignment[] }>(`/rbac/assignments/DEPARTMENT/${assignment?.id}`)).data.data, enabled: assignment !== null && permissions.can('rbac:roles:view') })
  const usages = useQuery({ queryKey: ['role-usage', usage?.id], queryFn: async () => (await api.get<{ data: { subjectType: string; name: string; source: string }[] }>(`/rbac/roles/${usage?.id}/usage`)).data.data, enabled: usage !== null })
  const save = useMutation({ mutationFn: (values: Record<string, unknown>) => editor?.mode === 'edit' ? api.put(`${endpoint}/${editor.values.id}`, values) : api.post(endpoint, values), onSuccess: async () => { setEditor(null); Message.success('保存成功'); await client.invalidateQueries() }, onError: (error) => notifyApiError(error) })
  const remove = useMutation({ mutationFn: (ids: number[]) => api.delete(endpoint, { data: ids }), onSuccess: async () => { setSelected([]); Message.success('删除成功'); await client.invalidateQueries() }, onError: (error) => notifyApiError(error) })
  const bind = useMutation({ mutationFn: () => { if (bindingBlocked) return Promise.reject(new Error('请先成功读取角色及原有绑定后再保存')); return api.put(`/rbac/assignments/DEPARTMENT/${assignment?.id}`, roleIds ?? (assignments.data ?? []).map((row) => row.roleId)) }, onSuccess: async () => { setAssignment(null); Message.success('角色绑定已保存'); await client.invalidateQueries() }, onError: (error) => notifyApiError(error) })
  const saveAccount = useMutation({ mutationFn: (values: Record<string, unknown>) => api.put(`/employees/${accountEditor?.id}/account`, values), onSuccess: async () => { setAccountEditor(null); Message.success('登录账号已保存'); await client.invalidateQueries() }, onError: (error) => notifyApiError(error) })
  async function openAccount(row: Row) { try { const response = await api.get<{ data: Record<string, unknown> }>(`/employees/${row.id}/account`); setAccountEditor({ id: row.id, values: response.data.data }) } catch (error) { notifyApiError(error) } }
  async function open(row: Row, mode: 'view' | 'edit') {
    try { const response = await api.get<{ data: Row }>(`${endpoint}/${row.id}`); const value = response.data.data; setEditor({ mode, values: { ...value, parentId: value.parentId == null ? undefined : String(value.parentId) } }) } catch (error) { notifyApiError(error) }
  }
  function confirmDelete(ids: number[]) { Modal.confirm({ title: `删除 ${ids.length} 条记录？`, content: '有关联数据的记录会被阻止删除。', onOk: () => remove.mutateAsync(ids) }) }
  function create(parentId?: number) { setEditor({ mode: 'create', values: { status: 'ACTIVE', memberships: [], parentId: parentId === undefined ? undefined : String(parentId) } }) }
  const fields: FieldSpec[] = kind === 'employees' ? [
    { name: 'employeeNo', label: '工号', readOnly: true }, { name: 'displayName', label: '姓名', required: true }, { name: 'internalMobile', label: '手机号', rules: [{ match: /^1[0-9]{10}$/, message: '请输入 11 位手机号' }] }, { name: 'internalEmail', label: '邮箱', rules: [{ type: 'email', message: '邮箱格式不正确' }] },
    { name: 'memberships', label: '所属部门与职级', render: <DepartmentMemberships departments={departments.data ?? []} grades={grades.data ?? []} disabled={editor?.mode === 'view' || save.isPending} /> },
  ] : [
    { name: 'code', label: '系统编码', readOnly: true }, { name: 'name', label: '名称', required: true },
    ...(kind === 'departments' ? [{ name: 'parentId', label: '上级部门', type: 'treeSelect' as const, treeData: treeNodes(departments.data ?? [], editor?.mode === 'edit' ? Number(editor.values.id) : undefined) }, { name: 'leaderEmployeeId', label: '负责人', type: 'select' as const, options: (employees.data ?? []).map((row) => ({ label: row.displayName, value: row.id })) }] : []),
    ...(kind === 'job-grades' ? [{ name: 'levelValue', label: '等级值 0–9999', type: 'number' as const, min: 0, max: 9999, precision: 0 }, { name: 'defaultRoleId', label: '默认角色', type: 'select' as const, readOnly: !permissions.can('rbac:roles:assign'), options: (roles.data ?? []).map((row) => ({ label: row.name, value: row.id })) }] : []),
  ]
  fields.push({ name: 'status', label: '状态', type: 'select', required: true, options: (kind === 'employees' ? [['在职', 'ACTIVE'], ['停用', 'DISABLED'], ['离职', 'LEFT'], ['锁定', 'LOCKED']] : [['启用', 'ACTIVE'], ['停用', 'INACTIVE']]).map(([label, value]) => ({ label, value })) })
  const relation = (value: unknown, options: { id: number; name: string }[]) => value == null ? '未设置' : options.find((row) => row.id === Number(value))?.name ?? '关联名称不可用'
  function actions(row: Row) { return <Space wrap>
    <Button type="text" onClick={() => void open(row, 'view')}>详情</Button>{permissions.can(`${scope}:update`) && <Button type="text" onClick={() => void open(row, 'edit')}>编辑</Button>}{kind === 'employees' && permissions.can('employees:update') && <Button type="text" onClick={() => void openAccount(row)}>登录账号</Button>}{kind === 'employees' && <Button type="text" onClick={() => navigate(`/permissions?employeeId=${row.id}`)}>权限分析</Button>}{kind === 'departments' && <>{permissions.can('departments:create') && <Button type="text" onClick={() => create(row.id)}>新增子部门</Button>}<Button type="text" onClick={() => navigate(`/employees?departmentId=${row.id}`)}>员工</Button>{permissions.can('rbac:roles:assign') && <Button type="text" onClick={() => { setRoleIds(null); setAssignment(row) }}>绑定角色</Button>}</>}{kind === 'job-grades' && <Button type="text" onClick={() => navigate(`/employees?jobGradeId=${row.id}`)}>关联员工</Button>}{kind === 'roles' && <>{permissions.can('rbac:roles:grant') && <Button type="text" onClick={() => setGrant(row)}>配置权限</Button>}{permissions.can('rbac:roles:grant') && <Button type="text" onClick={() => setScopeRole(row.id)}>数据范围</Button>}<Button type="text" onClick={() => setUsage(row)}>使用主体</Button>
    </>}{permissions.can(`${scope}:delete`) && <Button type="text" status="danger" onClick={() => confirmDelete([row.id])}>删除</Button>}</Space> }
  const requiredRelations = kind === 'employees'
    ? [{ allowed: permissions.can('departments:view'), query: departments, name: '部门' }, { allowed: permissions.can('job-grades:view'), query: grades, name: '职级' }]
    : kind === 'departments'
      ? [{ allowed: permissions.can('departments:view'), query: departments, name: '部门层级' }, { allowed: permissions.can('employees:view'), query: employees, name: '负责人' }]
      : kind === 'job-grades' ? [{ allowed: permissions.can('rbac:roles:view'), query: roles, name: '默认角色' }] : []
  const missingRelations = requiredRelations.filter((item) => !item.allowed)
  const relationFailure = requiredRelations.find((item) => item.query.isError)
  const editorBlocked = requiredRelations.some((item) => !item.allowed || !item.query.isSuccess || item.query.isFetching)
  const relationMessage = missingRelations.length ? `缺少${missingRelations.map((item) => item.name).join('、')}读取权限，无法安全编辑关联字段` : relationFailure ? `${relationFailure.name}读取失败：${getApiMessage(relationFailure.query.error)}` : editorBlocked ? '正在读取关联数据，请稍候再保存' : undefined
  const dictionaryError = departments.error ?? grades.error ?? (kind === 'job-grades' ? roles.error : null)
  const bindingBlocked = !permissions.can('rbac:roles:view') || !roles.isSuccess || !assignments.isSuccess || roles.isFetching || assignments.isFetching
  const bindingError = !permissions.can('rbac:roles:view') ? '缺少角色读取权限，无法读取和编辑原有绑定' : assignments.isError || roles.isError ? getApiMessage(assignments.error ?? roles.error) : undefined
  if (permissions.isLoading) return <Spin />
  if (permissions.isError) return <Alert type="error" content={`权限读取失败：${getApiMessage(permissions.error)}`} action={<Button onClick={() => void permissions.refetch()}>重试</Button>} />
  if (!permissions.can(`${scope}:view`)) return <Alert type="error" content="暂无权限访问此页面" />
  return <div>{scopeRole !== undefined && <RoleDataScopeDrawer roleId={scopeRole} onClose={() => setScopeRole(undefined)} />}
    <div className="page-heading">
    <div>
    <h1>{title}</h1>
    <p className="muted">系统生成编码，关联对象按名称选择，变更由后端校验。</p>
    </div>
    <Space>
    <Button icon={<IconRefresh />} onClick={() => void client.invalidateQueries()}>刷新</Button>{permissions.can(`${scope}:create`) && <Button type="primary" icon={<IconPlus />} onClick={() => create()}>新增</Button>}</Space>
    </div>
    <Card>
    <Space wrap className="filter-row">
    <Input.Search allowClear value={keyword} placeholder="搜索姓名、名称或编码" onChange={(value) => filter('keyword', value)} />
    <Select allowClear placeholder="状态" value={status} style={{ width: 150 }} onChange={(value) => filter('status', value)} options={kind === 'employees' ? ['ACTIVE', 'DISABLED', 'LEFT', 'LOCKED'] : ['ACTIVE', 'INACTIVE']} />{kind === 'employees' && <>
    <Select allowClear showSearch placeholder="职级" value={params.get('jobGradeId') ? Number(params.get('jobGradeId')) : undefined} options={(grades.data ?? []).map((row) => ({ label: row.name, value: row.id }))} style={{ width: 180 }} onChange={(value) => filter('jobGradeId', value ? String(value) : undefined)} />
    <TreeSelect allowClear showSearch placeholder="部门" value={params.get('departmentId') ?? undefined} treeData={treeNodes(departments.data ?? [])} style={{ width: 180 }} onChange={(value) => filter('departmentId', value ? String(value) : undefined)} />
    </>}<Button onClick={() => void rows.refetch()}>查询</Button><Button onClick={() => { setParams({}); setSelected([]) }}>重置</Button>
    </Space>
    <Space className="table-actions">{permissions.can(`${scope}:delete`) && <Button status="danger" disabled={!selected.length} onClick={() => confirmDelete(selected)}>批量删除（{selected.length}）</Button>}{kind === 'employees' && permissions.can('employees:update') && <Button disabled={!selected.length} onClick={() => Modal.confirm({ title: `停用 ${selected.length} 位员工？`, onOk: async () => { try { const data = await Promise.all(selected.map(async (id) => (await api.get<{ data: Row }>(`/employees/${id}`)).data.data)); await Promise.all(data.map((row) => api.put(`/employees/${row.id}`, { ...row, status: 'DISABLED' }))); Message.success('已停用'); setSelected([]) } catch (error) { notifyApiError(error) } finally { await client.invalidateQueries() } } })}>批量停用</Button>}</Space>
    {dictionaryError && <Alert type="error" content={`关联数据读取失败：${getApiMessage(dictionaryError)}`} action={<Button onClick={() => void client.invalidateQueries({ queryKey: ['organization-options'] })}>重试</Button>} />}
    {rows.isError ? <Alert type="error" content={getApiMessage(rows.error)} action={<Button onClick={() => void rows.refetch()}>重试</Button>} /> : <div className={kind === 'departments' ? 'entity-tree-layout' : undefined}>{kind === 'departments' && <Card className="tree-pane" title="组织层级">
    <Tree blockNode treeData={treeNodes(departments.data ?? [])} selectedKeys={params.get('node') ? [params.get('node')!] : []} onSelect={(keys) => { const row = departments.data?.find((item) => item.id === Number(keys[0])); if (row) { filter('node', keys[0]); void open(row as unknown as Row, 'view') } }} />
    </Card>}<Table<Row> rowKey="id" loading={rows.isLoading} data={rows.data?.items ?? []} scroll={{ x: 1280 }} rowSelection={{ selectedRowKeys: selected, onChange: (keys) => setSelected(keys.map(Number)) }} pagination={{ current: page, pageSize: 10, total: rows.data?.total ?? 0, showTotal: true, onChange: (next) => { const query = new URLSearchParams(params); query.set('page', String(next)); setParams(query); setSelected([]) } }} columns={[
      { title: kind === 'employees' ? '姓名' : '名称', dataIndex: kind === 'employees' ? 'displayName' : 'name', width: 160, ellipsis: true }, { title: '编码', dataIndex: kind === 'employees' ? 'employeeNo' : 'code', width: 210 },
      ...(kind === 'employees' ? [{ title: '部门', width: 150, render: (_: unknown, row: Row) => relation(row.primaryDepartmentId, departments.data ?? []) }, { title: '职级', width: 120, render: (_: unknown, row: Row) => relation(row.jobGradeId, grades.data ?? []) }] : []),
      ...(kind === 'departments' ? [{ title: '上级部门', dataIndex: 'parentName', width: 160 }, { title: '在职员工（可见范围）', dataIndex: 'employeeCount', width: 180, render: (value: unknown) => value == null ? '无员工读取权限' : String(value) }] : []),
      ...(kind === 'roles' ? [{ title: '授权资源数', dataIndex: 'resourceCount', width: 110 }, { title: '关联在职人员（去重）', dataIndex: 'employeeCount', width: 140 }] : []),
      ...(['employees', 'job-grades'].includes(kind) ? [{ title: '来源', dataIndex: 'source', width: 110, render: (value: unknown) => value === 'WECOM' ? '企业微信' : '本地' }] : []),
      { title: '更新时间', dataIndex: 'updatedAt', width: 200 },
      ...(kind === 'job-grades' ? [{ title: '等级', dataIndex: 'levelValue', width: 90 }, { title: '默认角色', width: 150, render: (_: unknown, row: Row) => relation(row.defaultRoleId, roles.data ?? []) }] : []),
      { title: '状态', dataIndex: 'status', width: 110, render: (value: string) => <Tag color={value === 'ACTIVE' ? 'green' : 'gray'}>{({ ACTIVE: kind === 'employees' ? '在职' : '启用', INACTIVE: '停用', DISABLED: '停用', LEFT: '离职', LOCKED: '锁定' } as Record<string, string>)[value] ?? value}</Tag> }, { title: '操作', width: 390, fixed: 'right' as const, render: (_: unknown, row: Row) => actions(row) },
    ]} />
    </div>}</Card>
    {editor && !permissions.can('rbac:roles:assign') && (kind === 'employees' || kind === 'job-grades') && <Alert content="修改部门/职级若改变继承角色，服务端会要求额外的角色分配权限；普通资料编辑不授予该权限。" />}<EntityDrawer title={`${editor?.mode === 'view' ? '查看' : editor?.mode === 'edit' ? '编辑' : '新增'}${title}`} mode={editor?.mode ?? 'create'} visible={editor !== null} initialValues={editor?.values} fields={fields.filter((field) => editor?.mode !== 'create' || !field.readOnly)} loading={save.isPending} submitDisabled={editorBlocked} topError={relationMessage ?? (save.isError ? getApiMessage(save.error) : undefined)} onClose={() => setEditor(null)} onSubmit={(values) => { if (editorBlocked) return; const normalized = { ...values }; for (const field of fields) if (normalized[field.name] === undefined) normalized[field.name] = null; if (kind === 'departments') normalized.parentId = values.parentId ? Number(values.parentId) : null; save.mutate(normalized) }} />
    <EntityDrawer mode="edit" title="维护登录账号" visible={accountEditor !== null} initialValues={accountEditor?.values} loading={saveAccount.isPending} topError={saveAccount.isError ? getApiMessage(saveAccount.error) : undefined} onClose={() => setAccountEditor(null)} onSubmit={(values) => saveAccount.mutate(values)} fields={[{ name: 'loginPhone', label: '登录账号', required: true }, { name: 'password', label: '密码', type: 'password', required: accountEditor?.values.exists === false, placeholder: '新账号必填；已有账号留空保留密码' }, { name: 'status', label: '账号状态', type: 'select', required: true, options: [{ label: '启用', value: 'ACTIVE' }, { label: '停用', value: 'DISABLED' }] }]} />
    {grant && <ResourceGrantDrawer endpoint={`/rbac/roles/${grant.id}/grants`} title={`配置「${grant.name}」权限`} onClose={() => setGrant(null)} />}
    <Drawer visible={assignment !== null} width={480} title="绑定部门角色" confirmLoading={bind.isPending} okButtonProps={{ disabled: bindingBlocked || bind.isPending }} onCancel={() => { if (bind.isPending) return; if (roleIds !== null) Modal.confirm({ title: '放弃角色修改？', onOk: () => setAssignment(null) }); else setAssignment(null) }} onOk={() => { if (bindingBlocked || bind.isPending) return; Modal.confirm({ title: '保存人工角色绑定？', content: '系统自动来源将保留。', onOk: () => bind.mutateAsync() }) }}>{bindingError && <Alert type="error" content={bindingError} action={permissions.can('rbac:roles:view') ? <Button onClick={() => { void roles.refetch(); void assignments.refetch() }}>重试</Button> : undefined} />}<Select mode="multiple" disabled={bindingBlocked || bind.isPending} loading={roles.isFetching || assignments.isFetching} showSearch style={{ width: '100%' }} value={roleIds ?? (assignments.data ?? []).map((row) => row.roleId)} onChange={setRoleIds} options={(roles.data ?? []).map((row) => ({ label: row.name, value: row.id }))} /></Drawer>
    <Drawer visible={usage !== null} title="角色使用主体" width={650} footer={null} onCancel={() => setUsage(null)}>{usages.isError ? <Alert type="error" content={getApiMessage(usages.error)} action={<Button onClick={() => void usages.refetch()}>重试</Button>} /> : usages.isLoading ? <Spin /> : !usages.data?.length ? <Empty description="暂无使用主体" /> : <Descriptions column={1} data={usages.data.map((row, index) => ({ key: String(index), label: row.subjectType, value: `${row.name} · ${row.source}` }))} />}</Drawer>
  </div>
}
