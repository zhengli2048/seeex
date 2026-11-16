import { Alert, Button, Card, Message, Select, Space, Table, Tag } from '@arco-design/web-react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { api, notifyApiError } from '../../lib/api'
import { usePermissions } from '../../lib/management'
import { RequestDetail } from './RequestDetail'
import { QueryState, readApi, ReasonModal, StateTag } from './shared'
import type { Context, MessageView, NoticeView, PageView } from './types'
/** 站内已读与申请人领取独立，不发送或显示凭证。 */
export function InboxPage() {
  const permissions = usePermissions(),
    client = useQueryClient(),
    [page, setPage] = useState(1),
    [state, setState] = useState<string>(),
    [selected, setSelected] = useState<number>()
  const query = useQuery({
    queryKey: ['onboarding', 'inbox', page, state],
    queryFn: () => readApi<PageView<MessageView>>('/me/inbox', { page, pageSize: 20, state }),
    enabled: permissions.can('notifications:own:view'),
  })
  const mark = useMutation({
    mutationFn: (row: MessageView) =>
      api.post(`/me/inbox/${row.id}/read`, { expectedVersion: row.rowVersion }),
    onSuccess: () => {
      void client.invalidateQueries({ queryKey: ['onboarding', 'inbox'] })
    },
    onError: notifyApiError,
  })
  return (
    <div className="onboarding-page">
      <div className="page-heading">
        <div>
          <h1>通知中心</h1>
          <p className="muted">消息只提示处理进度。已读不等于审批、领取或确认收件。</p>
        </div>
      </div>
      {permissions.isError ? (
        <QueryState loading={false} error={permissions.error} onRetry={() => void permissions.refetch()}>
          {null}
        </QueryState>
      ) : permissions.isLoading ? (
        <QueryState loading onRetry={() => void permissions.refetch()}>
          {null}
        </QueryState>
      ) : !permissions.can('notifications:own:view') ? (
        <Alert type="warning" content="暂无通知读取权限" />
      ) : (
        <Card>
          <Space className="section-card">
            <Select
              allowClear
              value={state}
              placeholder="全部/未读"
              options={[{ label: '未读', value: 'UNREAD' }]}
              onChange={(value) => {
                setState(value)
                setPage(1)
              }}
            />
            <Button onClick={() => void query.refetch()}>刷新</Button>
          </Space>
          <QueryState loading={query.isLoading} error={query.error} onRetry={() => void query.refetch()}>
            <Table<MessageView>
              rowKey="id"
              data={query.data?.items ?? []}
              loading={query.isFetching}
              pagination={{ current: page, pageSize: 20, total: query.data?.total ?? 0, onChange: setPage }}
              columns={[
                { title: '标题', dataIndex: 'title' },
                { title: '内容', dataIndex: 'content' },
                {
                  title: '状态',
                  render: (_, row) => (
                    <Tag color={row.readAt ? 'gray' : 'arcoblue'}>{row.readAt ? '已读' : '未读'}</Tag>
                  ),
                },
                { title: '时间', dataIndex: 'createdAt' },
                {
                  title: '操作',
                  render: (_, row) => (
                    <Space>
                      <Button onClick={() => setSelected(row.requestId)}>查看申请</Button>
                      {!row.readAt && (
                        <Button
                          disabled={mark.isPending || query.isFetching}
                          onClick={() => mark.mutate(row)}
                        >
                          标记已读
                        </Button>
                      )}
                    </Space>
                  ),
                },
              ]}
            />
          </QueryState>
        </Card>
      )}
      {selected !== undefined && <RequestDetail id={selected} onClose={() => setSelected(undefined)} />}
    </div>
  )
}
/** 外部通知异常与站内交付分开；重试不会触发凭证轮换。 */
export function NotificationJobsPage() {
  const permissions = usePermissions(),
    client = useQueryClient(),
    [page, setPage] = useState(1),
    [state, setState] = useState<string>(),
    [target, setTarget] = useState<NoticeView | null>(null),
    [comment, setComment] = useState('')
  const context = useQuery({
    queryKey: ['onboarding', 'context'],
    queryFn: () => readApi<Context>('/onboarding/context'),
    enabled: permissions.can('notifications:operate'),
  })
  const query = useQuery({
    queryKey: ['onboarding', 'notice-jobs', page, state],
    queryFn: () => readApi<PageView<NoticeView>>('/notification-jobs', { page, pageSize: 20, state }),
    enabled: permissions.can('notifications:operate'),
  })
  const retry = useMutation({
    mutationFn: () =>
      api.post(`/notification-jobs/${target!.id}/retry`, { expectedVersion: target!.rowVersion, comment }),
    onSuccess: () => {
      setTarget(null)
      setComment('')
      Message.success('原通知已重新排队')
      void client.invalidateQueries({ queryKey: ['onboarding', 'notice-jobs'] })
    },
    onError: notifyApiError,
  })
  return (
    <div className="onboarding-page">
      <div className="page-heading">
        <div>
          <h1>通知异常与发送记录</h1>
          <p className="muted">外部通知默认关闭；失败不影响已完成注册和站内交付。</p>
        </div>
      </div>
      {permissions.isError ? (
        <QueryState loading={false} error={permissions.error} onRetry={() => void permissions.refetch()}>
          {null}
        </QueryState>
      ) : permissions.isLoading ? (
        <QueryState loading onRetry={() => void permissions.refetch()}>
          {null}
        </QueryState>
      ) : !permissions.can('notifications:operate') ? (
        <Alert type="warning" content="暂无通知运维权限" />
      ) : (
        <Card>
          <Space className="section-card">
            <Select
              allowClear
              value={state}
              placeholder="发送状态"
              style={{ width: 170 }}
              options={['PENDING', 'SENDING', 'SENT', 'RETRY_WAIT', 'DEAD_LETTER', 'SKIPPED', 'DISABLED']}
              onChange={(value) => {
                setState(value)
                setPage(1)
              }}
            />
            <Button onClick={() => void query.refetch()}>刷新</Button>
          </Space>
          <QueryState loading={query.isLoading} error={query.error} onRetry={() => void query.refetch()}>
            <Table<NoticeView>
              rowKey="id"
              data={query.data?.items ?? []}
              pagination={{ current: page, pageSize: 20, total: query.data?.total ?? 0, onChange: setPage }}
              columns={[
                { title: '通知记录', dataIndex: 'id' },
                { title: '申请记录', dataIndex: 'requestId' },
                { title: '状态', render: (_, row) => <StateTag state={row.state} /> },
                { title: '尝试次数', dataIndex: 'attempts' },
                { title: '下次尝试', dataIndex: 'nextAttemptAt' },
                { title: '安全错误说明', dataIndex: 'lastError' },
                {
                  title: '操作',
                  render: (_, row) => (
                    <Button
                      disabled={
                        retry.isPending ||
                        query.isFetching ||
                        !context.data?.notificationsEnabled ||
                        !['DEAD_LETTER', 'SKIPPED', 'DISABLED'].includes(row.state)
                      }
                      onClick={() => {
                        retry.reset()
                        setComment('')
                        setTarget(row)
                      }}
                    >
                      重试原通知
                    </Button>
                  ),
                },
              ]}
            />
          </QueryState>
        </Card>
      )}
      <ReasonModal
        visible={target !== null}
        title="重试原通知（不会轮换凭证）"
        busy={retry.isPending}
        error={retry.error}
        comment={comment}
        onChange={setComment}
        onCancel={() => {
          if (!retry.isPending) setTarget(null)
        }}
        onConfirm={() => retry.mutate()}
      />
    </div>
  )
}
