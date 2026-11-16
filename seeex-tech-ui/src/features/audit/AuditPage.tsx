import { Alert, Button, Card, DatePicker, Descriptions, Drawer, Input, Select, Space, Table, Tag } from '@arco-design/web-react'
import { useMutation, useQuery } from '@tanstack/react-query'
import { useEffect, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { api, getApiMessage, type PageResult, notifyApiError } from '../../lib/api'
import { usePermissions } from '../../lib/management'

interface Audit { applicationCode?: string; resourceKey?: string; operatorName?: string; id: number; logNo: string; operatorEmployeeId: string; action: string; resourceType: string; resourceId?: number; sourceIp: string; result: string; createdAt: string; requestSummary?: string; beforeJson?: string; afterJson?: string }
/** 将审计摘要转换为业务字段，避免在详情中直接倾倒 JSON。 */
function fields(summary?: string) {
  if (!summary) return []
  const result: { label: string; value: string }[] = []
  function visit(value: unknown, path: string, depth = 0) {
    if (result.length >= 300) return
    if (value !== null && typeof value === 'object' && depth < 8) {
      const entries = Object.entries(value)
      if (!entries.length) result.push({ label: path || '内容', value: Array.isArray(value) ? '空列表' : '空对象' })
      entries.forEach(([key, item]) => visit(item, path ? `${path}.${key}` : key, depth + 1))
    } else result.push({ label: path || '摘要', value: depth >= 8 && typeof value === 'object' ? '层级过深，已省略' : String(value ?? '-') })
  }
  try { visit(JSON.parse(summary) as unknown, '') } catch { result.push({ label: '摘要', value: summary }) }
  return result
}
function differences(before?: string, after?: string) {
  const old = new Map(fields(before).map((row) => [row.label, row.value])), next = new Map(fields(after).map((row) => [row.label, row.value]))
  return [...new Set([...old.keys(), ...next.keys()])].filter((key) => old.get(key) !== next.get(key)).map((key) => ({ key, before: old.get(key) ?? '无', after: next.get(key) ?? '无' }))
}
interface Options { operators: { id: string; name: string }[]; applications: { appCode: string; name: string }[]; resources: string[] }
export function AuditPage() {
  const permissions = usePermissions()
  const [params, setParams] = useSearchParams()
  const page = Number(params.get('page')) || 1
  const [search, setSearch] = useState({ operatorKeyword: '', appKeyword: '', resourceKeyword: '' })
  const [remoteSearch, setRemoteSearch] = useState(search)
  useEffect(() => { const timer = window.setTimeout(() => setRemoteSearch(search), 250); return () => window.clearTimeout(timer) }, [search])
  const selectedOptions = { operator: params.get('operator') ?? undefined, applicationCode: params.get('applicationCode') ?? undefined, resourceKey: params.get('resourceKey') ?? undefined }
  const options = useQuery({ queryKey: ['audit-options', remoteSearch, selectedOptions], placeholderData: (previous) => previous, queryFn: async () => (await api.get<{ data: Options }>('/audit-logs/options', { params: { ...remoteSearch, ...selectedOptions } })).data.data, enabled: permissions.can('audit:view') })
  const exporting = useMutation({ mutationFn: async () => {
    const response = await api.get<Blob>('/audit-logs/export', { params: Object.fromEntries(params), responseType: 'blob' })
    const url = URL.createObjectURL(response.data), anchor = document.createElement('a')
    anchor.href = url; anchor.download = 'seeex-audit.csv'; document.body.appendChild(anchor); anchor.click(); anchor.remove(); window.setTimeout(() => URL.revokeObjectURL(url), 10000)
  }, onError: (error) => notifyApiError(error) })
  const [detailId, setDetailId] = useState<number>()
  const query = useQuery({ queryKey: ['audit', params.toString()], queryFn: async () => (await api.get<{ data: PageResult<Audit> }>('/audit-logs', { params: { ...Object.fromEntries(params), page, pageSize: 20 } })).data.data, enabled: permissions.can('audit:view') })
  const detail = useQuery({ queryKey: ['audit-detail', detailId], queryFn: async () => (await api.get<{ data: Audit }>(`/audit-logs/${detailId}`)).data.data, enabled: detailId !== undefined && permissions.can('audit:view') })
  function filter(key: string, value?: string) { const next = new URLSearchParams(params); value ? next.set(key, value) : next.delete(key); next.delete('page'); setParams(next) }
  if (permissions.isError) return <Alert type="error" content={`权限读取失败：${getApiMessage(permissions.error)}`} action={<Button onClick={() => void permissions.refetch()}>重试</Button>} />
  if (!permissions.isLoading && !permissions.can('audit:view')) return <Alert type="error" content="暂无审计日志权限" />
  return <div>
    <div className="page-heading">
    <div>
    <h1>审计日志</h1>
    <p className="muted">查看管理操作、来源、结果和脱敏业务摘要。</p>
    </div>
    <Space><Button onClick={() => void query.refetch()}>刷新</Button>{permissions.can('audit:export') && <Button type="primary" loading={exporting.isPending} onClick={() => exporting.mutate()}>导出筛选结果</Button>}</Space>
    </div>
    <Card>
    <Space wrap className="filter-row">
    <Select showSearch allowClear filterOption={false} loading={options.isFetching} onSearch={(value) => setSearch((old) => ({ ...old, operatorKeyword: value }))} placeholder="操作人姓名" style={{ width: 200 }} value={params.get('operator') ?? undefined} options={options.data?.operators.map((row) => ({ label: row.name, value: row.id }))} onChange={(value) => filter('operator', value)} />
    <Select showSearch allowClear filterOption={false} loading={options.isFetching} onSearch={(value) => setSearch((old) => ({ ...old, appKeyword: value }))} placeholder="所属应用" style={{ width: 200 }} value={params.get('applicationCode') ?? undefined} options={options.data?.applications.map((row) => ({ label: row.name, value: row.appCode }))} onChange={(value) => filter('applicationCode', value)} />
    <Select showSearch allowClear filterOption={false} loading={options.isFetching} onSearch={(value) => setSearch((old) => ({ ...old, resourceKeyword: value }))} placeholder="具体资源路径" style={{ width: 260 }} value={params.get('resourceKey') ?? undefined} options={options.data?.resources} onChange={(value) => filter('resourceKey', value)} />
    <Select allowClear placeholder="操作结果" style={{ width: 150 }} value={params.get('result') ?? undefined} options={[{label:'成功',value:'SUCCESS'},{label:'失败',value:'FAILED'}]} onChange={(value) => filter('result', value)} />
    <Input allowClear placeholder="操作动作" value={params.get('action') ?? ''} onChange={(value) => filter('action', value)} />
    <Select allowClear placeholder="对象类型" style={{ width: 180 }} value={params.get('resourceType') ?? undefined} onChange={(value) => filter('resourceType', value)} options={['ORGANIZATION', 'RBAC', 'APPLICATION', 'FILE', 'WECOM', 'AUTH', 'AUDIT']} />
    <DatePicker.RangePicker showTime value={params.has('from') && params.has('to') ? [params.get('from')!.replace('T', ' '), params.get('to')!.replace('T', ' ')] : undefined} onChange={(value) => { const next = new URLSearchParams(params); if (value?.length === 2) { next.set('from', value[0].replace(' ', 'T')); next.set('to', value[1].replace(' ', 'T')) } else { next.delete('from'); next.delete('to') } next.delete('page'); setParams(next) }} />
    <Button onClick={() => void query.refetch()}>查询</Button><Button onClick={() => setParams({})}>重置</Button>
    </Space>{options.isError && <Alert type="error" content={getApiMessage(options.error)} action={<Button onClick={() => void options.refetch()}>重试筛选项</Button>} />}{exporting.isError && <Alert type="error" content={getApiMessage(exporting.error)} />}{query.isError ? <Alert type="error" content={getApiMessage(query.error)} action={<Button onClick={() => void query.refetch()}>重试</Button>} /> : <Table<Audit> rowKey="id" loading={query.isLoading || permissions.isLoading} data={query.data?.items ?? []} scroll={{ x: 1100 }} pagination={{ current: page, pageSize: 20, total: query.data?.total ?? 0, showTotal: true, onChange: (value) => { const next = new URLSearchParams(params); next.set('page', String(value)); setParams(next) } }} columns={[{ title: '时间', dataIndex: 'createdAt', width: 200 }, { title: '操作人', dataIndex: 'operatorName', width: 140 }, { title: '应用', dataIndex: 'applicationCode', width: 140, render: (code: string) => options.data?.applications.find((row) => row.appCode === code)?.name ?? code ?? '历史未记录' }, { title: '资源标识', dataIndex: 'resourceKey', width: 220, ellipsis: true }, { title: '动作', dataIndex: 'action', width: 220 }, { title: '对象', dataIndex: 'resourceType', width: 150 }, { title: '来源 IP', dataIndex: 'sourceIp', width: 150 }, { title: '结果', dataIndex: 'result', width: 110, render: (value) => <Tag color={value === 'SUCCESS' ? 'green' : 'red'}>{value === 'SUCCESS' ? '成功' : '失败'}</Tag> }, { title: '操作', width: 100, render: (_, row) => <Button type="text" onClick={() => setDetailId(row.id)}>详情</Button> }]} />}</Card>
    <Drawer width={640} visible={detailId !== undefined} title="审计详情" footer={null} onCancel={() => setDetailId(undefined)}>{detail.isError ? <Alert type="error" content={getApiMessage(detail.error)} action={<Button onClick={() => void detail.refetch()}>重试</Button>} /> : detail.isLoading ? <div className="state">正在读取审计详情…</div> : <>
    <Descriptions column={1} border data={[{ label: '日志编号', value: detail.data?.logNo ?? '-' }, { label: '操作人', value: detail.data?.operatorName ?? detail.data?.operatorEmployeeId ?? '-' }, { label: '动作', value: detail.data?.action ?? '-' }, { label: '时间', value: detail.data?.createdAt ?? '-' }, { label: '来源 IP', value: detail.data?.sourceIp ?? '-' }, { label: '结果', value: detail.data?.result ?? '-' }]} />
    <h3>请求摘要</h3>
    <Descriptions column={1} data={fields(detail.data?.requestSummary)} />
    <h3>字段差异</h3><Table rowKey="key" pagination={false} data={differences(detail.data?.beforeJson, detail.data?.afterJson)} columns={[{title:'字段',dataIndex:'key'},{title:'变更前',dataIndex:'before'},{title:'变更后',dataIndex:'after'}]} />
    <h3>变更前</h3>
    <Descriptions column={1} data={fields(detail.data?.beforeJson)} />
    <h3>变更后</h3>
    <Descriptions column={1} data={fields(detail.data?.afterJson)} />
    </>}</Drawer>
    </div>
}
