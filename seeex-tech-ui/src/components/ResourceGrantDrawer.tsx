import { Alert, Button, Card, Drawer, Empty, Message, Modal, Select, Space, Spin, Tag, Tree } from '@arco-design/web-react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { api, getApiMessage, notifyApiError } from '../lib/api'
import { loadAll, treeNodes, type TreeRow } from '../lib/management'

interface Grant { resourceId: number; effect: string }
interface GrantResource extends TreeRow { status: string; entryType: 'WEB' | 'ADMIN'; resourceType: string; menuCategory: string; permissionCode: string }
interface Props { endpoint: string; title: string; onClose: () => void }

/** 应用内按端查看，完整提交当前应用授权，其他端和停用资源原授权保留。 */
export function ResourceGrantDrawer({ endpoint, title, onClose }: Props) {
  const client = useQueryClient()
  const [app, setApp] = useState('platform')
  const [entry, setEntry] = useState<string>()
  const [draft, setDraft] = useState<Grant[] | null>(null)
  const apps = useQuery({ queryKey: ['grant-apps'], queryFn: async () => (await api.get<{ data: { appCode: string; name: string }[] }>('/rbac/applications')).data.data })
  const resources = useQuery({ queryKey: ['grant-resources', app], queryFn: () => loadAll<GrantResource>('/rbac/resources', { applicationCode: app }) })
  const current = useQuery({ queryKey: ['grant-current', endpoint], queryFn: async () => (await api.get<{ data: Grant[] }>(endpoint)).data.data })
  const allRows = resources.data ?? []
  const ids = new Set(allRows.map((row) => row.id))
  const original = (current.data ?? []).filter((grant) => ids.has(grant.resourceId))
  const value = draft ?? original
  const displayed = allRows.filter((row) => !entry || row.entryType === entry)
  const visibleIds = new Set(displayed.map((row) => row.id))
  const save = useMutation({ mutationFn: () => api.put(endpoint, value, { params: { applicationCode: app } }), onSuccess: async () => { Message.success('权限已保存'); await client.invalidateQueries(); onClose() }, onError: (error) => { notifyApiError(error) } })
  const failed = resources.error ?? current.error ?? apps.error
  const busy = resources.isLoading || current.isLoading || apps.isLoading
  const blocked = busy || Boolean(failed) || save.isPending
  const options = [{ label: '基础平台', value: 'platform' }, ...(apps.data ?? []).filter((row) => row.appCode !== 'platform').map((row) => ({ label: row.name, value: row.appCode }))]
  function close() { if (save.isPending) return; if (draft) Modal.confirm({ title: '放弃未保存的权限修改？', onOk: onClose }); else onClose() }
  function change(effect: string, keys: string[]) {
    if (blocked) return
    const selected = new Set(keys.map(Number).filter((id) => visibleIds.has(id)))
    const newlyInactive = [...selected].some((id) => allRows.find((row) => row.id === id)?.status !== 'ACTIVE' && !original.some((grant) => grant.resourceId === id && grant.effect === effect))
    if (newlyInactive) { Message.warning('停用资源仅可保留或移除原授权，不能新增授权'); return }
    setDraft([...value.filter((grant) => !visibleIds.has(grant.resourceId) || grant.effect !== effect && !selected.has(grant.resourceId)), ...[...selected].map((resourceId) => ({ resourceId, effect }))])
  }
  function confirmSave() {
    if (blocked) return
    const key = (grant: Grant) => `${grant.resourceId}:${grant.effect}`
    const added = value.filter((grant) => !original.some((old) => key(old) === key(grant))).length
    const removed = original.filter((grant) => !value.some((next) => key(next) === key(grant))).length
    Modal.confirm({ title: '保存当前应用权限？', content: `新增 ${added} 项，移除 ${removed} 项。包含当前应用所有端的修改，其他应用权限保留。`, onOk: () => save.mutateAsync() })
  }
  const treeData = treeNodes(displayed.map((row) => ({ ...row, name: `${row.name} · ${row.entryType === 'ADMIN' ? '后台' : 'Web'} · ${row.resourceType}${row.status === 'ACTIVE' ? '' : '（停用）'}` })))
  return <Drawer width={900} visible title={title} onCancel={close} onOk={confirmSave} maskClosable={!save.isPending} closable={!save.isPending} confirmLoading={save.isPending} okButtonProps={{ disabled: blocked }}>
    <Space wrap><Select disabled={save.isPending} style={{ width: 260 }} value={app} options={options} onChange={(next) => { const changeApp = () => { setApp(next); setEntry(undefined); setDraft(null) }; if (draft) Modal.confirm({ title: '切换应用并放弃当前修改？', onOk: changeApp }); else changeApp() }} /><Select disabled={blocked} allowClear style={{ width: 180 }} placeholder="全部端" value={entry} onChange={setEntry} options={[{ label: 'Web 业务端', value: 'WEB' }, { label: '管理后台', value: 'ADMIN' }]} /><Tag>允许 {value.filter((row) => row.effect === 'ALLOW').length}</Tag><Tag color="red">拒绝 {value.filter((row) => row.effect === 'DENY').length}</Tag></Space>
    <Alert className="section-card" content="每个节点独立授权；选择父节点不会自动勾选所有子节点。DENY 优先，切换端保留当前草稿。" />
    {failed ? <Alert type="error" content={getApiMessage(failed)} action={<Button onClick={() => { void resources.refetch(); void current.refetch(); void apps.refetch() }}>重试</Button>} /> : busy ? <Spin /> : !displayed.length ? <Empty description="当前范围暂无资源" /> : <div className="grant-columns">{['ALLOW', 'DENY'].map((effect) => <Card key={effect} title={effect === 'ALLOW' ? '允许访问' : '明确拒绝（优先）'}><Tree disabled={save.isPending} blockNode checkable checkStrictly selectable={false} treeData={treeData} checkedKeys={value.filter((grant) => grant.effect === effect && visibleIds.has(grant.resourceId)).map((grant) => String(grant.resourceId))} onCheck={(keys) => change(effect, keys)} /></Card>)}</div>}
    <Space className="section-card"><Button disabled={blocked} onClick={() => setDraft(value.filter((grant) => !visibleIds.has(grant.resourceId)))}>清空当前显示范围</Button><Button status="danger" disabled={blocked} onClick={() => Modal.confirm({ title: '清空当前应用所有端的授权？', onOk: () => setDraft([]) })}>清空当前应用授权</Button></Space>
    {save.isError && <Alert type="error" content={getApiMessage(save.error)} />}
  </Drawer>
}
