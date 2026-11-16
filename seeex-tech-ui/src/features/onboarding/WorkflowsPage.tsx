import {
  Alert,
  Button,
  Card,
  Drawer,
  Input,
  InputNumber,
  Message,
  Modal,
  Select,
  Space,
  Switch,
  Table,
  Tree,
} from '@arco-design/web-react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { api, getApiMessage, notifyApiError } from '../../lib/api'
import { usePermissions } from '../../lib/management'
import { ApplicationFields } from './ApplicationFields'
import {
  ApprovalPreview,
  Field,
  FlowTree,
  PeopleSelect,
  QueryState,
  readApi,
  ReasonModal,
  StateTag,
  useActionKeys,
  useDictionaryGate,
  useDraftGuard,
  validateForm,
} from './shared'
import { freshForm, freshNode, freshSelector } from './types'
import type {
  ApplicationForm,
  ApprovalNode,
  Context,
  DefinitionPage,
  DefinitionView,
  FlowModel,
  Selector,
  ValidationView,
} from './types'
interface Working {
  id?: number
  definitionKey: string
  name: string
  model: FlowModel
  rowVersion?: number
  sourceId?: number
}
interface Publication {
  row: DefinitionView
  bindingVersion: number
  mode: 'publish' | 'disable'
  bindings: DefinitionPage['bindings']
}
/** 审批流用Arco树与属性面板配置白名单节点；发布版本保持不可变。 */
export function WorkflowsPage() {
  const creationKeys = useActionKeys()
  const permissions = usePermissions(),
    client = useQueryClient(),
    gate = useDictionaryGate()
  const [page, setPage] = useState(1),
    [search, setSearch] = useState(''),
    [keyword, setKeyword] = useState(''),
    [status, setStatus] = useState<string>(),
    [working, setWorking] = useState<Working | null>(null),
    [original, setOriginal] = useState(''),
    [selected, setSelected] = useState(0),
    [past, setPast] = useState<Working[]>([]),
    [future, setFuture] = useState<Working[]>([]),
    [viewing, setViewing] = useState<DefinitionView | null>(null),
    [publication, setPublication] = useState<Publication | null>(null),
    [environments, setEnvironments] = useState<string[]>(['TEST']),
    [reason, setReason] = useState(''),
    [previewing, setPreviewing] = useState<DefinitionView | null>(null),
    [sample, setSample] = useState<ApplicationForm>(),
    [preview, setPreview] = useState<ValidationView>(),
    [error, setError] = useState('')
  const context = useQuery({
    queryKey: ['onboarding', 'context'],
    queryFn: () => readApi<Context>('/onboarding/context'),
    enabled: permissions.isSuccess,
  })
  const query = useQuery({
    queryKey: ['onboarding', 'definitions', page, keyword, status],
    queryFn: () =>
      readApi<DefinitionPage>('/workflow/definitions', { page, pageSize: 20, keyword, state: status }),
    enabled: permissions.can('workflow:definitions:view'),
  })
  const before = useQuery({
    queryKey: ['onboarding', 'publication-diff', publication?.row.id, publication?.bindingVersion],
    enabled: publication?.mode === 'publish',
    queryFn: () =>
      Promise.all(
        [...new Set((publication?.bindings ?? []).map((binding) => binding.definitionId))].map((id) =>
          readApi<DefinitionView>(`/workflow/definitions/${id}`),
        ),
      ),
  })
  const save = useMutation({
    mutationFn: async () => {
      const body = {
        definitionKey: working!.definitionKey,
        name: working!.name,
        model: working!.model,
        expectedVersion: working!.rowVersion,
        sourceId: working!.sourceId,
      }
      return (
        await api.request<{ data: DefinitionView }>({
          url: working!.id ? `/workflow/definitions/${working!.id}/draft` : '/workflow/definitions',
          method: working!.id ? 'put' : 'post',
          data: body,
          headers: working!.id ? undefined : { 'Idempotency-Key': creationKeys('CREATE_DEFINITION', body) },
        })
      ).data.data
    },
    onSuccess: (value) => {
      const next = {
        id: value.id,
        definitionKey: value.definitionKey,
        name: value.name,
        model: value.model,
        rowVersion: value.rowVersion,
      }
      setWorking(next)
      setOriginal(JSON.stringify(next))
      setPast([])
      setFuture([])
      Message.success('流程草稿已保存，尚未发布')
      void client.invalidateQueries({ queryKey: ['onboarding', 'definitions'] })
    },
    onError: notifyApiError,
  })
  const validate = useMutation({
    mutationFn: async (row: DefinitionView | Working) =>
      (await api.post(`/workflow/definitions/${row.id}/validate`, { expectedVersion: row.rowVersion })).data,
    onSuccess: () => Message.success('已保存版本校验通过'),
    onError: notifyApiError,
  })
  const publish = useMutation({
    mutationFn: () =>
      api.post(`/workflow/definitions/${publication!.row.id}/${publication!.mode}`, {
        expectedVersion: publication!.row.rowVersion,
        expectedBindingVersion: publication!.bindingVersion,
        environments,
        comment: reason,
      }),
    onSuccess: () => {
      Message.success(publication?.mode === 'publish' ? '新流程版本已发布' : '已停止此版本的新申请入口')
      setPublication(null)
      setReason('')
      void client.invalidateQueries({ queryKey: ['onboarding'] })
    },
    onError: notifyApiError,
  })
  const simulate = useMutation({
    mutationFn: async () =>
      (await api.post<{ data: ValidationView }>(`/workflow/definitions/${previewing!.id}/preview`, sample))
        .data.data,
    onSuccess: setPreview,
    onError: notifyApiError,
  })
  const busy = save.isPending || validate.isPending || publish.isPending || simulate.isPending,
    dirty = Boolean(working && JSON.stringify(working) !== original)
  useDraftGuard(dirty || Boolean(publication && reason) || Boolean(previewing && sample), busy)
  function edit(value: Working) {
    if (working) setPast((old) => [...old.slice(-29), structuredClone(working)])
    setFuture([])
    setWorking(value)
    setError('')
    save.reset()
  }
  function open(row?: DefinitionView, copy = false) {
    const value: Working = row
      ? {
          id: copy ? undefined : row.id,
          definitionKey: row.definitionKey,
          name: row.name,
          model: structuredClone(row.model),
          rowVersion: copy ? undefined : row.rowVersion,
          sourceId: copy ? row.id : undefined,
        }
      : {
          definitionKey: 'app-onboarding',
          name: '应用接入审批',
          model: { nodes: [freshNode()], fulfillers: freshSelector(), operators: freshSelector() },
        }
    setWorking(value)
    setOriginal(JSON.stringify(value))
    setPast([])
    setFuture([])
    setSelected(0)
    setError('')
    save.reset()
    validate.reset()
  }
  function updateNode(patch: Partial<ApprovalNode>) {
    if (working)
      edit({
        ...working,
        model: {
          ...working.model,
          nodes: working.model.nodes.map((node, i) => (i === selected ? { ...node, ...patch } : node)),
        },
      })
  }
  function close() {
    if (busy) return
    if (dirty) Modal.confirm({ title: '放弃未保存的流程草稿？', onOk: () => setWorking(null) })
    else setWorking(null)
  }
  function validateClient() {
    if (!working) return false
    if (!/^[a-z][a-z0-9-]{1,63}$/.test(working.definitionKey) || !working.name.trim()) {
      setError('请填写有效流程编码和名称')
      return false
    }
    if (
      !working.model.nodes.length ||
      working.model.nodes.length > 20 ||
      new Set(working.model.nodes.map((node) => node.key)).size !== working.model.nodes.length
    ) {
      setError('审批节点须为1–20个，编码不能重复')
      return false
    }
    const index = working.model.nodes.findIndex(
      (node) =>
        !node.name.trim() ||
        !/^[a-z][a-z0-9-]{0,63}$/.test(node.key) ||
        node.dueHours < 1 ||
        node.dueHours > 720,
    )
    if (index >= 0) {
      setSelected(index)
      setError(`节点${index + 1}名称、编码或提醒时限无效`)
      return false
    }
    return true
  }
  function openPublication(row: DefinitionView, mode: 'publish' | 'disable') {
    if (!query.data) return
    setPublication({
      row: structuredClone(row),
      bindingVersion: query.data.bindingVersion,
      mode,
      bindings: structuredClone(query.data.bindings),
    })
    setEnvironments(row.environments.length ? row.environments : ['TEST'])
    setReason('')
    publish.reset()
  }
  function openPreview(row: DefinitionView) {
    if (!context.data) return
    setPreviewing(row)
    setSample({
      ...freshForm(context.data.actorId),
      name: '流程预览样例',
      proposedAppCode: 'preview-app',
      description: '仅用于预览审批路径',
      businessPurpose: '只读预览，不创建申请、不创建应用、不发送通知',
      requestedDomains: ['preview.example.com'],
    })
    setPreview(undefined)
    simulate.reset()
  }
  const active = working?.model.nodes[selected]
  if (permissions.isLoading)
    return (
      <QueryState loading onRetry={() => void permissions.refetch()}>
        {null}
      </QueryState>
    )
  if (permissions.isError)
    return (
      <QueryState loading={false} error={permissions.error} onRetry={() => void permissions.refetch()}>
        {null}
      </QueryState>
    )
  if (!permissions.can('workflow:definitions:view'))
    return <Alert type="warning" content="暂无流程配置读取权限" />
  return (
    <div className="onboarding-page">
      <div className="page-heading">
        <div>
          <h1>应用接入审批流配置</h1>
          <p className="muted">审批条件、候选来源与会签规则可配置；办理和原申请人收件为固定终点。</p>
        </div>
        <Space>
          <Button disabled={busy} onClick={() => void query.refetch()}>
            刷新
          </Button>
          {permissions.can('workflow:definitions:write') && (
            <Button type="primary" disabled={busy} onClick={() => open()}>
              新建流程草稿
            </Button>
          )}
        </Space>
      </div>
      <Alert content="同一环境只有一个新申请流程绑定。发布会替换所选环境的绑定，已提交申请继续使用原版本。至少一个必经审批节点，所有新接入路径均须安全审批。" />
      <Card className="section-card">
        <Space wrap>
          <Input.Search
            value={search}
            onChange={setSearch}
            placeholder="流程名称或编码"
            onSearch={() => {
              setKeyword(search)
              setPage(1)
            }}
          />
          <Select
            allowClear
            value={status}
            placeholder="版本状态"
            options={['DRAFT', 'PUBLISHED', 'DISABLED']}
            onChange={(value) => {
              setStatus(value)
              setPage(1)
            }}
          />
          <Button
            onClick={() => {
              setKeyword(search)
              setPage(1)
            }}
          >
            查询
          </Button>
          <Button
            onClick={() => {
              setSearch('')
              setKeyword('')
              setStatus(undefined)
              setPage(1)
            }}
          >
            重置
          </Button>
        </Space>
        <QueryState loading={query.isLoading} error={query.error} onRetry={() => void query.refetch()}>
          <Table<DefinitionView>
            rowKey="id"
            data={query.data?.items ?? []}
            loading={query.isFetching}
            pagination={{ current: page, pageSize: 20, total: query.data?.total ?? 0, onChange: setPage }}
            columns={[
              { title: '流程', dataIndex: 'name' },
              { title: '编码', dataIndex: 'definitionKey' },
              { title: '版本', render: (_, row) => `v${row.version}` },
              { title: '状态', render: (_, row) => <StateTag state={row.status} /> },
              { title: '当前环境绑定', render: (_, row) => row.environments.join('、') || '无新申请绑定' },
              {
                title: '操作',
                width: 360,
                render: (_, row) => (
                  <Space wrap>
                    <Button size="mini" disabled={busy} onClick={() => setViewing(row)}>
                      查看模型
                    </Button>
                    <Button
                      size="mini"
                      disabled={busy}
                      onClick={() => {
                        setSearch(row.definitionKey)
                        setKeyword(row.definitionKey)
                        setStatus(undefined)
                        setPage(1)
                      }}
                    >
                      历史版本
                    </Button>
                    {permissions.can('workflow:definitions:write') && (
                      <>
                        <Button size="mini" disabled={busy} onClick={() => open(row, row.status !== 'DRAFT')}>
                          {row.status === 'DRAFT' ? '编辑草稿' : '复制为新版本'}
                        </Button>
                        <Button
                          size="mini"
                          disabled={busy || !context.isSuccess}
                          onClick={() => openPreview(row)}
                        >
                          预览路径
                        </Button>
                        <Button size="mini" disabled={busy} onClick={() => validate.mutate(row)}>
                          校验
                        </Button>
                      </>
                    )}
                    {permissions.can('workflow:definitions:publish') && row.status === 'DRAFT' && (
                      <Button
                        size="mini"
                        type="primary"
                        disabled={busy || query.isFetching}
                        onClick={() => openPublication(row, 'publish')}
                      >
                        发布
                      </Button>
                    )}
                    {permissions.can('workflow:definitions:publish') && row.status === 'PUBLISHED' && (
                      <Button
                        size="mini"
                        status="danger"
                        disabled={busy || query.isFetching}
                        onClick={() => openPublication(row, 'disable')}
                      >
                        停用新申请入口
                      </Button>
                    )}
                  </Space>
                ),
              },
            ]}
          />
        </QueryState>
        {validate.isError && <Alert type="error" content={getApiMessage(validate.error)} />}
      </Card>
      <Drawer
        className="onboarding-drawer"
        visible={working !== null}
        width={1120}
        title={working?.id ? '编辑流程草稿' : '新建流程版本'}
        footer={null}
        maskClosable={!busy}
        closable={!busy}
        onCancel={close}
      >
        <Space wrap className="section-card">
          <Button
            type="primary"
            loading={save.isPending}
            disabled={busy || !gate.ready}
            onClick={() => {
              if (validateClient()) save.mutate()
            }}
          >
            保存草稿
          </Button>
          <Button
            disabled={busy || dirty || !working?.id || !gate.ready}
            onClick={() => working && validate.mutate(working)}
          >
            校验已保存草稿
          </Button>
          <Button
            disabled={busy || !past.length}
            onClick={() => {
              if (!working || !past.length) return
              setFuture((old) => [structuredClone(working), ...old])
              setWorking(past.at(-1)!)
              setPast((old) => old.slice(0, -1))
              setSelected(0)
            }}
          >
            撤销
          </Button>
          <Button
            disabled={busy || !future.length}
            onClick={() => {
              if (!working || !future.length) return
              setPast((old) => [...old, structuredClone(working)])
              setWorking(future[0])
              setFuture((old) => old.slice(1))
              setSelected(0)
            }}
          >
            重做
          </Button>
          <Button disabled={busy} onClick={close}>
            关闭
          </Button>
        </Space>
        {working && (
          <>
            <div className="onboarding-grid">
              <Field label="流程编码（版本共享）">
                <Input
                  maxLength={64}
                  value={working.definitionKey}
                  disabled={busy || Boolean(working.id || working.sourceId)}
                  onChange={(definitionKey) => edit({ ...working, definitionKey })}
                />
              </Field>
              <Field label="流程名称">
                <Input
                  maxLength={128}
                  value={working.name}
                  disabled={busy}
                  onChange={(name) => edit({ ...working, name })}
                />
              </Field>
            </div>
            <div className="onboarding-designer">
              <Card title="审批顺序与条件">
                <Tree
                  blockNode
                  selectedKeys={active ? ['node:' + selected] : []}
                  treeData={working.model.nodes.map((node, index) => ({
                    key: `node:${index}`,
                    title: `${index + 1}. ${node.name || '未命名'} · ${node.mode === 'ALL' ? '会签' : '或签'}`,
                  }))}
                  onSelect={(keys) => {
                    if (!keys.length) return
                    const index = Number(String(keys[0] ?? '').replace('node:', ''))
                    if (Number.isInteger(index) && index >= 0 && index < working.model.nodes.length)
                      setSelected(index)
                  }}
                />
                <Space className="section-card" wrap>
                  <Button
                    disabled={busy || working.model.nodes.length >= 20}
                    onClick={() => {
                      edit({
                        ...working,
                        model: { ...working.model, nodes: [...working.model.nodes, freshNode()] },
                      })
                      setSelected(working.model.nodes.length)
                    }}
                  >
                    添加审批节点
                  </Button>
                  <Button
                    disabled={busy || !active || working.model.nodes.length <= 1}
                    status="danger"
                    onClick={() =>
                      Modal.confirm({
                        title: '删除此审批节点？',
                        onOk: () => {
                          edit({
                            ...working,
                            model: {
                              ...working.model,
                              nodes: working.model.nodes.filter((_, i) => i !== selected),
                            },
                          })
                          setSelected(Math.max(0, selected - 1))
                        },
                      })
                    }
                  >
                    删除节点
                  </Button>
                  <Button
                    disabled={busy || selected === 0}
                    onClick={() => {
                      const nodes = [...working.model.nodes]
                      ;[nodes[selected - 1], nodes[selected]] = [nodes[selected], nodes[selected - 1]]
                      edit({ ...working, model: { ...working.model, nodes } })
                      setSelected(selected - 1)
                    }}
                  >
                    上移
                  </Button>
                  <Button
                    disabled={busy || selected >= working.model.nodes.length - 1}
                    onClick={() => {
                      const nodes = [...working.model.nodes]
                      ;[nodes[selected + 1], nodes[selected]] = [nodes[selected], nodes[selected + 1]]
                      edit({ ...working, model: { ...working.model, nodes } })
                      setSelected(selected + 1)
                    }}
                  >
                    下移
                  </Button>
                </Space>
                <Alert content="审批通过后 → 平台办理 → 原申请人领取并确认（这两个步骤不可删除）。" />
              </Card>
              <Card title={active?.name || '节点属性'}>
                {active && (
                  <Space direction="vertical" style={{ width: '100%' }}>
                    <Field label="节点编码">
                      <Input
                        disabled={busy}
                        maxLength={64}
                        value={active.key}
                        onChange={(value) => updateNode({ key: value })}
                      />
                    </Field>
                    <Field label="节点名称">
                      <Input
                        disabled={busy}
                        maxLength={128}
                        value={active.name}
                        onChange={(value) => updateNode({ name: value })}
                      />
                    </Field>
                    <Field label="决策规则">
                      <Select
                        disabled={busy}
                        value={active.mode}
                        options={[
                          { label: '会签：全部通过，一人拒绝即驳回', value: 'ALL' },
                          { label: '或签：一人通过，全部拒绝才驳回', value: 'ANY' },
                        ]}
                        onChange={(value) => updateNode({ mode: value })}
                      />
                    </Field>
                    <Field label="提醒时限（1–720小时，不自动通过）">
                      <InputNumber
                        disabled={busy}
                        min={1}
                        max={720}
                        precision={0}
                        value={active.dueHours}
                        onChange={(value) => updateNode({ dueHours: value ?? 24 })}
                      />
                    </Field>
                    <Field label="安全审批节点">
                      <Switch
                        checked={active.securityReview}
                        disabled={busy}
                        onChange={(value) => updateNode({ securityReview: value })}
                      />
                    </Field>
                    <SelectorEditor
                      value={active.reviewers}
                      disabled={busy}
                      prefix={'node:' + selected}
                      onReady={gate.update}
                      onChange={(reviewers) => updateNode({ reviewers })}
                    />
                    <Field label="条件：环境（空表示不限）">
                      <Select
                        mode="multiple"
                        allowClear
                        disabled={busy}
                        value={active.environments}
                        options={['DEV', 'TEST', 'PROD']}
                        onChange={(environments) => updateNode({ environments })}
                      />
                    </Field>
                    <Field label="条件：数据级别（空表示不限）">
                      <Select
                        mode="multiple"
                        allowClear
                        disabled={busy}
                        value={active.classifications}
                        options={['INTERNAL', 'CONFIDENTIAL']}
                        onChange={(classifications) => updateNode({ classifications })}
                      />
                    </Field>
                    <Field label="条件：应用类型（空表示不限）">
                      <Select
                        mode="multiple"
                        allowClear
                        disabled={busy}
                        value={active.appTypes}
                        options={['WEB', 'ADMIN', 'AI', 'OTHER']}
                        onChange={(appTypes) => updateNode({ appTypes })}
                      />
                    </Field>
                    <Alert content="三个条件按AND匹配；每类中的多个值按OR匹配。不匹配时跳过该节点；至少保留一个无条件必经节点。" />
                  </Space>
                )}
              </Card>
            </div>
            <Card className="section-card" title="平台办理人来源">
              <SelectorEditor
                value={working.model.fulfillers}
                disabled={busy}
                prefix="fulfillers"
                onReady={gate.update}
                onChange={(fulfillers) => edit({ ...working, model: { ...working.model, fulfillers } })}
              />
            </Card>
            <Card className="section-card" title="流程异常运维人员">
              <SelectorEditor
                value={working.model.operators}
                disabled={busy}
                prefix="operators"
                onReady={gate.update}
                onChange={(operators) => edit({ ...working, model: { ...working.model, operators } })}
              />
              <Alert content="运维人员需有申请管理和本人通知权限。异常恢复不能直接批准；未通过申请将重新经过完整审批。" />
            </Card>
          </>
        )}
        {(error || save.isError || validate.isError) && (
          <Alert
            className="section-card"
            type="error"
            content={error || getApiMessage(save.error ?? validate.error)}
          />
        )}
        {!gate.ready && <Alert content="人员/角色字典未准备好，暂不能保存。" />}
      </Drawer>
      <Drawer
        className="onboarding-drawer"
        visible={viewing !== null}
        width={720}
        title={`${viewing?.name ?? ''} · v${viewing?.version ?? ''}`}
        footer={null}
        onCancel={() => setViewing(null)}
      >
        {viewing && <FlowTree model={viewing.model} index={-1} />}
      </Drawer>
      <ReasonModal
        visible={publication !== null}
        title={
          publication?.mode === 'publish' ? '发布并替换所选环境的新申请流程？' : '停用此版本的新申请入口？'
        }
        busy={publish.isPending}
        error={publish.error}
        comment={reason}
        onChange={setReason}
        required
        blocked={
          publication?.mode === 'publish' && (!environments.length || !before.isSuccess || before.isFetching)
        }
        onCancel={() => {
          if (!publish.isPending) setPublication(null)
        }}
        onConfirm={() => publish.mutate()}
      >
        <Alert content="仅影响后续新提交的申请，在途审批不变；停用后无绑定环境的申请将明确拒绝提交。" />
        {publication?.mode === 'publish' && (
          <>
            <p>
              现有绑定：
              {publication?.bindings
                .map((binding) => `${binding.environment} → ${binding.name} v${binding.version}`)
                .join('；') || '无'}
            </p>
            <Field label="绑定环境">
              <Select
                mode="multiple"
                value={environments}
                options={['DEV', 'TEST', 'PROD']}
                disabled={publish.isPending}
                onChange={setEnvironments}
              />
            </Field>
            <QueryState loading={before.isLoading} error={before.error} onRetry={() => void before.refetch()}>
              {publication && before.data && (
                <Space direction="vertical">
                  {environments.map((environment) => {
                    const binding = publication.bindings.find((item) => item.environment === environment),
                      previous = before.data?.find((item) => item.id === binding?.definitionId)
                    return (
                      <Alert
                        key={environment}
                        content={
                          environment +
                          '：' +
                          (previous
                            ? `${previous.name} v${previous.version} → ${publication.row.name} v${publication.row.version}；${flowChanges(previous.model, publication.row.model)}`
                            : '首次绑定；' + publication.row.model.nodes.length + '个审批节点')
                        }
                      />
                    )
                  })}
                  <p>模型摘要：{publication?.row.checksum}</p>
                </Space>
              )}
            </QueryState>
          </>
        )}
      </ReasonModal>
      <Modal
        className="onboarding-modal"
        visible={previewing !== null}
        style={{ width: 860 }}
        title="只读流程预览（不会创建申请或通知）"
        okText="计算审批路径"
        confirmLoading={simulate.isPending}
        okButtonProps={{ disabled: simulate.isPending || !gate.ready }}
        maskClosable={!simulate.isPending}
        closable={!simulate.isPending}
        cancelButtonProps={{ disabled: simulate.isPending }}
        onCancel={() => {
          if (!simulate.isPending) {
            setPreviewing(null)
            setPreview(undefined)
          }
        }}
        onOk={() => {
          if (sample) {
            const fields = validateForm(sample, true)
            if (Object.keys(fields).length) {
              setError(Object.values(fields).join('；'))
              return
            }
            setError('')
            simulate.mutate()
          }
        }}
      >
        {sample && context.data && (
          <ApplicationFields
            value={sample}
            actor={context.data.actorId}
            onChange={(value) => {
              setSample(value)
              setPreview(undefined)
            }}
            disabled={simulate.isPending}
            onReady={gate.update}
          />
        )}
        {error && <Alert type="error" content={error} />}
        {simulate.isError && <Alert type="error" content={getApiMessage(simulate.error)} />}
        {preview && <ApprovalPreview value={preview} />}
      </Modal>
    </div>
  )
}
function SelectorEditor({
  value,
  onChange,
  prefix,
  onReady,
  disabled,
}: {
  value: Selector
  onChange: (value: Selector) => void
  prefix: string
  onReady: (key: string, ready: boolean) => void
  disabled: boolean
}) {
  return (
    <Space direction="vertical" style={{ width: '100%' }}>
      <Field label="候选来源">
        <Select
          disabled={disabled}
          value={value.type}
          onChange={(type) => onChange({ ...value, type, employeeIds: [], roleIds: [] })}
          options={[
            { label: '固定员工', value: 'EMPLOYEE' },
            { label: '有效角色成员（含组织继承）', value: 'ROLE' },
            { label: '申请人主部门负责人', value: 'DEPARTMENT_LEADER' },
          ]}
        />
      </Field>
      {value.type === 'EMPLOYEE' && (
        <Field label="固定候选员工">
          <PeopleSelect
            disabled={disabled}
            value={value.employeeIds}
            gateKey={prefix + ':employees'}
            onReady={onReady}
            onChange={(employeeIds) => onChange({ ...value, employeeIds })}
          />
        </Field>
      )}
      {value.type === 'ROLE' && (
        <Field label="候选角色">
          <PeopleSelect
            kind="ROLE"
            disabled={disabled}
            value={value.roleIds}
            gateKey={prefix + ':roles'}
            onReady={onReady}
            onChange={(roleIds) => onChange({ ...value, roleIds })}
          />
        </Field>
      )}
      <Field label="无人可用时的兜底员工（排除申请人）">
        <PeopleSelect
          disabled={disabled}
          value={value.fallbackEmployeeIds}
          gateKey={prefix + ':fallback'}
          onReady={onReady}
          onChange={(fallbackEmployeeIds) => onChange({ ...value, fallbackEmployeeIds })}
        />
      </Field>
    </Space>
  )
}

function flowChanges(before: FlowModel, after: FlowModel): string {
  const oldKeys = new Set(before.nodes.map((node) => node.key)),
    newKeys = new Set(after.nodes.map((node) => node.key))
  const added = after.nodes.filter((node) => !oldKeys.has(node.key)).map((node) => node.name),
    removed = before.nodes.filter((node) => !newKeys.has(node.key)).map((node) => node.name),
    changed = after.nodes
      .filter(
        (node) =>
          oldKeys.has(node.key) &&
          JSON.stringify(before.nodes.find((old) => old.key === node.key)) !== JSON.stringify(node),
      )
      .map((node) => node.name)
  return [
    `新增：${added.join('、') || '无'}`,
    `移除：${removed.join('、') || '无'}`,
    `属性变化：${changed.join('、') || '无'}`,
    before.nodes.map((node) => node.key).join() !== after.nodes.map((node) => node.key).join()
      ? '节点顺序变化'
      : '顺序不变',
    JSON.stringify(before.fulfillers) !== JSON.stringify(after.fulfillers)
      ? '办理人来源变化'
      : '办理人来源不变',
    JSON.stringify(before.operators) !== JSON.stringify(after.operators)
      ? '运维人来源变化'
      : '运维人来源不变',
  ].join('；')
}
