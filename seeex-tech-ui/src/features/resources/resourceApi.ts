import { api, type PageResult } from '../../lib/api'
import type { ResourceDefinition, ResourceKey } from './resourceDefinitions'

export type ResourceRow = Record<string, unknown> & { id: number }
export interface ResourceQuery { page: number; pageSize: number; keyword?: string; status?: string; applicationCode?: string }

export async function listResource(definition: ResourceDefinition, query: ResourceQuery) {
  const response = await api.get<{ data: PageResult<ResourceRow> }>(definition.endpoint, { params: { page: query.page, pageSize: query.pageSize, keyword: query.keyword || undefined, status: query.status || undefined, applicationCode: query.applicationCode || undefined } })
  return response.data.data
}

export async function createResource(definition: ResourceDefinition, values: Record<string, unknown>) {
  const response = await api.post<{ data: ResourceRow }>(definition.endpoint, values)
  return response.data.data
}

export async function updateResource(definition: ResourceDefinition, id: number, values: Record<string, unknown>) {
  const response = await api.put<{ data: ResourceRow }>(`${definition.endpoint}/${id}`, values)
  return response.data.data
}

export async function deleteResource(definition: ResourceDefinition, ids: number[]) {
  await api.delete(definition.endpoint, { data: ids })
}

export function definitionFor(key: ResourceKey) {
  return key
}
