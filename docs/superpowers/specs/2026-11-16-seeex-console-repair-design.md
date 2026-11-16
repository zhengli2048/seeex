# SEEEX Console 全量闭环修复设计

> 日期：2026-11-16
> 状态：待评审（brainstorming 产出，用户 review 后进入 writing-plans）

## 1. 背景与目标

将当前 SEEEX Console 从「接口样例 + 演示闭环」改造成可持续扩展的企业管理平台。
现状缺陷与缺口来自两部分：代码实况审查（本会话已完成）、以及 `docs/requirements`、`docs/design`、`docs/plans` 与代码之间的不一致。

目标：一次收敛需求口径，补齐数据模型、认证权限、组织/RBAC/应用/审计、企业微信同步、前端五条线的完整闭环，消除所有「假数据 / 假成功 / JSON 详情 / ID 手填 / 内存态丢失」问题。

## 2. 范围

**做**：真实认证与权限核心、组织与 RBAC、应用注册中心、审计、企业微信完整同步/事件状态机、前端重建。

**不做**：
- `ai` 后端模块（不开发，仅保留通过注册中心接入外部应用的能力）。
- 真实企业微信联调（沿用约束，代码/接口/静态检查完整，页面标注「未联调」）。
- 真实 OSS 联调（沿用约束）。
- 通用多租户能力（本项目单公司、单租户上下文）。

## 3. 权威口径裁定（ADR，解决文档矛盾）

> 这些裁定是后续所有实现的事实来源。需求规格 `rbac-wecom-requirements.md` 部分条目已过时，以本表为准。

| # | 主题 | 裁定 | 推翻/澄清的旧口径 |
|---|---|---|---|
| ADR-1 | 外部身份 | 不建 `external_identity` 表，企微同步直接写 `employee` | 需求 FR-WECOM-001 已过时 |
| ADR-2 | 员工多部门 | 建 `employee_department` 关联表，员工可属多部门、最多一个主部门 | 当前只有 `primary_department_id` |
| ADR-3 | 员工状态 | `ACTIVE` / `DISABLED` / `LEFT` / `LOCKED` | 代码用 `ACTIVE/INACTIVE/DELETED` |
| ADR-4 | 数据范围 | `SELF` / `DEPARTMENT` / `DEPARTMENT_AND_DESCENDANTS` / `ALL` / `CUSTOM`，挂角色 | FR-DATA-004 保留 |
| ADR-5 | 资源类型 | 7 类：`APPLICATION` / `MODULE` / `PAGE` / `MENU` / `ACTION` / `API` / `DATA_SCOPE` | 代码 4 类 |
| ADR-6 | 软删除 | `deleted_at` 时间戳 | 代码用 `status=DELETED` |
| ADR-7 | 业务编号 | 日期+序列 `YYYYMMDDNNNNNNNN`，主键与业务编号分离 | 代码用 UUID/无 |
| ADR-8 | 同步/事件状态机 | 完整实现（PENDING/PARTIAL、RETRY_WAIT/DEAD_LETTER、退避/死信/乱序/幂等） | 代码只有 RUNNING/SUCCEEDED/FAILED |
| ADR-9 | 登录契约 | 入参 `{phone, password}`；返回 `access_token`（snake_case）等 | 代码 `{loginPhone}` / `accessToken` |
| ADR-10 | 菜单 | 方案 A：`MENU` 类型资源即菜单，不建独立 `menu` 表 | 需求 FR-RBAC-004「独立 menu 表」过时 |
| ADR-11 | 职级 | 全局字典；挂 `employee_department.job_grade_id`；职级不直接当授权主体，每个职级绑一个「默认角色」 | 代码把 JOB_GRADE 当直接主体 |
| ADR-12 | ai 模块 | 不建；应用注册中心保留 | — |
| ADR-13 | 错误响应 | 用 `message` 字段（`error_type` 保留） | 需求 §7 的 `error_message` 为旧版 |
| ADR-14 | 分页响应 | `{ items, total }` | unified-plan 的 `page/pageSize` 不强加 |
| ADR-15 | 授权主体 | `EMPLOYEE` / `DEPARTMENT`（职级走默认角色），不引入 `APPLICATION` 主体，应用边界用 `application_code` 体现 | unified-plan §4 含 APPLICATION |
| ADR-16 | 前端 | 菜单后端化 + 应用切换 + 面包屑 + 工作台丰富化 + 拆分专用页面 | 当前 menu.ts 写死、无面包屑 |

