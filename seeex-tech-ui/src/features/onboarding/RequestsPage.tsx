import { Alert, Button, Card, DatePicker, Input, Message, Select, Space, Table } from '@arco-design/web-react'
import { IconPlus, IconRefresh } from '@arco-design/web-react/icon'
import { useQuery } from '@tanstack/react-query'
import { useEffect, useRef, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { usePermissions } from '../../lib/management'
import { freshForm, stateNames } from './types'
import type { ApplicationForm, Context, PageView, RequestItem } from './types'
import { QueryState, readApi, StateTag } from './shared'
import { RequestDetail } from './RequestDetail'
import { RequestEditor } from './RequestEditor'
import type { EditorValue } from './RequestEditor'
/** 三种队列共用真实申请详情；管理权限不替代办理候选关系。 */
export function RequestsPage({ scope = 'MINE' }: { scope?: 'MINE' | 'MANAGE' | 'FULFILL' }) {
  const permissions = usePermissions(),
    [params] = useSearchParams(),
    [page, setPage] = useState(1),
    [search, setSearch] = useState(''),
    [keyword, setKeyword] = useState(''),
    [state, setState] = useState<string>(),
    [range, setRange] = useState<string[]>([]),
    [selected, setSelected] = useState<number>(),
    [editor, setEditor] = useState<EditorValue | null>(null)
  const consumed = useRef('')
  const selfServiceReady = [
    'app-requests:create',
    'app-requests:own:view',
    'app-deliveries:own:view',
    'notifications:own:view',
  ].every(permissions.can)
  const permission =
    scope === 'MANAGE'
      ? 'app-requests:manage'
      : scope === 'FULFILL'
        ? 'app-fulfillments:view'
        : 'app-requests:own:view'
  const canList = permissions.can(permission),
    context = useQuery({
      queryKey: ['onboarding', 'context'],
      queryFn: () => readApi<Context>('/onboarding/context'),
      enabled: permissions.isSuccess,
    })
  const query = useQuery({
    queryKey: ['onboarding', 'requests', scope, page, keyword, state, range],
    queryFn: () =>
      readApi<PageView<RequestItem>>(
        scope === 'FULFILL'
          ? '/application-fulfillments'
          : scope === 'MINE'
            ? '/me/app-requests'
            : '/app-requests',
        {
          scope,
          page,
          pageSize: 20,
          keyword,
          state,
          from: range[0]?.replace(' ', 'T'),
          to: range[1]?.replace(' ', 'T'),
        },
      ),
    enabled: canList,
  })
  useEffect(() => {
    const id = Number(params.get('request'))
    if (Number.isSafeInteger(id) && id > 0) setSelected(id)
  }, [params])
  useEffect(() => {
    if (
      params.get('new') === '1' &&
      context.data &&
      selfServiceReady &&
      consumed.current !== params.toString()
    ) {
      consumed.current = params.toString()
      setEditor((old) => old ?? { form: freshForm(context.data.actorId) })
    }
  }, [params, context.data, permissions.data])
  function newDraft(form?: ApplicationForm) {
    if (!context.data || !selfServiceReady) {
      Message.warning('请先补齐申请创建、本人查看、交付领取和通知权限')
      return
    }
    setSelected(undefined)
    setEditor({
      form: form
        ? {
            ...form,
            proposedAppCode: '',
            requestedAdminEmployeeIds: [
              ...new Set([context.data.actorId, ...form.requestedAdminEmployeeIds]),
            ],
          }
        : freshForm(context.data.actorId),
    })
  }
  const title =
    scope === 'MINE' ? '我的应用接入申请' : scope === 'MANAGE' ? '应用接入申请管理' : '平台接入办理'
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
  return (
    <div className="onboarding-page">
      <div className="page-heading">
        <div>
          <h1>{title}</h1>
          <p className="muted">申请、审批、办理和原申请人收件分别记录，处理进度可追溯。</p>
        </div>
        <Space>
          <Button
            icon={<IconRefresh />}
            disabled={query.isFetching || !canList}
            onClick={() => void query.refetch()}
          >
            刷新
          </Button>
          {scope === 'MINE' && permissions.can('app-requests:create') && (
            <Button
              type="primary"
              icon={<IconPlus />}
              disabled={!context.isSuccess || !selfServiceReady}
              onClick={() => newDraft()}
            >
              新建接入申请
            </Button>
          )}
        </Space>
      </div>
      {permissions.can('app-requests:create') && !selfServiceReady && (
        <Alert
          type="warning"
          content="发起申请需同时具备：申请创建、本人申请查看、本人交付领取和本人通知查看权限，请先联系管理员补齐。"
        />
      )}
      {context.isError && (
        <Alert
          type="error"
          content="当前申请身份读取失败，请重试后编辑。"
          action={<Button onClick={() => void context.refetch()}>重试</Button>}
        />
      )}
      {canList ? (
        <>
          <Card className="section-card">
            <Space wrap>
              <Input.Search
                aria-label="搜索申请"
                allowClear
                placeholder="申请编号、应用名称或编码"
                value={search}
                onChange={setSearch}
                onSearch={() => {
                  setKeyword(search)
                  setPage(1)
                }}
              />
              <SelectState
                state={state}
                onChange={(value) => {
                  setState(value)
                  setPage(1)
                }}
              />
              <DatePicker.RangePicker
                showTime
                format="YYYY-MM-DD HH:mm:ss"
                value={range}
                onChange={(value) => {
                  setRange(value)
                  setPage(1)
                }}
              />
              <Button
                type="primary"
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
                  setState(undefined)
                  setRange([])
                  setPage(1)
                }}
              >
                重置
              </Button>
            </Space>
          </Card>
          <Card className="section-card">
            <QueryState loading={query.isLoading} error={query.error} onRetry={() => void query.refetch()}>
              <Table<RequestItem>
                rowKey="id"
                loading={query.isFetching}
                data={query.data?.items ?? []}
                scroll={{ x: 1000 }}
                pagination={{ current: page, pageSize: 20, total: query.data?.total ?? 0, onChange: setPage }}
                columns={[
                  { title: '申请编号', dataIndex: 'requestNo', width: 260 },
                  { title: '应用名称', dataIndex: 'name' },
                  { title: '应用编码', dataIndex: 'appCode' },
                  { title: '申请人', dataIndex: 'applicantName' },
                  { title: '状态', render: (_, row) => <StateTag state={row.state} /> },
                  { title: '提交时间', render: (_, row) => row.submittedAt || '尚未提交' },
                  {
                    title: '操作',
                    fixed: 'right',
                    width: 110,
                    render: (_, row) => (
                      <Button type="text" onClick={() => setSelected(row.id)}>
                        详情与处理
                      </Button>
                    ),
                  },
                ]}
              />
            </QueryState>
          </Card>
        </>
      ) : (
        <Alert type="warning" content="暂无此列表的读取权限，可从本人待办或通知进入有资格查看的申请。" />
      )}
      {selected !== undefined && !editor && (
        <RequestDetail
          key={selected}
          id={selected}
          onClose={() => setSelected(undefined)}
          onEdit={(value) => {
            setSelected(undefined)
            setEditor(value)
          }}
          onCopy={(form) => newDraft(form)}
        />
      )}
      {editor && context.data && (
        <RequestEditor
          initial={editor}
          actor={context.data.actorId}
          canFiles={permissions.can('files:view')}
          onClose={() => setEditor(null)}
          onSaved={(value) => {
            setEditor(null)
            setSelected(value.item.id)
            Message.info('在详情中预检并确认提交后才会进入审批。')
          }}
        />
      )}
    </div>
  )
}
function SelectState({ state, onChange }: { state?: string; onChange: (value: string | undefined) => void }) {
  return (
    <Select
      allowClear
      aria-label="申请状态"
      placeholder="申请状态"
      style={{ width: 160 }}
      value={state}
      onChange={onChange}
      options={[
        'DRAFT',
        'IN_APPROVAL',
        'RETURNED',
        'REJECTED',
        'WITHDRAWN',
        'PENDING_FULFILLMENT',
        'FULFILLING',
        'PENDING_RECEIPT',
        'COMPLETED',
        'CANCELLED',
        'EXCEPTION',
      ].map((value) => ({ value, label: stateNames[value] }))}
    />
  )
}
