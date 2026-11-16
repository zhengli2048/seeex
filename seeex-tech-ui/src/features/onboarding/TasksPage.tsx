import { Alert, Button, Card, Select, Space, Table } from '@arco-design/web-react'
import { useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { usePermissions } from '../../lib/management'
import { RequestDetail } from './RequestDetail'
import { QueryState, readApi, StateTag } from './shared'
import type { PageView, TaskView } from './types'
/** 待办与已办均由服务端限定本人候选记录，详情中领取后处理。 */
export function TasksPage() {
  const permissions = usePermissions(),
    [category, setCategory] = useState('PENDING'),
    [page, setPage] = useState(1),
    [selected, setSelected] = useState<number>()
  const query = useQuery({
    queryKey: ['onboarding', 'tasks', category, page],
    queryFn: () => readApi<PageView<TaskView>>('/me/approval-tasks', { category, page, pageSize: 20 }),
    enabled: permissions.can('workflow:tasks:view'),
  })
  return (
    <div className="onboarding-page">
      <div className="page-heading">
        <div>
          <h1>我的审批任务</h1>
          <p className="muted">仅展示本人任务；角色、部门及员工状态变化后，处理资格会实时重新检查。</p>
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
      ) : !permissions.can('workflow:tasks:view') ? (
        <Alert type="warning" content="暂无审批待办读取权限" />
      ) : (
        <Card>
          <Space className="section-card">
            <Select
              value={category}
              options={[
                { label: '待处理', value: 'PENDING' },
                { label: '已处理/失效', value: 'DONE' },
              ]}
              onChange={(value) => {
                setCategory(value)
                setPage(1)
              }}
            />
            <Button disabled={query.isFetching} onClick={() => void query.refetch()}>
              刷新
            </Button>
          </Space>
          <QueryState loading={query.isLoading} error={query.error} onRetry={() => void query.refetch()}>
            <Table<TaskView>
              rowKey="id"
              data={query.data?.items ?? []}
              loading={query.isFetching}
              pagination={{ current: page, pageSize: 20, total: query.data?.total ?? 0, onChange: setPage }}
              columns={[
                { title: '申请编号', dataIndex: 'requestNo' },
                { title: '应用', dataIndex: 'applicationName' },
                { title: '节点', dataIndex: 'nodeName' },
                { title: '状态', render: (_, row) => <StateTag state={row.state} /> },
                { title: '提醒截止', dataIndex: 'dueAt' },
                {
                  title: '操作',
                  render: (_, row) => (
                    <Button type="primary" onClick={() => setSelected(row.requestId)}>
                      查看材料与审批
                    </Button>
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