## 4. 数据模型（Phase 0，Flyway 迁移）

> 全部增量迁移，方言 MySQL（与 `application.yml` 一致）。命名 snake_case，公共字段 `created_at/updated_at/deleted_at`。

### 4.1 组织与账号

- `employee`：加 `source`（`LOCAL`/`WECOM`）、`deleted_at`；`status` 取值改为 `ACTIVE/DISABLED/LEFT/LOCKED`。
- `employee_department`（新）：`employee_id` + `department_id` + `job_grade_id` + `is_primary` + `deleted_at`，唯一约束 `(employee_id, department_id)`，最多一条 `is_primary=1`。
- `department`：加 `leader_employee_id`（负责人，仅组织属性不赋权）、`deleted_at`。
- `job_grade`：加 `default_role_id`、`deleted_at`。
- `employee_account`（接入，表已建）：`login_phone` 业务唯一，`password_hash` 用 BCrypt。

### 4.2 RBAC 与资源

- `resource`：`resource_type` 支持 7 类；加 `deleted_at`；保留 `application_code`/`parent_id`/`route_path`/`icon`/`sort_order`/`visible`/`is_public`。
- `role`：加 `role_type`（`SYSTEM`/`CUSTOM`）、`deleted_at`。
- `role_resource`：加 `deleted_at`、`assignment_source`/`source_key`/`sync_run_id`（来源追踪）。
- `subject_role_assignment`：加 `deleted_at`、`assignment_source`（`MANUAL`/`JOB_GRADE`/`DEPARTMENT`/`WE_COM`）、`source_key`、`sync_run_id`。
- `role_data_scope`（新）：`role_id` + `resource_code` + `scope_type` + `scope_config`(JSON) + `deleted_at`。

### 4.3 应用注册（复用已建表，接入）

- `application_registry`、`application_route_release`（已建）。
- `application_ci_credential`、`application_route_approval`、`application_release_rollout`（V8 已建，接入）。
- `auth_sso_exchange_code`（已建，接入，60s 一次性）。

### 4.4 审计与同步

- `audit_log`（已建，扩展）：加 `source_ip`、`request_summary`、`before_json`、`after_json`、`result`。
- `sync_run`（扩展）：`status` 支持 `PENDING/RUNNING/SUCCESS/PARTIAL/FAILED`。
- `sync_event`（扩展）：`status` 支持 `RECEIVED/PROCESSING/SUCCESS/RETRY_WAIT/DEAD_LETTER`，加 `retry_count`、`next_retry_at`、`processed_at`、`error_message`、`payload`（原文落库，不加密，普通前端不可见）。

### 4.5 业务编号生成

统一序列生成器（基于日期 + 每类独立序列，数据库唯一约束兜底）。对象：员工、部门、职级、角色、资源。生成后业务编号不可直接修改。

## 5. 认证与权限核心（Phase 1）

1. `employee_account` 接入：`AccountService` + mapper；登录校验 `phone + BCrypt`。
2. `AuthController.login` 改用 `{phone, password}`，签发 subject=真实 `employeeId` 的 JWT；返回 `access_token/refresh_token/expires_in/员工摘要/has_permission`。
3. 移除 `ManagementAuthorizationService` 的 `demo` 绕过；`PlatformController./me` 返回真实员工信息。
4. `/me/roles`、`/me/resources`、`/me/data-scopes` 补齐。
5. `PermissionService` 扩展：
   - 角色来源 = 员工直绑 + 各所属部门角色 + 各(部门+职级)职级默认角色；
   - 合并 `ALLOW - DENY` 得最终权限；
   - `visibleMenus`（菜单六条过滤规则，见 §6.3）；
   - `resolveDataScope`（`role_data_scope` 合并取最宽）；
   - 进程内短 TTL 缓存 + 失效事件。
6. 未登记 API 默认拒绝：`SecurityConfig` 改为按 `permission_code` 白名单/资源登记校验，公共接口（登录/健康检查/企微回调）显式白名单。

## 6. 资源 / 角色 / 菜单 / 权限 / 应用模型

### 6.1 资源树

```
Application
 └─ Module
     └─ Page
         ├─ Menu ── Action(按钮)
         ├─ Action
         ├─ API
         └─ DATA_SCOPE
```

