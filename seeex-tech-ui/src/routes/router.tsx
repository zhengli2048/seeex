import { RequestsPage } from '../features/onboarding/RequestsPage'
import { TasksPage } from '../features/onboarding/TasksPage'
import { WorkflowsPage } from '../features/onboarding/WorkflowsPage'
import { InboxPage, NotificationJobsPage } from '../features/onboarding/InboxPage'
import { DraftGuardProvider } from '../components/DraftGuard'
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
function Protected() { return isAuthenticated() ? <DraftGuardProvider><AdminLayout /></DraftGuardProvider> : <Navigate to="/login" replace /> }
export const router = createBrowserRouter([
  { path: '/login', element: <LoginPage />, errorElement: <PageErrorBoundary /> },
  { path: '/', element: <Protected />, errorElement: <PageErrorBoundary />, children: [
    { index: true, element: <DashboardPage /> },
    { path: 'application-requests', element: <RequestsPage /> },
    { path: 'application-requests/manage', element: <RequestsPage scope="MANAGE" /> },
    { path: 'application-fulfillments', element: <RequestsPage scope="FULFILL" /> },
    { path: 'approval-tasks', element: <TasksPage /> },
    { path: 'workflow-definitions', element: <WorkflowsPage /> },
    { path: 'inbox', element: <InboxPage /> },
    { path: 'notification-jobs', element: <NotificationJobsPage /> },
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
