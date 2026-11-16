# Seeex Console Management Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Turn the existing read-only Seeex Console skeleton into a usable management console with persisted CRUD, server-side permission enforcement, file operations, and automatic access-token renewal.

**Architecture:** Keep the current Spring Boot/MyBatis-Plus and React/Arco/TanStack Query stack. Add a focused backend management service that owns pagination, validation, soft deletion, and permission checks; expose a stable REST contract to a reusable typed frontend resource layer. Keep file operations in the file module and authentication refresh in the Axios boundary.

**Tech Stack:** Java 21, Spring Boot 4, Spring Security, MyBatis-Plus, Flyway, React 19, TypeScript, Arco Design, TanStack Query, Axios.

**Spec:** `seeex-tech-ui/docs/superpowers/specs/2026-11-15-console-management-design.md`

## Global Constraints

- Preserve the existing `ApiResponse<T>` envelope and `/api` route defaults.
- Enforce permissions in backend services/controllers; frontend gates only control presentation.
- Use server-side pagination and filtering for every management list.
- Keep soft-delete/status semantics consistent with the existing schema.
- Do not report mutation success unless the backend request succeeds.
- Add focused tests before production changes for each new service behavior; run the complete backend and frontend verification commands before completion.

## File Map

Backend:

- `web/src/main/java/com/seeex/tech/web/organization/OrganizationManagementService.java`: validated organization CRUD and paged queries.
- `web/src/main/java/com/seeex/tech/web/organization/OrganizationController.java`: employee, department, and job-grade REST endpoints.
- `web/src/main/java/com/seeex/tech/web/rbac/entity/{RoleDO,ResourceDO,RoleResourceDO}.java` and mappers: RBAC persistence models.
- `web/src/main/java/com/seeex/tech/web/rbac/RbacManagementService.java` and `RbacController.java`: role/resource CRUD, grants, and effective permission reads.
- `web/src/main/java/com/seeex/tech/web/api/PlatformController.java`: retain `/me` compatibility while delegating permission and list behavior.
- `web/src/main/java/com/seeex/tech/web/auth/AuthController.java` and `TokenSessionService.java`: real account validation and refresh-compatible session behavior where existing schema supports it.
- `file/src/main/java/com/seeex/tech/file/service/FileMetadataService.java` and `FileController.java`: paged listing, ownership checks, and batch deletion.
- `web/src/test/java/...`: service/controller contract tests for CRUD and authorization.

Frontend:

- `seeex-tech-ui/src/features/resources/resourceDefinitions.ts`: typed resource endpoint, field, and permission definitions.
- `seeex-tech-ui/src/features/resources/resourceApi.ts`: typed list/detail/mutation helpers.
- `seeex-tech-ui/src/features/resources/ResourcePage.tsx`: shared filter/table/modal CRUD behavior.
- `seeex-tech-ui/src/features/files/fileApi.ts` and `FilesPage.tsx`: file list/upload/download/delete flows.
- `seeex-tech-ui/src/lib/api.ts` and `auth.ts`: single-flight refresh and one-time request retry.
- `seeex-tech-ui/src/components/PermissionGate.tsx`: page and action-level gates.

## Tasks

### Task 1: Establish failing backend CRUD and permission tests

- [ ] Add tests for paged employee/department/job-grade queries, validation failures, soft deletion, role/resource CRUD, and denied mutations.
- [ ] Run the focused Maven tests and confirm they fail because the management services/endpoints do not exist.

### Task 2: Implement organization CRUD

- [ ] Add service methods for filtered page queries, detail lookup, create, update, soft delete, and batch delete.
- [ ] Validate required fields and uniqueness through MyBatis-Plus conditions; return stable conflict/not-found/validation errors.
- [ ] Add REST endpoints under `/api/employees`, `/api/departments`, and `/api/job-grades` with `page`, `page_size`, and filter parameters.
- [ ] Require the corresponding `*:view`, `*:create`, `*:update`, and `*:delete` permissions for each operation.
- [ ] Run the focused tests and then the web module tests.

### Task 3: Implement persisted RBAC management

- [ ] Add role, resource, and role-resource entity/mapper classes for the existing Flyway tables.
- [ ] Implement paged role/resource CRUD and grant replacement with transaction boundaries and validation.
- [ ] Compute `/api/permissions` from the authenticated employee and persisted assignments; keep DENY overriding ALLOW.
- [ ] Add authorization tests proving a user cannot mutate a resource without the matching permission.

### Task 4: Complete file management APIs

- [ ] Add paged file metadata listing with tenant/owner/business filters.
- [ ] Make confirm/delete verify ownership, current state, and affected-row count; support batch deletion safely.
- [ ] Keep original filenames in download responses and expose typed metadata needed by the UI.
- [ ] Add tests for PENDING → CONFIRMED → DELETED transitions and cross-tenant access denial.

### Task 5: Implement refresh-token retry and typed frontend API boundary

- [ ] Add a failing pure test for concurrent 401 responses sharing one refresh request and retrying the original request once.
- [ ] Implement the single-flight Axios response interceptor with refresh-token rotation and logout fallback.
- [ ] Add typed resource definitions and API helpers for list/detail/create/update/delete/batch-delete.
- [ ] Run frontend type checking and the focused API tests.

### Task 6: Replace read-only resource pages with CRUD screens

- [ ] Add server-side filter and pagination controls, selection state, refresh, create/edit modal, detail view, delete confirmation, and batch delete.
- [ ] Bind employee, department, job-grade, role, and resource pages to the shared definitions and permission gates.
- [ ] Ensure mutation success invalidates the relevant TanStack Query cache and failure shows the backend message.
- [ ] Verify build/lint and exercise the page contracts with focused component tests or pure request tests.

### Task 7: Complete file center UI and integration verification

- [ ] Add file picker upload flow: request upload URL, PUT the file, confirm metadata, refresh list, and show progress/errors.
- [ ] Add download URL handling, row selection, single/batch delete, and ownership-aware empty/loading/error states.
- [ ] Run `mvn verify`, `npm run lint`, `npm run build`, and focused tests; inspect the final diff for unsupported mock-success paths.

## Review Focus

- Server-side authorization must protect every mutation even if the frontend is bypassed.
- Page totals must come from the backend, not client-side slicing.
- Refresh must never issue parallel refresh requests or retry a request more than once.
- File operations must preserve tenant/owner scope and state transitions.
- API response field names must remain compatible with the existing `ApiResponse<T>` contract.

## Execution Notes

Implement task-by-task in the current workspace because the frontend has no Git repository and the backend repository has no committed baseline. Keep the plan updated as each task is completed and run the narrowest relevant verification before moving to the next task.
