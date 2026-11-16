import { Alert, Button, Card, Input, Select, Space, Table, Tag } from '@arco-design/web-react'
import { useQuery } from '@tanstack/react-query'
import { useEffect, useState } from 'react'
import { getApiMessage } from '../../lib/api'
import { Field, PeopleSelect, readApi } from './shared'
import type { ApplicationForm, PageView } from './types'
interface FileOption {
  objectKey: string
  fileName: string
  fileSize: number
}
/** 本人已确认平台附件，通过明确添加/移除操作跨页保留选择。 */
function AttachmentPicker({
  value,
  onChange,
  disabled,
  onReady,
}: {
  value: string[]
  onChange: (value: string[]) => void
  disabled: boolean
  onReady: (key: string, ready: boolean) => void
}) {
  const [page, setPage] = useState(1),
    [search, setSearch] = useState(''),
    [keyword, setKeyword] = useState(''),
    [labels, setLabels] = useState<Record<string, string>>({})
  const query = useQuery({
    queryKey: ['onboarding', 'files', page, keyword],
    queryFn: () =>
      readApi<PageView<FileOption>>('/files', {
        page,
        page_size: 20,
        keyword,
        status: 'CONFIRMED',
        applicationCode: 'platform',
      }),
  })
  useEffect(() => {
    onReady('attachments', query.isSuccess && !query.isFetching)
  }, [onReady, query.isSuccess, query.isFetching])
  useEffect(() => () => onReady('attachments', true), [onReady])
  useEffect(() => {
    if (query.data)
      setLabels((old) => ({
        ...old,
        ...Object.fromEntries(query.data.items.map((row) => [row.objectKey, row.fileName])),
      }))
  }, [query.data])
  return (
    <Card title="选择本人已确认的平台附件" className="section-card">
      <Alert content="附件最多10个、总量最多100MB。审批参与者只能通过本申请授权读取；不会修改文件的私有归属。" />
      <Space wrap className="section-card">
        <Input.Search
          value={search}
          onChange={setSearch}
          onSearch={() => {
            setKeyword(search)
            setPage(1)
          }}
          placeholder="输入文件名后搜索"
          disabled={disabled}
        />
        <Button
          disabled={disabled}
          onClick={() => {
            setKeyword(search)
            setPage(1)
          }}
        >
          搜索
        </Button>
      </Space>
      <Space wrap>
        {value.map((key) => (
          <Tag key={key} closable={!disabled} onClose={() => onChange(value.filter((item) => item !== key))}>
            {labels[key] ?? '已选附件 ' + key.split('/').at(-1)}
          </Tag>
        ))}
      </Space>
      {query.isError ? (
        <Alert
          type="error"
          content={getApiMessage(query.error)}
          action={<Button onClick={() => void query.refetch()}>重试附件</Button>}
        />
      ) : (
        <Table<FileOption>
          rowKey="objectKey"
          loading={query.isFetching}
          data={query.data?.items ?? []}
          pagination={{ current: page, pageSize: 20, total: query.data?.total ?? 0, onChange: setPage }}
          columns={[
            { title: '文件', dataIndex: 'fileName' },
            { title: '大小', render: (_, row) => `${(row.fileSize / 1024).toFixed(1)} KB` },
            {
              title: '操作',
              render: (_, row) => (
                <Button
                  size="mini"
                  disabled={disabled || value.includes(row.objectKey) || value.length >= 10}
                  onClick={() => onChange([...value, row.objectKey])}
                >
                  {value.includes(row.objectKey) ? '已添加' : '添加'}
                </Button>
              ),
            },
          ]}
        />
      )}
    </Card>
  )
}
export function ApplicationFields({
  value,
  onChange,
  actor,
  disabled = false,
  errors = {},
  onReady,
  canFiles = false,
}: {
  value: ApplicationForm
  onChange: (form: ApplicationForm) => void
  actor: number
  disabled?: boolean
  errors?: Record<string, string>
  onReady: (key: string, ready: boolean) => void
  canFiles?: boolean
}) {
  const [filesOpen, setFilesOpen] = useState(false)
  const change = (patch: Partial<ApplicationForm>) => onChange({ ...value, ...patch })
  return (
    <>
      <div className="onboarding-grid">
        <Field label="应用名称（提交必填）" error={errors.name}>
          <Input
            aria-label="应用名称"
            maxLength={128}
            value={value.name}
            disabled={disabled}
            onChange={(name) => change({ name })}
          />
        </Field>
        <Field label="应用编码（保存必填、提交后预留）" error={errors.proposedAppCode}>
          <Input
            aria-label="应用编码"
            maxLength={64}
            value={value.proposedAppCode}
            disabled={disabled}
            onChange={(proposedAppCode) => change({ proposedAppCode })}
            placeholder="如 finance-console"
          />
        </Field>
        <Field label="应用类型">
          <Select
            disabled={disabled}
            value={value.type}
            onChange={(type) => change({ type })}
            options={['WEB', 'ADMIN', 'AI', 'OTHER']}
          />
        </Field>
        <Field label="运行环境">
          <Select
            disabled={disabled}
            value={value.environment}
            onChange={(environment) => change({ environment })}
            options={['DEV', 'TEST', 'PROD']}
          />
        </Field>
        <Field label="接入端">
          <Select
            mode="multiple"
            disabled={disabled}
            value={value.requestedEntries}
            options={[
              { label: 'Web（必选）', value: 'WEB', disabled: true },
              { label: '管理后台', value: 'ADMIN' },
            ]}
            onChange={(entries: string[]) => change({ requestedEntries: [...new Set(['WEB', ...entries])] })}
          />
        </Field>
        <Field label="数据级别">
          <Select
            disabled={disabled}
            value={value.dataClassification}
            onChange={(dataClassification) => change({ dataClassification })}
            options={[
              { label: '内部数据', value: 'INTERNAL' },
              { label: '机密数据', value: 'CONFIDENTIAL' },
            ]}
          />
        </Field>
      </div>
      <Field label="应用描述（提交必填）" error={errors.description}>
        <Input.TextArea
          aria-label="应用描述"
          maxLength={500}
          showWordLimit
          value={value.description}
          disabled={disabled}
          onChange={(description) => change({ description })}
          autoSize={{ minRows: 2, maxRows: 5 }}
        />
      </Field>
      <Field label="业务用途（提交必填）" error={errors.businessPurpose}>
        <Input.TextArea
          aria-label="业务用途"
          maxLength={2000}
          showWordLimit
          value={value.businessPurpose}
          disabled={disabled}
          onChange={(businessPurpose) => change({ businessPurpose })}
          autoSize={{ minRows: 3, maxRows: 8 }}
        />
      </Field>
      <Field label="申请部署域名（精确小写域名，最多20个）" error={errors.requestedDomains}>
        <Select
          mode="multiple"
          allowCreate
          showSearch
          disabled={disabled}
          value={value.requestedDomains}
          options={value.requestedDomains}
          placeholder="输入域名并回车，如 app.example.com"
          onChange={(requestedDomains: string[]) => change({ requestedDomains })}
        />
      </Field>
      <Alert content="所有新应用接入路径必须保留安全审批，不能通过低报风险跳过。后续办理地址只能使用本次批准的域名；不能临时增加端、域名、管理员或改变收件人。" />
      <Field label="申请应用管理员（包含本人，最多100人）" error={errors.requestedAdminEmployeeIds}>
        <PeopleSelect
          value={value.requestedAdminEmployeeIds}
          disabled={disabled}
          gateKey="application-admins"
          onReady={onReady}
          onChange={(ids) => change({ requestedAdminEmployeeIds: [...new Set([actor, ...ids])] })}
        />
      </Field>
      {canFiles ? (
        <Button disabled={disabled} onClick={() => setFilesOpen((old) => !old)}>
          {filesOpen ? '收起附件选择' : '选择/检查本人附件'}（{value.attachmentKeys.length}/10）
        </Button>
      ) : (
        <Alert
          content={
            value.attachmentKeys.length
              ? '已有附件将由服务器检查归属与状态；修改附件需要文件查看权限。'
              : '附件可选；如需添加，请先取得文件查看权限并在文件中心完成上传确认。'
          }
        />
      )}
      {!canFiles && value.attachmentKeys.length > 0 && (
        <Space wrap>
          {value.attachmentKeys.map((key) => (
            <Tag
              key={key}
              closable={!disabled}
              onClose={() => change({ attachmentKeys: value.attachmentKeys.filter((item) => item !== key) })}
            >
              {key.split('/').at(-1)}
            </Tag>
          ))}
        </Space>
      )}
      {filesOpen && canFiles && (
        <AttachmentPicker
          value={value.attachmentKeys}
          disabled={disabled}
          onReady={onReady}
          onChange={(attachmentKeys) => change({ attachmentKeys })}
        />
      )}
    </>
  )
}
