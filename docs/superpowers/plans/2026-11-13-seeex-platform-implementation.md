# Seeex 平台实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development or superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 按当前技术架构完成平台基础能力，包括认证、RBAC、企业微信同步、文件服务和平台管理台，并让前后端通过统一 API 协议协作。

**Architecture:** `seeex-tech-ui` 负责页面、路由、权限展示和 API 调用；`seeex-tech-web` 负责用户认证、RBAC、组织数据、企业微信同步和业务 API；`seeex-tech-file` 独立负责 OSS 文件元数据、签名 URL 和文件权限；MySQL 由 Flyway 管理，不使用数据库外键。

**Tech Stack:** React 19, TypeScript 6, Vite 8, Arco Design, React Router 7, TanStack Query 5, Axios, Zod, Java 21, Spring Boot 4.0.9, Spring Security, MyBatis-Plus 3.5.18, MySQL 8, Flyway, Alibaba OSS, Micrometer Tracing, OpenTelemetry.

**Spec:** `D:\seeex\code\docs\superpowers\specs\2026-11-13-seeex-technical-architecture.md`

## Global Constraints

- 所有页面使用浅色背景，禁止深色背景。
- 接口错误统一使用 `code`、`error_type`、`message`。
- 数据库使用 MySQL 8、utf8mb4、InnoDB。
- 数据库不使用 `FOREIGN KEY`，关系由应用层维护。
- `created_by`、`updated_by`、`deleted_by` 保存显示名称字符串。
- 不建立 `employee_phone_history`、`employee_external_identity`、`external_identity_conflict`。
- 前端只负责权限展示，后端必须独立校验 API 权限。
- OSS AccessKey 只允许文件服务持有。

## Review Focus

- 前端 `ApiResponse` 与后端字段保持一致，不能重新引入 `error_message`。
- 未登录、无权限、资源不存在和业务冲突必须映射到稳定错误码。
- OAuth state、refresh token、上传幂等键必须一次性或可追踪失效。
- 企业微信重复事件不能重复修改员工、部门和授权数据。
- 文件下载必须校验租户、所有者或业务数据范围，并保留原始文件名。

### Task 1: MySQL Flyway 基础迁移

**Files:**

- Create: `web/src/main/resources/db/migration/V1__organization.sql`
- Create: `web/src/main/resources/db/migration/V2__authentication.sql`
- Create: `web/src/main/resources/db/migration/V3__rbac.sql`
- Create: `web/src/main/resources/db/migration/V4__wecom_sync.sql`
- Create: `web/src/main/resources/db/migration/V5__audit.sql`
- Modify: `web/src/main/resources/application.yml`

**Acceptance:** MySQL 初始化成功，所有字段有 `comment`，不存在 `FOREIGN KEY`，Flyway 能按顺序执行。

### Task 2: 后端统一认证和用户上下文

**Files:**

- Create: `web/src/main/java/com/seeex/tech/web/auth/AuthController.java`
- Create: `web/src/main/java/com/seeex/tech/web/auth/AuthService.java`
- Create: `web/src/main/java/com/seeex/tech/web/auth/AuthSessionService.java`
- Modify: `web/src/main/java/com/seeex/tech/web/config/SecurityConfig.java`
- Modify: `common/src/main/java/com/seeex/tech/common/api/ErrorCode.java`

**Interfaces:**

- `POST /api/auth/login`
- `POST /api/auth/refresh`
- `POST /api/auth/logout`
- `GET /api/me`

**Acceptance:** 登录、刷新、注销和 401/403 响应符合 API Contract；Token 只保存哈希。

### Task 3: RBAC 权限服务

**Files:**

- Create: `web/src/main/java/com/seeex/tech/web/rbac/PermissionService.java`
- Create: `web/src/main/java/com/seeex/tech/web/rbac/PermissionController.java`
- Create: `web/src/main/java/com/seeex/tech/web/rbac/mapper/`
- Create: `web/src/main/java/com/seeex/tech/web/rbac/model/`

