export interface Option {
  id: number
  name: string
}
export interface Selector {
  type: 'EMPLOYEE' | 'ROLE' | 'DEPARTMENT_LEADER'
  employeeIds: number[]
  roleIds: number[]
  fallbackEmployeeIds: number[]
}
export interface ApprovalNode {
  key: string
  name: string
  mode: 'ALL' | 'ANY'
  reviewers: Selector
  environments: string[]
  classifications: string[]
  appTypes: string[]
  dueHours: number
  securityReview: boolean
}
export interface FlowModel {
  nodes: ApprovalNode[]
  fulfillers: Selector
  operators: Selector
}
export interface ApplicationForm {
  name: string
  proposedAppCode: string
  description: string
  businessPurpose: string
  type: string
  requestedEntries: string[]
  environment: string
  requestedDomains: string[]
  requestedAdminEmployeeIds: number[]
  dataClassification: string
  attachmentKeys: string[]
}
export interface FulfillmentForm {
  baseUrl: string
  adminUrl: string
  apiUrl: string
  repositoryUrl: string
  logo: string
  ssoCallbackPath: string
  integrationNotes: string
}
export interface RequestItem {
  id: number
  requestNo: string
  name: string
  appCode: string
  applicantId: number
  applicantName: string
  state: string
  decision?: string
  rowVersion: number
  attemptNo: number
  createdAt: string
  submittedAt?: string
}
export interface TaskView {
  id: number
  requestId: number
  requestNo: string
  applicationName: string
  attemptNo: number
  nodeIndex: number
  nodeName: string
  mode: string
  state: string
  rowVersion: number
  candidateId: number
  candidateName: string
  dueAt: string
  allowedActions: string[]
}
export interface HistoryView {
  id: number
  actorId: number
  actorName: string
  action: string
  comment?: string
  attemptNo: number
  createdAt: string
}
export interface RevisionView {
  revision: number
  definitionId: number
  form: ApplicationForm
  submittedAt: string
}
export interface RequestView {
  item: RequestItem
  form: ApplicationForm
  admins: Option[]
  fulfillment?: FulfillmentForm
  fulfillerId?: number
  fulfillerName?: string
  exceptionMessage?: string
  definitionId?: number
  flow?: FlowModel
  nodeIndex: number
  tasks: TaskView[]
  history: HistoryView[]
  revisions: RevisionView[]
  allowedActions: string[]
}
export interface NodePreview {
  key: string
  name: string
  mode: string
  matched: boolean
  candidates: Option[]
}
export interface ValidationView {
  requestVersion: number
  definitionId: number
  definitionName: string
  definitionVersion: number
  nodes: NodePreview[]
  fulfillers: Option[]
  operators: Option[]
}
export interface DefinitionView {
  id: number
  definitionKey: string
  name: string
  version: number
  status: string
  rowVersion: number
  model: FlowModel
  environments: string[]
  checksum: string
  publishedAt?: string
}
export interface DefinitionPage {
  items: DefinitionView[]
  total: number
  bindingVersion: number
  bindings: { environment: string; definitionId: number; name: string; version: number }[]
}
export interface DeliveryView {
  id: number
  requestId: number
  deliveryVersion: number
  rowVersion: number
  config: FulfillmentForm
  appCode: string
  state: string
  expiresAt: string
  redeemedAt?: string
  acknowledgedAt?: string
  allowedActions: string[]
}
export interface SecretView {
  ciToken: string
  clientSecret: string
}
export interface MessageView {
  id: number
  requestId: number
  title: string
  content: string
  readAt?: string
  rowVersion: number
  createdAt: string
}
export interface NoticeView {
  id: number
  requestId: number
  recipientId: number
  state: string
  attempts: number
  lastError?: string
  rowVersion: number
  nextAttemptAt: string
}
export interface PageView<T> {
  items: T[]
  total: number
}
export interface Context {
  actorId: number
  name: string
  deliveryConfigured: boolean
  notificationsEnabled: boolean
}
export interface ActionBody {
  expectedVersion: number
  comment?: string
  decision?: string
  confirmRotation?: boolean
  definitionId?: number
  deliveryId?: number
}
export const stateNames: Record<string, string> = {
  DRAFT: '草稿',
  IN_APPROVAL: '审批中',
  RETURNED: '退回补充',
  REJECTED: '已驳回',
  WITHDRAWN: '已撤回',
  PENDING_FULFILLMENT: '待办理',
  FULFILLING: '办理中',
  PENDING_RECEIPT: '待收件',
  COMPLETED: '已完成',
  EXCEPTION: '异常待处理',
  PENDING: '待领取',
  CLAIMED: '已领取',
  APPROVED: '已通过',
  CANCELLED: '已取消/失效',
  PUBLISHED: '已发布',
  DISABLED: '已停用',
  AVAILABLE: '可领取',
  EXPIRED: '已过期',
  REDEEMED: '已领取凭证',
  ACKNOWLEDGED: '已确认',
  SENT: '已发送',
  RETRY_WAIT: '等待重试',
  DEAD_LETTER: '死信',
  SKIPPED: '已跳过',
  SENDING: '发送中',
}
export const freshForm = (actor: number): ApplicationForm => ({
  name: '',
  proposedAppCode: '',
  description: '',
  businessPurpose: '',
  type: 'WEB',
  requestedEntries: ['WEB'],
  environment: 'TEST',
  requestedDomains: [],
  requestedAdminEmployeeIds: [actor],
  dataClassification: 'INTERNAL',
  attachmentKeys: [],
})
export const freshConfig = (): FulfillmentForm => ({
  baseUrl: '',
  adminUrl: '',
  apiUrl: '',
  repositoryUrl: '',
  logo: '',
  ssoCallbackPath: '/sso/callback',
  integrationNotes: '',
})
export const freshSelector = (): Selector => ({
  type: 'EMPLOYEE',
  employeeIds: [],
  roleIds: [],
  fallbackEmployeeIds: [],
})
export const freshNode = (): ApprovalNode => ({
  key: 'approval-' + crypto.randomUUID().slice(0, 8),
  name: '接入审批',
  mode: 'ALL',
  reviewers: freshSelector(),
  environments: [],
  classifications: [],
  appTypes: [],
  dueHours: 24,
  securityReview: true,
})

export interface AttemptView {
  attemptNo: number
  form: ApplicationForm
  flow: FlowModel
  tasks: TaskView[]
  history: HistoryView[]
}