- 所有节点带 `application_code`，父资源不得跨应用；
- 层级语义由 `resource_type + parent_id` 组合；按钮可挂 Menu 或 Page 下；
- 每资源有稳定 `permission_code`。

### 6.2 权限计算

```
有效角色 = 员工直绑角色 ∪ 各部门角色 ∪ 各(部门+职级)默认角色
ALLOW = 有效角色 role_resource 的 ALLOW 并集
DENY  = 有效角色 role_resource 的 DENY 并集
FINAL = ALLOW − DENY
数据范围 = 按 role + resource_code 解析，取最宽（ALL > ..._AND_DESCENDANTS > DEPARTMENT > CUSTOM > SELF）
```

### 6.3 动态菜单（MENU 资源即菜单）

过滤规则：节点 `visible=true` 且 ACTIVE；有有效权限，或存在可见且有权限的子节点（保留父节点）；按 `parent_id + sort_order` 组树。菜单只影响导航，API/按钮仍独立鉴权。

### 6.4 应用边界

- 角色授权按 `application_code` 范围保存，范围外不被覆盖（保留并强化现有 replaceGrants 语义）；
- 动态菜单按应用分组注入「已接入应用」；
- 应用注册中心：注册 → CI 上报路由元数据 → 版本发布/审核/灰度 → 动态菜单 → SSO，全部 DB 持久化。

## 7. 组织 / RBAC / 应用 / 审计后端闭环（Phase 2）

1. 编码自动生成：`createXxx` 在 code 为空时自动生成业务编号，唯一约束兜底。
2. 多部门：员工表单用多部门选择（主部门单选 + 其他部门可选，各带职级）。
3. 职级默认角色：职级绑默认角色，员工自动继承；职级变化只撤销旧职级自动角色，不影响手动角色（`assignment_source` 区分）。
4. 授权来源追踪：角色绑定/授权记录来源与批次。
5. DENY：角色/人员授权支持 ALLOW/DENY 两栏。
6. 应用注册中心 DB 持久化（启动回读、CI token 只存 hash、SSO 落库）。
7. AOP 审计：`@AuditLog` 注解 + 切面，记录操作者/动作/资源/结果/来源 IP/请求摘要 + 前后 diff，覆盖登录/锁定/授权变更/组织变更/资源菜单变更/同步重试/事件解密。

## 8. 企业微信同步 / 事件状态机（Phase 3）

1. 全量/增量/定时校准三态，共用幂等 upsert。
2. 同步批次：`PENDING → RUNNING → SUCCESS/PARTIAL/FAILED`；单条失败记 `PARTIAL`，不批量停用未返回员工。
3. 事件：验签→AES 解密→幂等键→原文落库（不加密）→快速返回→异步处理→回源详情→统一 upsert。
4. 事件状态机：`RECEIVED → PROCESSING → SUCCESS / RETRY_WAIT / DEAD_LETTER`；指数退避（30s×2^n，上限 15min，jitter，最多 5 次）。
5. 乱序容忍：`update_user` 找不到身份时回源并按创建流程；`delete_user` 找不到身份时记不可匹配事件；`last_synced_at` 旧数据不覆盖新数据。
6. 重试/死信/人工重试接口 `POST /integrations/wecom/events/{id}/retry`（仅失败或死信）。
7. 监控指标：`sync_run_*`、`sync_event_*`、`wecom_api_error_by_errcode` 等。

## 9. 前端重建（Phase 4）

### 9.1 架构

- 菜单后端化：Sidebar 从后端 `/me/menus` + `/me/apps` 渲染，删除写死的 `menuGroups`。
- 应用切换器、面包屑、用户菜单（含退出）。
- 工作台丰富化：常用应用 / 最近访问 / 待办 / 系统状态 / 权限摘要。
- 拆分专用页面：`EmployeesPage` / `DepartmentsPage` / `JobGradesPage` / `RolesPage` / `ResourcesPage`，去掉 `ResourcePage` 的实体分支。
- 表单：抽屉式（查看/新增/编辑三态）、关闭前未保存确认、URL 筛选与分页状态保持、服务端字段错误。

### 9.2 页面清单

