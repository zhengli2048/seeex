# Seeex Console Management Capabilities Design

## Intent

Upgrade the current read-only administration pages into usable management screens for employees, departments, job grades, RBAC roles, RBAC resources, and files. Add page-level permission enforcement and automatic access-token renewal without scattering backend assumptions across UI components.

## Goals

- Provide consistent list, filtering, server-side pagination, create, edit, detail, delete, and batch-delete flows for the five resource areas.
- Provide file listing, upload, download, selection, and delete flows with batch actions.
- Enforce create, update, delete, and view permissions at the action and page levels through `PermissionGate`.
- Retry the original request once after a single-flight refresh-token request when the API returns `401`.
- Keep backend alignment in one typed contract layer so endpoint paths, methods, query parameters, response envelopes, and field mappings are easy to update.

## Non-goals

- Defining or changing backend APIs.
- Building a schema-driven form engine.
- Adding optimistic updates or offline behavior.
- Hiding unsupported backend actions behind fake client-side success states.

## Constraints

- Preserve the existing Axios `ApiResponse<T>` envelope and authentication headers.
- Preserve Arco Design, React Query, React Router, and the current visual language.
- Use server-side pagination for resource lists.
- Keep existing routes and existing endpoint defaults compatible with the current frontend.

## Approaches

1. **Reusable typed CRUD layer (selected):** resource definitions declare endpoint paths, field metadata, permissions, and request mappings; shared components render list and form behavior. This minimizes duplication while allowing per-resource overrides.
2. **Independent pages:** each page owns its API calls and forms. This offers maximum local control but duplicates pagination, permission, error, and mutation behavior.
3. **Backend-driven schema:** the server supplies field and action schemas. The current project has no schema protocol, so this would add a new cross-service dependency and is deferred.

The reusable typed layer is selected because the five resource pages share behavior and the backend contract is not available in this repository. Explicit resource definitions keep the integration boundary visible and editable.

## Backend contract boundary

- `src/features/resources/resourceDefinitions.ts` will be the source of truth for resource endpoints, supported actions, permissions, list query names, and field mappings.
- API helpers will expose typed functions for list, detail, create, update, delete, and batch delete. Components will consume those helpers rather than constructing URLs.
- Default REST paths follow the existing routes (`/employees`, `/departments`, `/job-grades`, `/rbac/roles`, `/rbac/resources`). Each definition can override the method or path when the backend contract differs.
- List requests send `page`, `page_size`, and configured filter keys; responses use `{ items, total }` inside `data`.
- Mutations send JSON bodies by default. Definitions can mark a field as `FormData` when an API requires multipart input.
- Refresh uses `VITE_AUTH_REFRESH_PATH` with a default of `/auth/refresh`; the response must provide `accessToken` and may provide `refreshToken`.
- File APIs are centralized under `src/features/files/fileApi.ts`, with configurable list/upload/download/delete paths and typed metadata.

## UX behavior

- Each resource page has a filter row, refresh button, create button, table, row actions, selection checkboxes, and pagination.
- Create and edit use the same modal form. Detail uses a read-only modal or drawer.
- Delete and batch delete require confirmation and invalidate the relevant React Query cache after success.
- Unsupported actions are disabled or hidden by permission and never report success without a successful API response.
- File upload uses a file picker and displays upload progress state; download opens the server-provided URL.
- Page access and individual actions are guarded with `PermissionGate`; the sidebar remains a navigation convenience, not the security boundary.

## Risks and calls

- Exact backend field names are not present in this repository. The initial definitions use the fields already rendered (`id`, `name`, `code`, `status`) and isolate overrides so contract changes remain local.
- The refresh endpoint shape is not present either. The client will accept both `data.accessToken` and a top-level `accessToken` response shape through one normalizer, while preserving the current envelope handling.
- No automated test runner exists in `package.json`; verification will use TypeScript build, ESLint, and focused pure-function tests added with a lightweight test setup only if required by the implementation.