**Acceptance:** 支持员工、部门、职级授权；执行 `FINAL = ALLOW - DENY`；支持有效期和数据范围；后端 API 权限检查独立于前端菜单。

### Task 4: 企业微信 OAuth 和同步

**Files:**

- Create: `web/src/main/java/com/seeex/tech/web/wecom/WeComAuthController.java`
- Create: `web/src/main/java/com/seeex/tech/web/wecom/WeComClient.java`
- Create: `web/src/main/java/com/seeex/tech/web/wecom/WeComSyncService.java`
- Create: `web/src/main/java/com/seeex/tech/web/wecom/WeComSyncJob.java`

**Interfaces:**

- `GET /api/auth/wecom/authorize`
- `GET /api/auth/wecom/callback`
- `POST /api/integrations/wecom/events`
- `POST /api/integrations/wecom/sync/full`
- `GET /api/integrations/wecom/sync/runs/{runId}`

**Acceptance:** state 一次性使用；事件按 provider/type/id 或 payload hash 幂等；失败支持重试和死信；同步只更新内部员工、部门、职级和授权。

### Task 5: 文件服务权限和元数据

**Files:**

- Modify: `file/src/main/java/com/seeex/tech/file/controller/FileController.java`
- Modify: `file/src/main/java/com/seeex/tech/file/service/FileMetadataService.java`
- Modify: `file/src/main/java/com/seeex/tech/file/service/OssStorageService.java`
- Create: `file/src/main/java/com/seeex/tech/file/service/FileCleanupService.java`

**Interfaces:**

- `POST /api/files/upload-url`
- `POST /api/files/confirm`
- `GET /api/files/download-url`
- `DELETE /api/files`

**Acceptance:** 服务令牌、用户和租户上下文全部校验；文件状态按 `PENDING -> CONFIRMED -> DELETED` 流转；下载响应保留原始文件名；过期 PENDING 文件可清理。

### Task 6: 前端 API 基础层

**Files:**

- Modify: `seeex-tech-ui/src/lib/api.ts`
- Create: `seeex-tech-ui/src/lib/auth.ts`
- Create: `seeex-tech-ui/src/lib/queryKeys.ts`
- Create: `seeex-tech-ui/src/components/PermissionGate.tsx`

**Acceptance:** Axios 自动注入 Bearer Token；统一处理 401、403、网络错误；`ApiResponse` 不包含 `error_message`；权限组件只控制显示。

### Task 7: 前端路由和菜单

**Files:**

- Modify: `seeex-tech-ui/src/routes/router.tsx`
- Modify: `seeex-tech-ui/src/layouts/Sidebar.tsx`
- Modify: `seeex-tech-ui/src/layouts/AdminLayout.tsx`
- Create: `seeex-tech-ui/src/config/menu.ts`

**Acceptance:** 菜单来自权限资源；无权限页面不展示；路由与文档中的菜单表一致；页面背景始终为浅色。

### Task 8: 平台基础前端页面

**Files:**

- Create: `seeex-tech-ui/src/features/dashboard/`
- Create: `seeex-tech-ui/src/features/employees/`
- Create: `seeex-tech-ui/src/features/departments/`
- Create: `seeex-tech-ui/src/features/rbac/`
- Create: `seeex-tech-ui/src/features/wecom/`
- Create: `seeex-tech-ui/src/features/files/`
- Create: `seeex-tech-ui/src/features/audit/`

**Acceptance:** 平台基础列表页具备加载、空、错误、无权限和分页状态；高风险操作有确认弹窗；文件页显示原始文件名和业务归属。业务域页面不在本计划内，后续按业务模块单独建计划。

### Task 9: 联调和交付

**Files:**

- Create: `web/src/test/`
- Create: `file/src/test/`
- Create: `seeex-tech-ui/src/**/*.test.tsx`
- Update: `D:\seeex\code\docs\design\00-current-model.md`

**Acceptance:** 前后端错误码一致；登录、权限、同步、上传、下载流程可以端到端演示；MySQL Flyway、Maven 构建和前端构建通过。
