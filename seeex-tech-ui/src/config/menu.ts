import { BarChart3, Building2, FileText, FolderOpen, KeyRound, Layers3, ShieldCheck, Users, Waypoints, Settings2, AppWindow } from 'lucide-react'
import type { ComponentType } from 'react'

export type MenuEntry = { path: string; label: string; icon: ComponentType<{ size?: number }>; permission: string }
export type MenuGroup = { key: string; label: string; icon: ComponentType<{ size?: number }>; items: MenuEntry[] }

export const menuGroups: MenuGroup[] = [
  { key: 'workspace', label: '工作台', icon: BarChart3, items: [{ path: '/', label: '工作台', icon: BarChart3, permission: 'platform:dashboard:view' }] },
  {
    key: 'platform', label: '系统平台', icon: Settings2, items: [
      { path: '/employees', label: '员工管理', icon: Users, permission: 'employees:view' },
      { path: '/departments', label: '部门管理', icon: Building2, permission: 'departments:view' },
      { path: '/job-grades', label: '职级管理', icon: Layers3, permission: 'job-grades:view' },
      { path: '/rbac/roles', label: '角色权限', icon: ShieldCheck, permission: 'rbac:roles:view' },
      { path: '/rbac/resources', label: '资源管理', icon: KeyRound, permission: 'rbac:resources:view' },
      { path: '/permissions', label: '权限分析', icon: ShieldCheck, permission: 'employees:view' },
      { path: '/wecom', label: '企业微信', icon: Waypoints, permission: 'wecom:config:view' },
      { path: '/files', label: '文件中心', icon: FolderOpen, permission: 'files:view' },
      { path: '/audit-logs', label: '审计日志', icon: FileText, permission: 'audit:view' },
    ],
  },
  { key: 'applications', label: '应用中心', icon: AppWindow, items: [{ path: '/applications', label: '应用注册', icon: Waypoints, permission: 'platform:apps:view' }] },
]

