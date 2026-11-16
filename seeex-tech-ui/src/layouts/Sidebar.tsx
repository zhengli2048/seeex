import { Alert, Button, Empty, Menu, Spin } from '@arco-design/web-react'
import type { ReactNode } from 'react'
import { useQuery } from '@tanstack/react-query'
import { useLocation, useNavigate } from 'react-router-dom'
import { AppWindow, ChevronRight, Layers, LayoutDashboard, X } from 'lucide-react'
import { api, getApiMessage, notifyApiError } from '../lib/api'
import { openApplication } from '../lib/management'

interface NavigationNode {
  key: string
  label: string
  kind: 'GROUP' | 'INTERNAL' | 'EXTERNAL'
  appCode?: string
  entry?: 'WEB' | 'ADMIN'
  path?: string
  openType?: string
  children: NavigationNode[]
}

export function Sidebar({ mobileOpen, onClose }: { mobileOpen: boolean; onClose: () => void }) {
  const location = useLocation()
  const navigate = useNavigate()
  const navigation = useQuery({ queryKey: ['me-navigation'], queryFn: async () => (await api.get<{ data: NavigationNode[] }>('/me/navigation')).data.data })
  const nodes = navigation.data ?? []
  const actions = new Map<string, NavigationNode>()
  let selectedKey = ''
  let selectedParents: string[] = []
  function renderNode(node: NavigationNode, depth = 0, parents: string[] = []): ReactNode {
    const hasChildren = node.children.length > 0
    const actionKey = hasChildren ? `${node.key}:open` : node.key
    if (node.kind !== 'GROUP') {
      actions.set(actionKey, node)
      if (node.kind === 'INTERNAL' && node.path === location.pathname) { selectedKey = actionKey; selectedParents = hasChildren ? [...parents, node.key] : parents }
    }
    const icon = depth === 0 ? (node.key === 'workspace' ? <LayoutDashboard size={17} /> : node.appCode && node.appCode !== 'platform' ? <AppWindow size={17} /> : <Layers size={17} />) : <ChevronRight size={14} />
    if (hasChildren) return <Menu.SubMenu key={node.key} title={<>{icon}{node.label}</>}>
      {node.kind !== 'GROUP' && <Menu.Item key={actionKey}>打开{node.label}</Menu.Item>}
      {node.children.map((child) => renderNode(child, depth + 1, [...parents, node.key]))}
    </Menu.SubMenu>
    return node.kind === 'GROUP' ? null : <Menu.Item key={node.key}>{icon}{node.label}</Menu.Item>
  }
  const menuItems = nodes.map((node) => renderNode(node))
  return <aside className={'sidebar ' + (mobileOpen ? 'mobile-open' : '')}>
    <div className="brand">
      <img src="/brand/seeex-logo.png" alt="SEEEX" className="brand-logo" />
      <div className="brand-copy"><strong>SEEEX</strong><span>统一工作空间</span></div>
      <button className="mobile-close" aria-label="关闭菜单" onClick={onClose}><X size={18} /></button>
    </div>
    {navigation.isLoading ? <div className="sidebar-feedback"><Spin tip="加载授权菜单" /></div> : navigation.isError ? <div className="sidebar-feedback"><Alert type="error" content={getApiMessage(navigation.error)} action={<Button size="mini" onClick={() => void navigation.refetch()}>重试</Button>} /></div> : nodes.length === 0 ? <Empty description="暂无可访问菜单" /> : <Menu key={nodes.map((node) => node.key).join('|') + location.pathname} className="sidebar-menu" selectedKeys={selectedKey ? [selectedKey] : []} defaultOpenKeys={[...new Set([...nodes.filter((node) => node.children.length).map((node) => node.key), ...selectedParents])]} autoScrollIntoView onClickMenuItem={(key) => {
      const node = actions.get(key)
      if (!node?.path) return
      if (node.kind === 'INTERNAL') navigate(node.path)
      if (node.kind === 'EXTERNAL' && node.appCode) void openApplication(node.appCode, node.path, node.entry, node.openType).catch(notifyApiError)
      onClose()
    }}>{menuItems}</Menu>}
  </aside>
}
