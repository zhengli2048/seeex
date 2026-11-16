import { Alert, Button, Card, Descriptions, Drawer, Form, Input, Message, Modal, Popconfirm, Select, Space, Switch, Table, Tabs, Tag } from '@arco-design/web-react'
import { IconEye, IconRefresh, IconSave, IconSync } from '@arco-design/web-react/icon'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useEffect, useState } from 'react'
import { api, getApiMessage, type PageResult, notifyApiError } from '../../lib/api'
import { PermissionGate } from '../../components/PermissionGate'

import { WeComDirectory } from './WeComDirectory'

interface PermissionResponse { effective: string[] }
interface WecomConfig { configured: boolean; provider: string; callbackPath: string; corpId?: string; agentId?: string; redirectUri?: string; loginEnabled?: boolean }
interface SyncRun { runId: string; status: string; userCount: number; failedCount?: number; error?: string; createdAt?: string }
interface SyncEvent { id: number; eventNo: string; eventType: string; status: string; retryCount: number; receivedAt: string; errorMessage?: string }

export function WecomPage() {
  const [tab, setTab] = useState('config')
  const [dirty, setDirty] = useState(false)
  const [runPage, setRunPage] = useState(1)
  const [runDetail, setRunDetail] = useState<string>()
  const [itemPage, setItemPage] = useState(1)
  const [eventPage, setEventPage] = useState(1)
  const [eventStatus, setEventStatus] = useState<string>()
  const [detail, setDetail] = useState<SyncEvent | null>(null)
  const [form] = Form.useForm()
  const queryClient = useQueryClient()

  const permissions = useQuery({ queryKey: ['permissions'], queryFn: async () => (await api.get<{ data: PermissionResponse }>('/permissions')).data.data })
  const can = (permission: string) => permissions.data?.effective.includes(permission) ?? false
  const config = useQuery({ queryKey: ['wecom-config'], queryFn: async () => (await api.get<{ data: WecomConfig }>('/wecom/config')).data.data, enabled: can('wecom:config:view'), retry: false })
  const syncRuns = useQuery({ queryKey: ['wecom-sync-runs', runPage], queryFn: async () => (await api.get<{ data: PageResult<SyncRun> }>('/integrations/wecom/sync/runs', { params: { page: runPage, pageSize: 20 } })).data.data, enabled: can('wecom:sync:view'), refetchInterval: tab === 'sync' && can('wecom:sync:view') ? 5000 : false, retry: false })
  const events = useQuery({ queryKey: ['wecom-events', eventPage, eventStatus], queryFn: async () => (await api.get<{ data: PageResult<SyncEvent> }>('/integrations/wecom/events', { params: { page: eventPage, pageSize: 20, status: eventStatus } })).data.data, enabled: can('wecom:events:view'), retry: false })

  const runItems = useQuery({ queryKey: ['wecom-run-items', runDetail, itemPage], queryFn: async () => (await api.get<{ data: PageResult<{ id: number; attemptNo: number; objectType: string; externalKey: string; status: string; errorMessage?: string }> }>(`/integrations/wecom/sync/runs/${runDetail}/items`, { params: { page: itemPage, pageSize: 20 } })).data.data, enabled: Boolean(runDetail) && can('wecom:sync:view') })
  const retryRun = useMutation({ mutationFn: (id: string) => api.post(`/integrations/wecom/sync/runs/${id}/retry`), onSuccess: () => { Message.success('批次已重新排队，原尝试明细保留'); void queryClient.invalidateQueries({ queryKey: ['wecom-sync-runs'] }) }, onError: (error) => notifyApiError(error) })

  useEffect(() => { if (config.data && !dirty) form.setFieldsValue(config.data) }, [config.data, dirty, form])
  useEffect(() => { const has = (name: string) => permissions.data?.effective.includes(`wecom:${name}:view`) ?? false; if (!dirty && permissions.isSuccess && !has(tab)) { const first = ['config', 'sync', 'events'].find(has); if (first) setTab(first) } }, [permissions.data, permissions.isSuccess, tab, dirty])
  const configBlocked = !can('wecom:config:update') || !can('wecom:config:view') || !config.isSuccess || config.isFetching

  const saveConfig = useMutation({
    mutationFn: (values: Record<string, unknown>) => { if (configBlocked) return Promise.reject(new Error('请先成功读取配置并确认具有修改权限')); return api.put('/wecom/config', values) },
    onSuccess: (response) => { queryClient.setQueryData(['wecom-config'], (response.data as { data: WecomConfig }).data); setDirty(false); form.setFieldsValue({ secret: '', token: '', aesKey: '' }); Message.success('配置已保存'); void queryClient.invalidateQueries({ queryKey: ['wecom-config'] }) },
    onError: (error) => notifyApiError(error),
  })
  const checkConnection = useMutation({
    mutationFn: () => api.post('/wecom/config/check'),
    onSuccess: (res) => { const ok = (res.data as { data?: { ok: boolean } }).data?.ok; ok ? Message.success('连接正常') : Message.error('连接失败') },
    onError: (error) => notifyApiError(error),
  })
  const runSync = useMutation({
    mutationFn: () => api.post('/integrations/wecom/sync/full'),
    onSuccess: (response) => { const result = (response.data as { data: SyncRun }).data; if (result.status === 'FAILED') Message.error(result.error ?? '同步失败'); else if (result.status === 'PARTIAL') Message.warning('同步部分完成，请查看错误'); else if (result.status === 'PENDING' || result.status === 'RUNNING') Message.success('同步已排队，可在批次列表查看进度'); else Message.success('同步完成'); void queryClient.invalidateQueries({ queryKey: ['wecom-sync-runs', runPage] }) },
    onError: (error) => notifyApiError(error),
  })
  const retryEvent = useMutation({
    mutationFn: (id: number) => api.post(`/integrations/wecom/events/${id}/retry`),
    onSuccess: () => { Message.success('已重试'); void queryClient.invalidateQueries({ queryKey: ['wecom-events', eventPage, eventStatus] }) },
    onError: (error) => notifyApiError(error),
  })

  const statusTag = (s: string) => <Tag color={s === 'SUCCESS' ? 'green' : s === 'FAILED' || s === 'DEAD_LETTER' ? 'red' : 'arcoblue'}>{({ PENDING: '待同步', RUNNING: '同步中', SUCCESS: '成功', PARTIAL: '部分完成', FAILED: '失败', RECEIVED: '已接收', PROCESSING: '处理中', RETRY_WAIT: '等待重试', DEAD_LETTER: '死信' } as Record<string, string>)[s] ?? s}</Tag>

  if (permissions.isLoading) return <div className="state">正在读取页面权限…</div>
  if (permissions.isError) return <Alert type="error" content={`权限读取失败：${getApiMessage(permissions.error)}`} action={<Button onClick={() => void permissions.refetch()}>重试</Button>} />
  if (!['config', 'sync', 'events'].some((name) => can(`wecom:${name}:view`))) return <Alert type="error" content="暂无企业微信管理页面的读取权限" />

  return (
    <div>
      <div className="page-heading"><div><h1>企业微信</h1><p className="muted">连接配置、通讯录同步与事件回调。</p></div></div>
      <Tabs activeTab={tab} onChange={(next) => { if (dirty) Modal.confirm({ title: '放弃未保存的配置修改？', onOk: () => { setDirty(false); form.resetFields(); form.setFieldsValue(config.data ?? {}); setTab(next) } }); else setTab(next) }}>
        <Tabs.TabPane key="config" title="连接配置">
          <Card>
            {!can('wecom:config:view') ? <Alert type="warning" content="暂无连接配置读取权限" /> : <>
            {config.isError && <Alert type="error" content={getApiMessage(config.error)} action={<Button size="small" onClick={() => void config.refetch()}>重试</Button>} />}
            {config.data || dirty ? <Form form={form} disabled={saveConfig.isPending || configBlocked} layout="vertical" onChange={() => setDirty(true)} onSubmit={(values) => { if (!configBlocked && !saveConfig.isPending) saveConfig.mutate(values) }} style={{ maxWidth: 520 }}>
                <Form.Item field="corpId" label="CorpID" rules={[{ required: true, message: '请输入 CorpID' }]}><Input /></Form.Item>
                <Form.Item field="agentId" label="AgentID" rules={[{ required: true, message: '请输入 AgentID' }]}><Input /></Form.Item>
                <Form.Item field="callbackPath" label="回调地址"><Input disabled /></Form.Item>
                <Form.Item field="redirectUri" label="登录回调 Web 地址" rules={[{ required: true }]}><Input placeholder="https://console.example.com/login" /></Form.Item><Form.Item field="token" label="回调 Token"><Input.Password placeholder="留空保留原值" /></Form.Item><Form.Item field="secret" label="Secret"><Input.Password placeholder="留空表示不修改" /></Form.Item>
                <Form.Item field="aesKey" label="AESKey"><Input.Password placeholder="留空表示不修改" /></Form.Item>
                <Form.Item field="loginEnabled" label="登录开关" triggerPropName="checked"><Switch /></Form.Item>
                <Space>
                  <Button type="primary" htmlType="submit" icon={<IconSave />} disabled={configBlocked} loading={saveConfig.isPending}>保存配置</Button>
                  <Button icon={<IconSync />} disabled={configBlocked || saveConfig.isPending} loading={checkConnection.isPending} onClick={() => checkConnection.mutate()}>连接检查</Button>
                </Space>
              </Form> : config.isLoading ? <div className="state">正在读取连接配置…</div> : null}</>}
            <Alert style={{ marginTop: 16 }} type="info" content="Secret/AESKey 只写不读，保存后仅显示 ****；企微当前未真实联调。" />
          </Card>{can('wecom:config:view') && <WeComDirectory mode="mapping" canEdit={can('wecom:config:update')} canPreview={false} onSync={async () => { await runSync.mutateAsync() }} syncing={runSync.isPending} />}
        </Tabs.TabPane>
        <Tabs.TabPane key="sync" title="通讯录同步">
          <Card>
            {!can('wecom:sync:view') ? <Alert type="warning" content="暂无同步批次读取权限" /> : <>
            <Space style={{ marginBottom: 16 }}>
              <Button type="primary" loading={runSync.isPending} disabled={!permissions.data?.effective.includes('wecom:sync:run')} icon={<IconRefresh />} onClick={() => Modal.confirm({ title: '发起全量同步？', onOk: () => runSync.mutate() })}>不预览直接同步</Button>
            </Space>
            <Button onClick={() => void syncRuns.refetch()}>刷新批次</Button>{syncRuns.isError ? <Alert type="error" content={getApiMessage(syncRuns.error)} action={<Button onClick={() => void syncRuns.refetch()}>重试</Button>} /> : <Table loading={syncRuns.isLoading} rowKey="runId" pagination={{ current: runPage, pageSize: 20, total: syncRuns.data?.total ?? 0, onChange: setRunPage }} data={syncRuns.data?.items ?? []} columns={[
              { title: '批次号', dataIndex: 'runId', ellipsis: true },
              { title: '状态', dataIndex: 'status', render: (v: string) => statusTag(v) },
              { title: '已处理员工数', dataIndex: 'userCount' },
              { title: '失败对象数', dataIndex: 'failedCount' },
              { title: '错误', dataIndex: 'error', ellipsis: true, render: (v: string) => v || '-' },
              { title: '操作', render: (_: unknown, row: SyncRun) => <Space><Button type="text" onClick={() => { setRunDetail(row.runId); setItemPage(1) }}>处理明细</Button>{can('wecom:sync:run') && <Button type="text" disabled={retryRun.isPending || !['FAILED', 'PARTIAL'].includes(row.status)} onClick={() => Modal.confirm({ title: '重新读取远端快照并重试此批次？', content: '已提交的员工写入不会回滚；新尝试会校验版本并保留原明细。', onOk: () => retryRun.mutateAsync(row.runId) })}>重试批次</Button>}</Space> },
            ]} />}</>}
          </Card>{can('wecom:sync:view') && <WeComDirectory mode="preview" canEdit={false} canPreview={can('wecom:sync:run')} onSync={async () => { await runSync.mutateAsync() }} syncing={runSync.isPending} />}
        </Tabs.TabPane>
        <Tabs.TabPane key="events" title="事件回调">
          <Card>
            {!can('wecom:events:view') ? <Alert type="warning" content="暂无事件记录读取权限" /> : <>
            <Space><Select allowClear placeholder="事件状态" style={{ width: 190 }} value={eventStatus} onChange={(value) => { setEventStatus(value); setEventPage(1) }} options={['RECEIVED', 'PROCESSING', 'SUCCESS', 'RETRY_WAIT', 'DEAD_LETTER']} /><Button onClick={() => void events.refetch()}>刷新事件</Button></Space>{events.isError ? <Alert type="error" content={getApiMessage(events.error)} action={<Button onClick={() => void events.refetch()}>重试</Button>} /> : <Table loading={events.isLoading} rowKey="id" pagination={{ current: eventPage, pageSize: 20, total: events.data?.total ?? 0, onChange: setEventPage }} data={events.data?.items ?? []} columns={[
              { title: '类型', dataIndex: 'eventType' },
              { title: '状态', dataIndex: 'status', render: (v: string) => statusTag(v) },
              { title: '幂等号', dataIndex: 'eventNo', ellipsis: true },
              { title: '重试次数', dataIndex: 'retryCount' },
              { title: '错误', dataIndex: 'errorMessage', ellipsis: true, render: (v: string) => v || '-' },
              { title: '操作', render: (_: unknown, row: SyncEvent) => (
                <Space>
                  <Button type="text" onClick={() => setDetail(row)}>详情</Button>
                  <PermissionGate permission="wecom:events:retry" permissions={permissions.data?.effective}>
                    <Popconfirm title="确认重试？" disabled={!['RETRY_WAIT', 'DEAD_LETTER'].includes(row.status)} onOk={() => retryEvent.mutate(row.id)}>
                      <Button type="text" size="small" loading={retryEvent.isPending} icon={<IconSync />} disabled={!['RETRY_WAIT', 'DEAD_LETTER'].includes(row.status)}>重试</Button>
                    </Popconfirm>
                  </PermissionGate>
                  <PermissionGate permission="wecom:events:raw" permissions={permissions.data?.effective}>
                    <Button type="text" size="small" icon={<IconEye />} onClick={async () => { try { const r = await api.get<{ data: string }>(`/integrations/wecom/events/${row.id}/raw`); Modal.info({ title: '受控事件原文', content: <Input.TextArea readOnly value={r.data.data ?? ''} autoSize /> }) } catch (error) { notifyApiError(error) } }}>原始</Button>
                  </PermissionGate>
                </Space>
              ) },
            ]} />}</>}
          </Card>
        </Tabs.TabPane>
      </Tabs><Drawer visible={runDetail !== undefined} width={900} title="同步尝试明细" footer={null} onCancel={() => setRunDetail(undefined)}><Button onClick={() => void runItems.refetch()}>刷新明细</Button>{runItems.isError ? <Alert type="error" content={getApiMessage(runItems.error)} action={<Button onClick={() => void runItems.refetch()}>重试</Button>} /> : <Table rowKey="id" loading={runItems.isLoading} data={runItems.data?.items ?? []} pagination={{ current: itemPage, pageSize: 20, total: runItems.data?.total ?? 0, onChange: setItemPage }} columns={[{ title: '尝试', dataIndex: 'attemptNo' }, { title: '对象类型', dataIndex: 'objectType' }, { title: '外部标识', dataIndex: 'externalKey' }, { title: '结果', dataIndex: 'status' }, { title: '说明', dataIndex: 'errorMessage' }]} />}</Drawer><Drawer width={500} title="事件详情" visible={detail !== null} footer={null} onCancel={() => setDetail(null)}><Descriptions column={1} border data={[{ label: '事件编号', value: detail?.eventNo ?? '-' }, { label: '类型', value: detail?.eventType ?? '-' }, { label: '状态', value: detail?.status ?? '-' }, { label: '接收时间', value: detail?.receivedAt ?? '-' }, { label: '重试次数', value: String(detail?.retryCount ?? 0) }, { label: '错误', value: detail?.errorMessage ?? '-' }]} /></Drawer>
    </div>
  )
}
