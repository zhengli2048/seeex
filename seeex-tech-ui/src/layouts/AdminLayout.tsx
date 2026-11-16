import { Outlet, useLocation } from 'react-router-dom'
import { Breadcrumb } from '@arco-design/web-react'
import { Header } from './Header'
import { Sidebar } from './Sidebar'
import { useState } from 'react'
export function AdminLayout() { const [mobileOpen, setMobileOpen] = useState(false); return <div className="admin-shell"><Sidebar mobileOpen={mobileOpen} onClose={() => setMobileOpen(false)} /><main className="main-area"><Header onMobileMenu={() => setMobileOpen(true)} /><section className="content"><Breadcrumb className="app-breadcrumb"><Breadcrumb.Item>控制台</Breadcrumb.Item><Breadcrumb.Item>{({ employees: '员工管理', departments: '部门管理', 'job-grades': '职级管理', roles: '角色权限', resources: '资源管理', permissions: '权限分析', applications: '应用中心', files: '文件中心', wecom: '企业微信', 'audit-logs': '审计日志' } as Record<string, string>)[useLocation().pathname.split('/').filter(Boolean).slice(-1)[0]] ?? (useLocation().pathname === '/' ? '工作台' : '当前页面')}</Breadcrumb.Item></Breadcrumb><Outlet /></section></main></div> }



