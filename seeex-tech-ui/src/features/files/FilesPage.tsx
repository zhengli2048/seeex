import { Button, Card, Alert, Descriptions, Drawer, Empty, Input, Message, Modal, Pagination, Select, Space, Spin, Table } from '@arco-design/web-react'
import { IconDelete, IconDownload, IconRefresh, IconUpload } from '@arco-design/web-react/icon'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useRef, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { api, getApiMessage, notifyApiError } from '../../lib/api'
import { PermissionGate } from '../../components/PermissionGate'
import { deleteFiles, getDownloadUrl, listFiles, uploadFile, type FileItem } from './fileApi'

interface PermissionResponse { effective: string[] }

export function FilesPage() {
  const inputRef = useRef<HTMLInputElement>(null)
  const [params, setParams] = useSearchParams()
  const page = Number(params.get('page')) || 1
  const keyword = params.get('keyword') ?? ''
  const status = params.get('status') ?? ''
  const applicationCode = params.get('applicationCode') ?? 'platform'
  const applications = useQuery({ queryKey: ['file-applications'], queryFn: async () => (await api.get<{ data: { appCode: string; name: string }[] }>('/me/file-applications')).data.data })
  const contentType = params.get('contentType') ?? ''
  const [detail, setDetail] = useState<FileItem | null>(null)
  function filter(key: string, value?: string) { const next = new URLSearchParams(params); if (value) next.set(key, value); else next.delete(key); next.delete('page'); setParams(next); setSelected([]) }
  function setPage(value: number) { const next = new URLSearchParams(params); next.set('page', String(value)); setParams(next); setSelected([]) }
  const [selected, setSelected] = useState<string[]>([])
  const [progress, setProgress] = useState<number | null>(null)
  const queryClient = useQueryClient()
  const permissions = useQuery({ queryKey: ['permissions'], queryFn: async () => (await api.get<{ data: PermissionResponse }>('/permissions')).data.data, retry: false })
  const canView = permissions.data?.effective.includes('files:view') ?? false
  const query = useQuery({ queryKey: ['files', applicationCode, page, keyword, status, contentType], queryFn: () => listFiles({ page, pageSize: 10, keyword, status, contentType, applicationCode }), enabled: canView })
  const uploadMutation = useMutation({ mutationFn: (file: File) => uploadFile(file, setProgress, applicationCode), onSuccess: () => { Message.success('上传成功'); setProgress(null); void queryClient.invalidateQueries({ queryKey: ['files'] }) }, onError: (error) => { setProgress(null); notifyApiError(error) } })
  const deleteMutation = useMutation({ mutationFn: (keys: string[]) => deleteFiles(keys, applicationCode), onSuccess: () => { Message.success('删除成功'); setSelected([]); void queryClient.invalidateQueries({ queryKey: ['files'] }) }, onError: (error) => notifyApiError(error), onSettled: () => { void queryClient.invalidateQueries({ queryKey: ['files'] }) } })
  const rows = query.data?.items ?? []
  const canDelete = permissions.data?.effective.includes('files:delete') ?? false

  async function download(row: FileItem) {
    try { window.open(await getDownloadUrl(row.objectKey, row.applicationCode), '_blank', 'noopener,noreferrer') } catch (error) { notifyApiError(error) }
  }

  function confirmDelete(keys: string[]) { Modal.confirm({ title: '确认删除文件？', content: '删除后文件将不可访问。', onOk: () => deleteMutation.mutateAsync(keys) }) }

  if (permissions.isLoading) return <div className="state"><Spin />正在读取页面权限…</div>
  if (permissions.isError) return <Alert type="error" content={`权限读取失败：${getApiMessage(permissions.error)}`} action={<Button onClick={() => void permissions.refetch()}>重试</Button>} />
  if (!canView) return <Alert type="error" content="暂无权限访问此页面" />

  return <div><div className="page-heading"><div><div className="eyebrow">FILE SERVICE</div><h1>文件中心</h1><p className="muted">文件元数据、归属和临时下载链接。</p></div><Space>
    <Button icon={<IconRefresh />} onClick={() => void query.refetch()}>刷新</Button>
    <PermissionGate permission="files:create" permissions={permissions.data?.effective}><Button type="primary" icon={<IconUpload />} loading={uploadMutation.isPending} disabled={uploadMutation.isPending || !applications.isSuccess} onClick={() => inputRef.current?.click()}>上传文件</Button></PermissionGate>
    <input ref={inputRef} hidden type="file" accept="image/png,image/jpeg,image/webp,application/pdf,text/plain" onChange={(event) => { const file = event.target.files?.[0]; if (file) uploadMutation.mutate(file); event.target.value = '' }} />
  </Space></div><Card>
    <Space className="filter-row"><Select style={{ width: 200 }} disabled={uploadMutation.isPending || deleteMutation.isPending || applications.isLoading} value={applicationCode} options={(applications.data ?? []).map((app) => ({ label: app.name, value: app.appCode }))} onChange={(value) => { filter('applicationCode', value); setDetail(null) }} /><Input.Search allowClear placeholder="按文件名搜索" value={keyword} onChange={(value) => filter('keyword', value)} /><Select allowClear placeholder="状态" style={{ width: 150 }} value={status || undefined} onChange={(value) => filter('status', value)} options={['PENDING', 'CONFIRMED']} /><Select allowClear placeholder="文件类型" style={{ width: 180 }} value={contentType || undefined} onChange={(value) => filter('contentType', value)} options={['image/png', 'image/jpeg', 'image/webp', 'application/pdf', 'text/plain']} /><Button onClick={() => void query.refetch()}>查询</Button><Button onClick={() => { setParams({}); setSelected([]) }}>重置</Button><Button status="danger" icon={<IconDelete />} loading={deleteMutation.isPending} disabled={!selected.length || !canDelete || deleteMutation.isPending} onClick={() => confirmDelete(selected)}>批量删除</Button>{progress !== null && <span className="muted">上传中 {progress}%</span>}</Space>
    {applications.isError && <Alert type="error" content={getApiMessage(applications.error)} action={<Button onClick={() => void applications.refetch()}>重试</Button>} />}{query.isLoading ? <div className="state"><Spin />加载中...</div> : query.isError ? <Alert type="error" content={getApiMessage(query.error)} action={<Button onClick={() => void query.refetch()}>重试</Button>} /> : !rows.length ? <Empty description="暂无文件" /> : <Table scroll={{ x: 950 }} rowKey="objectKey" columns={[{ title: '文件名', dataIndex: 'fileName' }, { title: '所属应用', render: (_: unknown, row: FileItem) => applications.data?.find((app) => app.appCode === row.applicationCode)?.name ?? row.applicationCode }, { title: '类型', dataIndex: 'contentType' }, { title: '大小', render: (_: unknown, row: FileItem) => `${Math.ceil(row.fileSize / 1024)} KB` }, { title: '状态', dataIndex: 'status' }, { title: '操作', width: 260, fixed: 'right', render: (_: unknown, row: FileItem) => <Space><Button type="text" onClick={() => setDetail(row)}>详情</Button><Button type="text" disabled={row.status !== 'CONFIRMED'} icon={<IconDownload />} onClick={() => void download(row)}>下载</Button><PermissionGate permission="files:delete" permissions={permissions.data?.effective}><Button type="text" status="danger" disabled={row.status !== 'CONFIRMED' || deleteMutation.isPending} icon={<IconDelete />} onClick={() => confirmDelete([row.objectKey])}>删除</Button></PermissionGate></Space> }]} data={rows} rowSelection={{ checkboxProps: (row: FileItem) => ({ disabled: row.status !== 'CONFIRMED' }), selectedRowKeys: selected, onChange: (keys) => setSelected(keys as string[]) }} pagination={false} />}
    <Pagination className="pagination" current={page} total={query.data?.total ?? 0} pageSize={10} showTotal showJumper onChange={setPage} />
  </Card><Drawer width={500} visible={detail !== null} title="文件详情" footer={null} onCancel={() => setDetail(null)}><Descriptions column={1} border data={[
    { label: '文件名', value: detail?.fileName ?? '-' }, { label: '所属应用', value: applications.data?.find((app) => app.appCode === detail?.applicationCode)?.name ?? detail?.applicationCode ?? '-' }, { label: '对象键', value: detail?.objectKey ?? '-' }, { label: '类型', value: detail?.contentType ?? '-' }, { label: '字节数', value: String(detail?.fileSize ?? 0) }, { label: '状态', value: detail?.status ?? '-' }, { label: '上传人标识', value: detail?.ownerId ?? '-' }, { label: '业务类型', value: detail?.bizType ?? '-' }, { label: '创建时间', value: detail?.createdAt ?? '-' }, { label: '更新时间', value: detail?.updatedAt ?? '-' },
  ]} /><Alert className="section-card" content="仅已确认的文件可以下载和删除。" /></Drawer></div>
}

