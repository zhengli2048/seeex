import type { ReactNode } from 'react'
export function PermissionGate({ permission, permissions, children, fallback = null }: { permission: string; permissions?: string[]; children: ReactNode; fallback?: ReactNode }) { return permissions?.includes(permission) ? children : fallback }