- 员工：多部门选择、手机号/邮箱、企微状态、角色数/直接权限数、最近登录、权限来源、审计入口。
- 部门：左树右详情，移动、负责人、员工列表、继承权限。
- 职级：默认角色、企微职位映射、关联员工、员工数。
- 角色：应用范围、ALLOW/DENY 资源树、绑定人员/部门/职级、复制、停用、反查被谁使用、变更前后差异预览。
- 资源：树 + 详情抽屉、新增子资源、移动、拖拽排序、跨应用禁止、查看绑定角色。
- 应用中心：卡片（Logo/名称/描述/类型/状态/负责人/版本/最近访问/入口）+ 分组；详情 Tabs（概览/资源/成员/权限/版本/认证/审计）；注册表单 CI token 仅创建时展示一次。
- 企业微信：配置（脱敏 CorpID/AgentID/回调地址/连接检查）、同步批次、事件详情、失败重试、差异预览、原始数据仅审计权限可见。
- 文件中心：目录树 + 文件表格 + 类型/状态筛选 + 空间容量 + 权限来源。
- 审计：筛选（人员/应用/资源/动作/时间）+ 列表 + 详情抽屉（请求摘要 + 前后 diff）+ 导出。

### 9.3 前端审查发现（全部并入，28 条）

**高（4）**：`visible` 用 status 类型提交字符串给 Boolean 字段（`resourceDefinitions.ts:11`）；`code/employeeNo/permissionCode` 隐藏但后端必填（`resourceDefinitions.ts:7-11`，随 Phase 2 编码自动生成一并解决）；职级 create 权限码 `rbac:job-grades:create` 应为 `job-grades:create`（`resourceDefinitions.ts:9`）；`wecom:config:view`/`audit:view` 后端不存在致菜单不可见（`menu.ts:17,19`，随菜单后端化一并解决）。

**中（8）**：`page_size`/`pageSize` 参数名不匹配致分页回落 20（`resourceApi.ts:8`）；`App.css` 重复冲突样式（`:1` 与 `:6-59`）；Dashboard 栅格无响应式（`DashboardPage.tsx:13`）；应用 `baseUrl` 无省略（`ApplicationsPage.tsx:10`）；资源层级树用当页数据（`ResourcePage.tsx:64,82`）；Dashboard 无 error 态；Wecom 失败永久转圈（`WecomPage.tsx:13,49`）；详情弹窗未用 `labelFor` 显示原始 ID（`ResourcePage.tsx:88`）。

**低（16）**：eyebrow 全大写英文标题混杂；硬编码「管理员/实时数据」；`queryKeys`/`cn`/`request`/`definitionFor` 死代码；Tailwind/clsx/tailwind-merge 未使用；死 CSS 与未用变量；两套建树函数重复；资源父级选项缓存过期；4 个未用 npm 依赖；长列无 ellipsis；登录页死按钮；AuditPage 纯占位（随 Phase 2 重写解决）。

## 10. 分阶段实施与验收

| 阶段 | 内容 | 验收 |
|---|---|---|
| Phase 0 | 数据模型地基（§4 全部迁移 + 实体） | `mvn verify` 通过，Flyway 迁移可执行 |
| Phase 1 | 认证 + 权限核心（§5） | 真实登录、demo 移除、`/me/*`、未登记 API 默认拒绝 |
| Phase 2 | 组织/RBAC/应用/审计后端闭环（§7） | 编码自动生成、多部门、DENY、应用持久化、审计落库 |
| Phase 3 | 企微同步/事件完整状态机（§8） | 幂等/退避/死信/乱序/重试单测通过 |
| Phase 4 | 前端重建（§9） | `npm run lint` + `npm run build` 通过，逐页验收 |

每阶段收尾：前端 `npm run lint` + `npm run build`；后端 `mvn verify`（含 Checkstyle）；阶段 D 最终验收（服务启动、健康检查 UP、逐页检查、不联调 OSS/企微）。

## 11. 风险与待办

- 数据模型迁移影响面大（employee/department/job_grade/resource 加字段），需保证向后兼容与一次性迁移脚本正确。
- 职级默认角色与「多部门多职级」组合的权限计算边界需单测覆盖（同名资源多来源合并、DENY 覆盖）。
- 未登记 API 默认拒绝是安全硬约束，改造后需全量回归所有 Controller。
- 前端拆分专用页面工作量大，建议按「员工→部门→职级→角色→资源」顺序推进，每页可独立验收。
