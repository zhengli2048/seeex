import {
  Alert,
  Button,
  Card,
  Descriptions,
  Drawer,
  Input,
  Message,
  Modal,
  Space,
  Switch,
  Table,
  Tabs,
} from '@arco-design/web-react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { api, getApiMessage, notifyApiError } from '../../lib/api'
import { DeliveryPanel } from './DeliveryPanel'
import {
  ApprovalPreview,
  Field,
  FlowTree,
  QueryState,
  readApi,
  ReasonModal,
  StateTag,
  useActionKeys,
  useDraftGuard,
} from './shared'
import { freshConfig } from './types'
import type {
  ActionBody,
  AttemptView,
  ApplicationForm,
  FulfillmentForm,
  RequestView,
  TaskView,
  ValidationView,
} from './types'
import type { EditorValue } from './RequestEditor'
interface ActionDialog {
  title: string
  path: string
  body: ActionBody
  required: boolean
  tag?: string
}
interface ConfigEditor {
  form: FulfillmentForm
  version: number
  original: FulfillmentForm
}
/** 服务端allowedActions控制可见操作；请求始终携带打开动作时的版本。 */
export function RequestDetail({
  id,
  onClose,
  onEdit,
  onCopy,
}: {
  id: number
  onClose: () => void
  onEdit?: (value: EditorValue) => void
  onCopy?: (form: ApplicationForm) => void
}) {
  const navigate = useNavigate()
  const client = useQueryClient(),
    key = useActionKeys(),
    query = useQuery({
      queryKey: ['onboarding', 'request', id],
      queryFn: () => readApi<RequestView>(`/app-requests/${id}`),
    })
  const [historicalAttempt, setHistoricalAttempt] = useState<number>()
  const [preview, setPreview] = useState<ValidationView | null>(null),
    [dialog, setDialog] = useState<ActionDialog | null>(null),
    [comment, setComment] = useState(''),
    [rotation, setRotation] = useState(false),
    [config, setConfig] = useState<ConfigEditor | null>(null),
    [validatedVersion, setValidatedVersion] = useState<number>(),
    [sensitive, setSensitive] = useState(false)
  const action = useMutation({
    mutationFn: async (input: {
      path: string
      body: unknown
      tag?: string
      method?: 'post' | 'put' | 'delete'
    }) =>
      (
        await api.request<{ data?: RequestView }>({
          url: input.path,
          method: input.method ?? 'post',
          data: input.method === 'delete' ? undefined : input.body,
          params: input.method === 'delete' ? input.body : undefined,
          headers: { 'Idempotency-Key': key(input.path, input.body) },
        })
      ).data.data,
    onSuccess: (result, input) => {
      if (result?.item) client.setQueryData(['onboarding', 'request', id], result)
      setDialog(null)
      setComment('')
      if (input.tag === 'CONFIG') setConfig(null)
      if (input.tag === 'VALIDATE_CONFIG') {
        setValidatedVersion(result?.item.rowVersion)
        Message.success('已保存的配置校验通过')
      } else {
        setValidatedVersion(undefined)
        Message.success(input.method === 'delete' ? '草稿已删除' : '操作成功')
      }
      if (input.tag === 'SUBMIT') setPreview(null)
      if (input.method === 'delete') onClose()
      if (input.tag === 'COMPLETE') void client.invalidateQueries()
      else void client.invalidateQueries({ queryKey: ['onboarding'] })
    },
    onError: notifyApiError,
  })
  const precheck = useMutation({
    mutationFn: async (version: number) =>
      (await api.post<{ data: ValidationView }>(`/app-requests/${id}/validate`, { expectedVersion: version }))
        .data.data,
    onSuccess: setPreview,
    onError: notifyApiError,
  })
  const attachments = useQuery({
    queryKey: ['onboarding', 'attachments', id],
    queryFn: () =>
      readApi<{ objectKey: string; fileName: string; fileSize: number }[]>(`/app-requests/${id}/attachments`),
    enabled: query.isSuccess && Boolean(query.data.form.attachmentKeys.length),
  })
  const historical = useQuery({
    queryKey: ['onboarding', 'attempt', id, historicalAttempt],
    enabled: historicalAttempt !== undefined,
    queryFn: () => readApi<AttemptView>(`/app-requests/${id}/attempts/${historicalAttempt}`),
  })
  const busy = action.isPending || precheck.isPending,
    dirty =
      Boolean(config && JSON.stringify(config.form) !== JSON.stringify(config.original)) ||
      Boolean(dialog && comment.trim())
  useDraftGuard(dirty, busy)
  function close() {
    if (busy) return
    if (dirty || sensitive)
      Modal.confirm({
        title: sensitive ? '关闭后不能回查当前凭证，确认已保存？' : '放弃未保存内容并关闭详情？',
        onOk: onClose,
      })
    else onClose()
  }
  function open(title: string, path: string, body: ActionBody, required = true, tag?: string) {
    action.reset()
    setComment('')
    setRotation(false)
    setDialog({ title, path, body, required, tag })
  }
  function taskAction(task: TaskView, decision?: string) {
    open(
      decision === 'APPROVE'
        ? '通过此审批任务？'
        : decision === 'REJECT'
          ? '驳回此申请？'
          : decision === 'RETURN'
            ? '退回申请人补充？'
            : '领取此审批任务？',
      `/approval-tasks/${task.id}/${decision ? 'decision' : 'claim'}`,
      { expectedVersion: task.rowVersion, decision },
      Boolean(decision && decision !== 'APPROVE'),
    )
  }
  const data = query.data,
    allowed = (name: string) => data?.allowedActions.includes(name) ?? false
  const download = async (objectKey: string) => {
    const target = window.open('about:blank', '_blank')
    if (target) target.opener = null
    try {
      const url = await readApi<string>(`/app-requests/${id}/attachments/download`, { objectKey })
      if (target) target.location.replace(url)
      else {
        Message.error('浏览器阻止打开，请允许弹窗后重试')
      }
    } catch (error) {
      target?.close()
      notifyApiError(error)
    }
  }
  return (
    <Drawer
      className="onboarding-drawer"
      visible
      width={1040}
      title={data ? `${data.item.name || data.item.appCode} · ${data.item.requestNo}` : '申请详情'}
      footer={null}
      onCancel={close}
      maskClosable={!busy}
      closable={!busy}
      unmountOnExit
    >
      <QueryState loading={query.isLoading} error={query.error} onRetry={() => void query.refetch()}>
        {data && (
          <>
            <Space wrap>
              <StateTag state={data.item.state} />
              <span>申请人：{data.item.applicantName}</span>
              <span>第{data.item.attemptNo}次审批</span>
              <Button
                disabled={busy || Boolean(config) || Boolean(dialog)}
                onClick={() => void query.refetch()}
              >
                刷新详情
              </Button>
              {allowed('EDIT') && onEdit && (
                <Button
                  disabled={busy}
                  onClick={() =>
                    onEdit({ id, version: data.item.rowVersion, form: structuredClone(data.form) })
                  }
                >
                  编辑草稿
                </Button>
              )}
              {allowed('COPY') && onCopy && (
                <Button disabled={busy} onClick={() => onCopy(structuredClone(data.form))}>
                  复制为新申请
                </Button>
              )}
              {!onEdit && (allowed('EDIT') || allowed('COPY')) && (
                <Button disabled={busy} onClick={() => navigate('/application-requests?request=' + id)}>
                  前往我的申请编辑
                </Button>
              )}
              {allowed('VALIDATE') && (
                <Button
                  type="primary"
                  loading={precheck.isPending}
                  disabled={busy}
                  onClick={() => {
                    action.reset()
                    precheck.mutate(data.item.rowVersion)
                  }}
                >
                  预检并确认提交
                </Button>
              )}
              {allowed('WITHDRAW') && (
                <Button
                  status="warning"
                  disabled={busy}
                  onClick={() =>
                    open('撤回未决申请？', `/app-requests/${id}/withdraw`, {
                      expectedVersion: data.item.rowVersion,
                    })
                  }
                >
                  撤回
                </Button>
              )}
              {allowed('DELETE') && (
                <Button
                  status="danger"
                  disabled={busy}
                  onClick={() =>
                    Modal.confirm({
                      title: '删除从未提交的草稿？',
                      onOk: () =>
                        action.mutateAsync({
                          path: `/app-requests/${id}/draft`,
                          body: { expectedVersion: data.item.rowVersion },
                          method: 'delete',
                        }),
                    })
                  }
                >
                  删除草稿
                </Button>
              )}
              {allowed('CANCEL') && (
                <Button
                  status="danger"
                  disabled={busy}
                  onClick={() =>
                    open(
                      '取消未注册申请并释放编码？审批历史会保留。',
                      `/application-fulfillments/${id}/cancel`,
                      { expectedVersion: data.item.rowVersion },
                    )
                  }
                >
                  取消未交付申请
                </Button>
              )}
              {allowed('RECOVER') && (
                <Button
                  disabled={busy}
                  onClick={() =>
                    open('恢复异常申请？未通过的申请将重新经过完整审批。', `/app-requests/${id}/recover`, {
                      expectedVersion: data.item.rowVersion,
                    })
                  }
                >
                  恢复异常
                </Button>
              )}
            </Space>
            {data.exceptionMessage && (
              <Alert className="section-card" type="error" content={data.exceptionMessage} />
            )}
            {action.isError && (
              <Alert className="section-card" type="error" content={getApiMessage(action.error)} />
            )}
            {precheck.isError && (
              <Alert className="section-card" type="error" content={getApiMessage(precheck.error)} />
            )}
            <Tabs defaultActiveTab="application">
              <Tabs.TabPane key="application" title="申请材料">
                <Descriptions
                  column={1}
                  border
                  data={[
                    { label: '应用编码', value: data.form.proposedAppCode },
                    { label: '应用类型 / 环境', value: `${data.form.type} / ${data.form.environment}` },
                    { label: '接入端', value: data.form.requestedEntries.join('、') },
                    { label: '数据级别', value: data.form.dataClassification },
                    { label: '批准域名', value: data.form.requestedDomains.join('、') || '未填写' },
                    { label: '管理员', value: data.admins.map((row) => row.name).join('、') },
                    { label: '描述', value: data.form.description || '未填写' },
                    { label: '业务用途', value: data.form.businessPurpose || '未填写' },
                    { label: '提交时间', value: data.item.submittedAt || '未提交' },
                  ]}
                />
                {data.form.attachmentKeys.length > 0 && (
                  <Card className="section-card" title="申请附件">
                    <QueryState
                      loading={attachments.isLoading}
                      error={attachments.error}
                      onRetry={() => void attachments.refetch()}
                    >
                      <Space direction="vertical">
                        {attachments.data?.map((row) => (
                          <Button key={row.objectKey} onClick={() => void download(row.objectKey)}>
                            {row.fileName} · {(row.fileSize / 1024).toFixed(1)} KB
                          </Button>
                        ))}
                      </Space>
                    </QueryState>
                  </Card>
                )}
              </Tabs.TabPane>
              <Tabs.TabPane key="flow" title="审批路径与意见">
                {data.flow ? (
                  <FlowTree model={data.flow} index={data.nodeIndex} form={data.form} tasks={data.tasks} />
                ) : (
                  <Alert content="尚未提交；预检后可查看匹配的审批路径。" />
                )}
                <Table<TaskView>
                  className="section-card"
                  rowKey="id"
                  data={data.tasks}
                  pagination={{ pageSize: 20 }}
                  columns={[
                    { title: '节点', dataIndex: 'nodeName' },
                    { title: '审批人', dataIndex: 'candidateName' },
                    { title: '规则', render: (_, row) => (row.mode === 'ALL' ? '会签' : '或签') },
                    { title: '状态', render: (_, row) => <StateTag state={row.state} /> },
                    { title: '提醒截止', dataIndex: 'dueAt' },
                    {
                      title: '操作',
                      render: (_, row) => (
                        <Space wrap>
                          {row.allowedActions.includes('CLAIM') && (
                            <Button disabled={busy} onClick={() => taskAction(row)}>
                              领取任务
                            </Button>
                          )}
                          {(['APPROVE', 'REJECT', 'RETURN'] as const)
                            .filter((name) => row.allowedActions.includes(name))
                            .map((name) => (
                              <Button
                                key={name}
                                disabled={busy}
                                status={name === 'REJECT' ? 'danger' : 'default'}
                                onClick={() => taskAction(row, name)}
                              >
                                {name === 'APPROVE' ? '通过' : name === 'REJECT' ? '驳回' : '退回补充'}
                              </Button>
                            ))}
                        </Space>
                      ),
                    },
                  ]}
                />
                <Table
                  rowKey="id"
                  data={data.history}
                  pagination={{ pageSize: 20 }}
                  columns={[
                    { title: '时间', dataIndex: 'createdAt' },
                    { title: '人员', dataIndex: 'actorName' },
                    { title: '动作', dataIndex: 'action' },
                    { title: '意见/原因', dataIndex: 'comment' },
                  ]}
                />
              </Tabs.TabPane>
              <Tabs.TabPane key="fulfillment" title="平台办理">
                <Alert
                  content={`审批通过后才能领取办理。应用所有者和交付收件人固定为 ${data.item.applicantName}，管理员固定为 ${data.admins.map((row) => row.name).join('、')}。`}
                />
                <Space className="section-card" wrap>
                  {allowed('CLAIM_FULFILLMENT') && (
                    <Button
                      type="primary"
                      disabled={busy}
                      onClick={() =>
                        open(
                          '领取此应用接入办理？',
                          `/application-fulfillments/${id}/claim`,
                          { expectedVersion: data.item.rowVersion },
                          false,
                        )
                      }
                    >
                      领取办理
                    </Button>
                  )}
                  {allowed('SAVE_CONFIG') && (
                    <Button
                      disabled={busy}
                      onClick={() => {
                        action.reset()
                        const original = structuredClone(data.fulfillment ?? freshConfig())
                        setConfig({
                          form: original,
                          original: structuredClone(original),
                          version: data.item.rowVersion,
                        })
                      }}
                    >
                      填写/编辑接入配置
                    </Button>
                  )}
                  {allowed('VALIDATE_CONFIG') && (
                    <Button
                      disabled={busy || Boolean(config)}
                      onClick={() =>
                        action.mutate({
                          path: `/application-fulfillments/${id}/validate`,
                          body: { expectedVersion: data.item.rowVersion },
                          tag: 'VALIDATE_CONFIG',
                        })
                      }
                    >
                      校验已保存配置
                    </Button>
                  )}
                  {allowed('COMPLETE') && (
                    <Button
                      type="primary"
                      disabled={busy || Boolean(config) || validatedVersion !== data.item.rowVersion}
                      onClick={() =>
                        open(
                          '完成注册并定向交付？',
                          `/application-fulfillments/${id}/complete`,
                          { expectedVersion: data.item.rowVersion },
                          false,
                          'COMPLETE',
                        )
                      }
                    >
                      完成办理并交付
                    </Button>
                  )}
                  {allowed('REISSUE') && (
                    <Button
                      status="danger"
                      disabled={busy}
                      onClick={() =>
                        open(
                          '轮换旧凭证并补发给原申请人？',
                          `/application-fulfillments/${id}/reissue`,
                          { expectedVersion: data.item.rowVersion },
                          true,
                          'REISSUE',
                        )
                      }
                    >
                      轮换并补发
                    </Button>
                  )}
                </Space>
                {data.fulfillment && (
                  <Descriptions
                    column={1}
                    border
                    data={Object.entries(data.fulfillment).map(([label, value]) => ({
                      label: configLabels[label] ?? label,
                      value: value || '未填写',
                    }))}
                  />
                )}
                {data.fulfillerName && <p>当前办理人：{data.fulfillerName}</p>}
              </Tabs.TabPane>
              {allowed('DELIVERY') && (
                <Tabs.TabPane key="delivery" title="本人接收交付">
                  <DeliveryPanel requestId={id} onSensitiveChange={setSensitive} />
                </Tabs.TabPane>
              )}
              <Tabs.TabPane key="revisions" title="提交修订历史">
                <Table
                  rowKey="revision"
                  data={data.revisions}
                  pagination={{ pageSize: 10 }}
                  expandedRowRender={(row) => (
                    <Descriptions
                      column={1}
                      border
                      data={[
                        { label: '名称', value: row.form.name },
                        { label: '描述', value: row.form.description },
                        { label: '用途', value: row.form.businessPurpose },
                        {
                          label: '端/域名',
                          value:
                            row.form.requestedEntries.join('、') +
                            '；' +
                            row.form.requestedDomains.join('、'),
                        },
                        {
                          label: '环境/数据级别',
                          value: row.form.environment + ' / ' + row.form.dataClassification,
                        },
                        {
                          label: '历史附件',
                          value: (
                            <Space direction="vertical">
                              {row.form.attachmentKeys.map((objectKey) => (
                                <Button key={objectKey} onClick={() => void download(objectKey)}>
                                  {objectKey.split('/').at(-1)}
                                </Button>
                              ))}
                            </Space>
                          ),
                        },
                      ]}
                    />
                  )}
                  columns={[
                    { title: '修订', dataIndex: 'revision' },
                    { title: '流程版本记录', dataIndex: 'definitionId' },
                    { title: '提交时间', dataIndex: 'submittedAt' },
                    {
                      title: '操作',
                      render: (_, row) => (
                        <Button onClick={() => setHistoricalAttempt(row.revision)}>审批图与意见</Button>
                      ),
                    },
                  ]}
                />
              </Tabs.TabPane>
            </Tabs>
            <Drawer
              className="onboarding-drawer"
              visible={historicalAttempt !== undefined}
              title={`第${historicalAttempt ?? ''}次审批 · 只读历史`}
              width={840}
              footer={null}
              onCancel={() => setHistoricalAttempt(undefined)}
            >
              <QueryState
                loading={historical.isLoading}
                error={historical.error}
                onRetry={() => void historical.refetch()}
              >
                {historical.data && (
                  <>
                    <FlowTree
                      model={historical.data.flow}
                      form={historical.data.form}
                      tasks={historical.data.tasks}
                    />
                    <Table
                      rowKey="id"
                      data={historical.data.tasks}
                      pagination={{ pageSize: 20 }}
                      columns={[
                        { title: '节点', dataIndex: 'nodeName' },
                        { title: '审批人', dataIndex: 'candidateName' },
                        { title: '状态', render: (_, row) => <StateTag state={row.state} /> },
                      ]}
                    />
                    <Table
                      rowKey="id"
                      data={historical.data.history}
                      pagination={{ pageSize: 20 }}
                      columns={[
                        { title: '时间', dataIndex: 'createdAt' },
                        { title: '动作', dataIndex: 'action' },
                        { title: '人员', dataIndex: 'actorName' },
                        { title: '意见', dataIndex: 'comment' },
                      ]}
                    />
                  </>
                )}
              </QueryState>
            </Drawer>
            <Modal
              className="onboarding-modal"
              visible={preview !== null}
              title="确认审批路径与接入范围"
              okText="确认提交审批"
              confirmLoading={action.isPending}
              maskClosable={!busy}
              closable={!busy}
              cancelButtonProps={{ disabled: busy }}
              onCancel={() => {
                if (!busy) setPreview(null)
              }}
              onOk={() =>
                preview &&
                action.mutate({
                  path: `/app-requests/${id}/submit`,
                  body: { expectedVersion: preview.requestVersion, definitionId: preview.definitionId },
                  tag: 'SUBMIT',
                })
              }
            >
              {preview && <ApprovalPreview value={preview} />}
              <p>
                应用：{data.form.name}（{data.form.proposedAppCode}）；端：
                {data.form.requestedEntries.join('、')}；域名：{data.form.requestedDomains.join('、')}
                ；管理员：{data.admins.map((row) => row.name).join('、')}。
              </p>
              {action.isError && (
                <Alert
                  type="error"
                  content={getApiMessage(action.error)}
                  action={
                    <Button
                      disabled={busy}
                      onClick={() =>
                        void query.refetch().then((result) => {
                          if (result.data) precheck.mutate(result.data.item.rowVersion)
                        })
                      }
                    >
                      重新载入并预检
                    </Button>
                  }
                />
              )}
            </Modal>
            <ReasonModal
              visible={dialog !== null}
              title={dialog?.title ?? ''}
              busy={busy}
              error={action.error}
              comment={comment}
              onChange={setComment}
              required={dialog?.required}
              blocked={dialog?.tag === 'REISSUE' && !rotation}
              onCancel={() => {
                if (!busy) setDialog(null)
              }}
              onConfirm={() => {
                if (dialog)
                  action.mutate({
                    path: dialog.path,
                    body: {
                      ...dialog.body,
                      comment,
                      confirmRotation: dialog.tag === 'REISSUE' ? rotation : undefined,
                    },
                    tag: dialog.tag,
                  })
              }}
            >
              {dialog?.tag === 'REISSUE' && (
                <Alert
                  type="warning"
                  content={
                    <Space>
                      旧CI和服务凭证将立即失效，仍只交付原申请人。
                      <Switch
                        checked={rotation}
                        disabled={busy}
                        onChange={setRotation}
                        checkedText="确认轮换"
                        uncheckedText="未确认"
                      />
                    </Space>
                  }
                />
              )}
              {dialog?.tag === 'COMPLETE' && (
                <Alert
                  content={`将为 ${data.form.name} 创建应用 ${data.form.proposedAppCode}；所有者/收件人为 ${data.item.applicantName}；管理员为 ${data.admins.map((row) => row.name).join('、')}。办理响应不会显示明文凭证。`}
                />
              )}
            </ReasonModal>
            <Modal
              className="onboarding-modal"
              visible={config !== null}
              title="填写接入配置草稿"
              okText="保存配置草稿"
              confirmLoading={busy}
              maskClosable={!busy}
              closable={!busy}
              cancelButtonProps={{ disabled: busy }}
              onCancel={() => {
                if (!busy) Modal.confirm({ title: '放弃未保存的接入配置？', onOk: () => setConfig(null) })
              }}
              onOk={() => {
                if (config)
                  action.mutate({
                    path: `/application-fulfillments/${id}/config`,
                    body: { form: config.form, expectedVersion: config.version },
                    method: 'put',
                    tag: 'CONFIG',
                  })
              }}
            >
              <Alert
                content={`只允许获批域名：${data.form.requestedDomains.join('、')}。保存后需校验，再确认完成办理。说明中不要填写密码或密钥。`}
              />
              <div className="onboarding-grid">
                {config &&
                  (Object.keys(configLabels) as (keyof FulfillmentForm)[])
                    .filter((name) => name !== 'adminUrl' || data.form.requestedEntries.includes('ADMIN'))
                    .map((name) => (
                      <Field key={name} label={configLabels[name]}>
                        {name === 'integrationNotes' ? (
                          <Input.TextArea
                            value={config.form[name]}
                            maxLength={2000}
                            disabled={busy}
                            onChange={(value) =>
                              setConfig({ ...config, form: { ...config.form, [name]: value } })
                            }
                            autoSize={{ minRows: 3, maxRows: 6 }}
                          />
                        ) : (
                          <Input
                            value={config.form[name]}
                            maxLength={name === 'ssoCallbackPath' ? 256 : 512}
                            disabled={busy}
                            onChange={(value) =>
                              setConfig({ ...config, form: { ...config.form, [name]: value } })
                            }
                          />
                        )}
                      </Field>
                    ))}
              </div>
              {action.isError && <Alert type="error" content={getApiMessage(action.error)} />}
            </Modal>
          </>
        )}
      </QueryState>
    </Drawer>
  )
}
const configLabels: Record<keyof FulfillmentForm, string> = {
  baseUrl: 'Web部署地址',
  adminUrl: '后台部署地址',
  apiUrl: '后端服务地址',
  repositoryUrl: '代码仓库地址',
  logo: 'Logo地址',
  ssoCallbackPath: 'SSO回调路径',
  integrationNotes: '接入说明',
}
