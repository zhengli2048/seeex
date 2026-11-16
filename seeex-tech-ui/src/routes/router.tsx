import { Navigate, createBrowserRouter } from 'react-router-dom'
import { AdminLayout } from '../layouts/AdminLayout'
import { LoginPage } from '../features/auth/LoginPage'
import { DashboardPage } from '../features/dashboard/DashboardPage'
import { ResourcePage } from '../features/resources/ResourcePage'
import { ResourcesPage } from '../features/resources/ResourcesPage'
import { EmployeesPage } from '../features/employees/EmployeesPage'
import { DepartmentsPage } from '../features/departments/DepartmentsPage'
import { WecomPage } from '../features/wecom/WecomPage'
import { FilesPage } from '../features/files/FilesPage'
import { AuditPage } from '../features/audit/AuditPage'
import { ApplicationsPage } from '../features/applications/ApplicationsPage'
import { isAuthenticated } from '../lib/auth'
import { PermissionAnalysisPage } from '../features/permissions/PermissionAnalysisPage'
import { PageErrorBoundary } from '../components/PageErrorBoundary'
// eslint-disable-next-line react-refresh/only-export-components
function Protected() { return isAuthenticated() ? <AdminLayout /> : <Navigate to="/login" replace /> }
export const router = createBrowserRouter([
  { path: '/login', element: <LoginPage />, errorElement: <PageErrorBoundary /> },
  { path: '/', element: <Protected />, errorElement: <PageErrorBoundary />, children: [
    { index: true, element: <DashboardPage /> },
    { path: 'applications', element: <ApplicationsPage /> },
    { path: 'employees', element: <EmployeesPage /> },
    { path: 'departments', element: <DepartmentsPage /> },
    { path: 'job-grades', element: <ResourcePage resource="job-grades" /> },
    { path: 'rbac/roles', element: <ResourcePage resource="roles" /> },
    { path: 'rbac/resources', element: <ResourcesPage /> },
    { path: 'permissions', element: <PermissionAnalysisPage /> },
    { path: 'wecom', element: <WecomPage /> },
    { path: 'files', element: <FilesPage /> },
    { path: 'audit-logs', element: <AuditPage /> },
    { path: '*', element: <PageErrorBoundary notFound /> },
  ]},
])
