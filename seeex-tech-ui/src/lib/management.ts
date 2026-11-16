import { useQuery } from '@tanstack/react-query'
import { api, type PageResult } from './api'

export interface TreeRow { id: number; name: string; parentId?: number | null; sortOrder?: number }
export interface TreeNode { key: string; title: string; children: TreeNode[]; disabled?: boolean }

/** 关联选择器读取全部分页，避免 100 条上限静默截断。 */
export async function loadAll<T>(endpoint: string, filters: Record<string, unknown> = {}): Promise<T[]> {
  const rows: T[] = []
  for (let page = 1; ; page++) {
    const { data } = await api.get<{ data: PageResult<T> }>(endpoint, { params: { ...filters, page, pageSize: 100 } })
    rows.push(...data.data.items)
    if (rows.length >= data.data.total || data.data.items.length === 0) return rows
  }
}

/** 树节点使用字符串 key；API 边界显式转换数字 ID。 */
export function treeNodes(rows: TreeRow[], excluded?: number): TreeNode[] {
  const blocked = new Set<number>(excluded === undefined ? [] : [excluded])
  let changed = true
  while (changed) {
    changed = false
    rows.forEach((row) => { if (row.parentId && blocked.has(row.parentId) && !blocked.has(row.id)) { blocked.add(row.id); changed = true } })
  }
  const nodes = new Map<number, TreeNode>()
  const sorted = [...rows].sort((a, b) => (a.sortOrder ?? 0) - (b.sortOrder ?? 0) || a.id - b.id)
  sorted.forEach((row) => { if (!blocked.has(row.id)) nodes.set(row.id, { key: String(row.id), title: row.name, children: [] }) })
  const roots: TreeNode[] = []
  sorted.forEach((row) => { const node = nodes.get(row.id); if (!node) return; const parent = row.parentId ? nodes.get(row.parentId) : undefined; if (parent) parent.children.push(node); else roots.push(node) })
  return roots
}

export function usePermissions() {
  const query = useQuery({ queryKey: ['permissions'], queryFn: async () => (await api.get<{ data: { effective: string[] } }>('/permissions')).data.data })
  return { ...query, can: (code: string) => query.data?.effective.includes(code) ?? false }
}

/** 在用户手势中先打开窗口，异步授权后替换地址，避免被拦截。 */
export async function openApplication(appCode: string, redirectPath: string, entry: 'WEB' | 'ADMIN' = 'WEB', openType = 'NEW_TAB') {
  const target = openType === 'CURRENT_TAB' ? null : window.open('about:blank', '_blank')
  if (target) target.opener = null
  try {
    const { data } = await api.get<{ data: { redirectUrl: string } }>('/sso/authorize', { params: { appCode, redirectPath, entry } })
    if (target) target.location.replace(data.data.redirectUrl)
    else window.location.assign(data.data.redirectUrl)
  } catch (error) { target?.close(); throw error }
}
