import { EntityManagementPage } from './EntityManagementPage'
import { ResourcesPage } from './ResourcesPage'
import type { ResourceKey } from './resourceDefinitions'
export function ResourcePage({ resource }: { resource: ResourceKey }) { return resource === 'resources' ? <ResourcesPage /> : <EntityManagementPage kind={resource} /> }
