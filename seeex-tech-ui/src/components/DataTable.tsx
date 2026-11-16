import { Empty, Pagination, Spin, Table } from '@arco-design/web-react'

interface DataTableProps {
  columns: any[]
  data: any[]
  rowKey: string
  loading?: boolean
  error?: string
  total?: number
  page?: number
  pageSize?: number
  onPageChange?: (page: number) => void
  rowSelection?: any
  emptyText?: string
  pagination?: boolean
}

/** 通用服务端分页表格：内置 loading/empty/error 三态 + 分页。 */
export function DataTable(props: DataTableProps) {
  const { columns, data, rowKey, loading, error, total = 0, page = 1, pageSize = 10, onPageChange, rowSelection, emptyText = '暂无数据', pagination = true } = props
  if (loading) return <div className="state"><Spin />加载中...</div>
  if (error) return <div className="state error">{error}</div>
  if (!data.length) return <Empty description={emptyText} />
  return (
    <div>
      <Table rowKey={rowKey} columns={columns} data={data} rowSelection={rowSelection} pagination={false} />
      {pagination && onPageChange && (
        <Pagination className="pagination" current={page} total={total} pageSize={pageSize} showTotal showJumper onChange={onPageChange} />
      )}
    </div>
  )
}
