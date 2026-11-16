import {
  Alert,
  Button,
  Card,
  Empty,
  Input,
  Message,
  Modal,
  Select,
  Space,
  Spin,
  Tag,
  Tree,
} from '@arco-design/web-react'
import { useQuery } from '@tanstack/react-query'
import { useCallback, useEffect, useRef, useState } from 'react'
import type { ReactNode } from 'react'
import { api, getApiMessage } from '../../lib/api'
import type { ApplicationForm, FlowModel, Option, TaskView, ValidationView } from './types'
import { stateNames } from './types'

/** 接口统一信封解包，不把失败当空列表。 */
export async function readApi<T>(path: string, params?: Record<string, unknown>): Promise<T> {
  return (await api.get<{ data: T }>(path, { params })).data.data
}
export function StateTag({ state }: { state: string }) {
  return (
    <Tag
      color={
        ['EXCEPTION', 'REJECTED', 'EXPIRED', 'DEAD_LETTER'].includes(state)
          ? 'red'
          : ['COMPLETED', 'APPROVED', 'PUBLISHED', 'ACKNOWLEDGED', 'SENT'].includes(state)
            ? 'green'
            : 'arcoblue'
      }
    >
      {stateNames[state] ?? state}
    </Tag>
  )
}
export function QueryState({
  loading,
  error,
  onRetry,
  empty,
  children,
}: {
  loading: boolean
  error?: unknown
  onRetry: () => void
  empty?: boolean
  children: ReactNode
}) {
  return loading ? (
    <Spin tip="正在读取…" />
  ) : error ? (
    <Alert type="error" content={getApiMessage(error)} action={<Button onClick={onRetry}>重试</Button>} />
  ) : empty ? (
    <Empty description="暂无符合条件的记录" />
  ) : (
    <>{children}</>
  )
}
export function Field({ label, error, children }: { label: string; error?: string; children: ReactNode }) {
  return (
    <label className="onboarding-field">
      <span>{label}</span>
      {children}
      {error && (
        <span className="onboarding-field-error" role="alert">
          {error}
        </span>
      )}
    </label>
  )
}
import { useDraftGuard } from '../../components/DraftGuard'
export { useDraftGuard } from '../../components/DraftGuard'
/** 相同动作和正文重试复用同一幂等键，不写浏览器存储。 */
export function useActionKeys() {
  const keys = useRef(new Map<string, string>())
  return (action: string, body: unknown) => {
    const signature = action + JSON.stringify(body)
    let key = keys.current.get(signature)
    if (!key) {
      key = crypto.randomUUID()
      keys.current.set(signature, key)
    }
    return key
  }
}
export function useDictionaryGate() {
  const [status, setStatus] = useState<Record<string, boolean>>({})
  const update = useCallback(
    (key: string, ready: boolean) =>
      setStatus((old) => (old[key] === ready ? old : { ...old, [key]: ready })),
    [],
  )
  return { ready: Object.values(status).every(Boolean), update }
}
/** 每次50项远程搜索，当前选择回填名称；失败阻止依赖该字典的提交。 */
export function PeopleSelect({
  value,
  onChange,
  kind = 'EMPLOYEE',
  disabled = false,
  gateKey,
  onReady,
}: {
  value: number[]
  onChange: (value: number[]) => void
  kind?: 'EMPLOYEE' | 'ROLE'
  disabled?: boolean
  gateKey: string
  onReady: (key: string, ready: boolean) => void
}) {
  const [search, setSearch] = useState(''),
    [keyword, setKeyword] = useState('')
  const selected = [...value].sort((a, b) => a - b).join(',')
  useEffect(() => {
    setSearch('')
    setKeyword('')
  }, [gateKey])
  useEffect(() => {
    const timer = setTimeout(() => setKeyword(search), 250)
    return () => clearTimeout(timer)
  }, [search])
  const query = useQuery({
    queryKey: ['onboarding', 'options', kind, keyword, selected],
    queryFn: () =>
      readApi<Option[]>('/onboarding/options', { kind, keyword, selected: selected || undefined }),
    placeholderData: (old) => old,
  })
  useEffect(() => {
    onReady(gateKey, query.isSuccess && !query.isFetching)
  }, [gateKey, onReady, query.isSuccess, query.isFetching])
  useEffect(() => () => onReady(gateKey, true), [gateKey, onReady])
  return (
    <>
      <Select
        aria-label={kind === 'ROLE' ? '选择角色' : '选择员工'}
        mode="multiple"
        showSearch
        allowClear
        filterOption={false}
        loading={query.isFetching}
        disabled={disabled}
        value={value}
        onChange={onChange}
        onSearch={setSearch}
        options={query.data?.map((row) => ({ label: row.name, value: row.id }))}
        placeholder={kind === 'ROLE' ? '搜索角色名称' : '搜索员工姓名'}
      />
      {query.isError && (
        <Alert
          type="error"
          content={getApiMessage(query.error)}
          action={
            <Button size="mini" onClick={() => void query.refetch()}>
              重试字典
            </Button>
          }
        />
      )}
    </>
  )
}
export function ApprovalPreview({ value }: { value: ValidationView }) {
  return (
    <Card title={`${value.definitionName} · v${value.definitionVersion}`}>
      <Space direction="vertical" style={{ width: '100%' }}>
        {value.nodes.map((node) => (
          <div key={node.key}>
            <Tag>{node.mode === 'ALL' ? '会签' : '或签'}</Tag>
            <strong>{node.name}</strong> ·{' '}
            {node.matched ? node.candidates.map((row) => row.name).join('、') : '条件不匹配，跳过'}
          </div>
        ))}
        <Alert
          content={`审批完成后由 ${value.fulfillers.map((row) => row.name).join('、')} 办理；接入信息仅交付原申请人。异常运维：${value.operators.map((row) => row.name).join('、')}。`}
        />
      </Space>
    </Card>
  )
}
export function FlowTree({
  model,
  index = -1,
  form,
  tasks = [],
}: {
  model: FlowModel
  index?: number
  form?: ApplicationForm
  tasks?: TaskView[]
}) {
  function state(node: FlowModel['nodes'][number], position: number) {
    if (
      form &&
      ((node.environments.length && !node.environments.includes(form.environment)) ||
        (node.classifications.length && !node.classifications.includes(form.dataClassification)) ||
        (node.appTypes.length && !node.appTypes.includes(form.type)))
    )
      return '条件跳过 · '
    const rows = tasks.filter((task) => task.nodeIndex === position)
    if (rows.some((row) => row.state === 'RETURNED')) return '已退回 · '
    if (
      rows.some((row) => row.state === 'APPROVED') &&
      (node.mode === 'ANY' || rows.every((row) => row.state === 'APPROVED'))
    )
      return '已通过 · '
    if (
      rows.some((row) => row.state === 'REJECTED') &&
      (node.mode === 'ALL' || rows.every((row) => ['REJECTED', 'CANCELLED'].includes(row.state)))
    )
      return '已驳回 · '
    if (rows.some((row) => ['PENDING', 'CLAIMED'].includes(row.state))) return '待处理 · '
    if (rows.length && rows.every((row) => row.state === 'CANCELLED')) return '已失效 · '
    return position === index ? '当前节点 · ' : ''
  }
  return (
    <Tree
      blockNode
      defaultExpandedKeys={['start', ...model.nodes.map((node) => node.key)]}
      treeData={[
        {
          key: 'start',
          title: '申请提交',
          children: model.nodes.map((node, position) => ({
            key: node.key,
            title: `${state(node, position)}${node.name} · ${node.mode === 'ALL' ? '全部通过' : '任一通过'}${node.securityReview ? ' · 安全审批' : ''}`,
            children: [
              {
                key: node.key + ':condition',
                title: `条件：${[...node.environments, ...node.classifications, ...node.appTypes].join(' / ') || '必经'}；${node.dueHours}小时提醒`,
              },
            ],
          })),
        },
        { key: 'fulfill', title: '平台办理（固定）' },
        { key: 'delivery', title: '原申请人领取并确认（固定）' },
      ]}
    />
  )
}
export function validateForm(form: ApplicationForm, complete: boolean): Record<string, string> {
  const errors: Record<string, string> = {}
  if (!/^[a-z][a-z0-9-]{1,63}$/.test(form.proposedAppCode) || form.proposedAppCode === 'platform')
    errors.proposedAppCode = '2–64位小写字母、数字或连字符，不能为platform'
  if (complete && !form.name.trim()) errors.name = '请填写应用名称'
  if (complete && !form.description.trim()) errors.description = '请填写应用描述'
  if (complete && !form.businessPurpose.trim()) errors.businessPurpose = '请填写业务用途'
  if (complete && !form.requestedDomains.length) errors.requestedDomains = '至少填写一个部署域名'
  if (
    form.requestedDomains.some(
      (value) => !/^([a-z0-9])([a-z0-9.-]*[a-z0-9])?$/.test(value) || value.includes('..'),
    )
  )
    errors.requestedDomains = '填写小写精确域名，无协议、路径或通配符'
  if (form.requestedAdminEmployeeIds.length > 100) errors.requestedAdminEmployeeIds = '应用管理员最多100人'
  if (
    form.requestedDomains.length > 20 ||
    new Set(form.requestedDomains).size !== form.requestedDomains.length
  )
    errors.requestedDomains = '最多20个域名且不能重复'
  if (!form.requestedAdminEmployeeIds.length) errors.requestedAdminEmployeeIds = '至少保留申请人'
  if (form.attachmentKeys.length > 10) errors.attachmentKeys = '最多10个附件'
  return errors
}
export async function copyText(value: string) {
  try {
    await navigator.clipboard.writeText(value)
    Message.success('已复制')
  } catch {
    Message.error('复制失败，请手动选择并复制')
  }
}
export function ReasonModal({
  visible,
  title,
  busy,
  error,
  comment,
  onChange,
  onCancel,
  onConfirm,
  required = true,
  blocked = false,
  children,
}: {
  visible: boolean
  title: string
  busy: boolean
  error?: unknown
  comment: string
  onChange: (value: string) => void
  onCancel: () => void
  onConfirm: () => void
  required?: boolean
  blocked?: boolean
  children?: ReactNode
}) {
  useDraftGuard(visible && Boolean(comment.trim()), visible && busy)
  return (
    <Modal
      className="onboarding-modal"
      visible={visible}
      title={title}
      confirmLoading={busy}
      maskClosable={!busy}
      closable={!busy}
      cancelButtonProps={{ disabled: busy }}
      okButtonProps={{ disabled: busy || blocked || (required && !comment.trim()) }}
      onCancel={() => {
        if (busy) return
        if (comment.trim()) Modal.confirm({ title: '放弃尚未提交的意见或原因？', onOk: onCancel })
        else onCancel()
      }}
      onOk={onConfirm}
    >
      {children}
      <Input.TextArea
        aria-label="操作意见"
        maxLength={1000}
        showWordLimit
        value={comment}
        disabled={busy}
        onChange={onChange}
        placeholder={required ? '请填写原因或意见（必填）' : '审批意见（选填）'}
        autoSize={{ minRows: 3, maxRows: 6 }}
      />
      {Boolean(error) && <Alert type="error" content={getApiMessage(error)} />}
    </Modal>
  )
}
