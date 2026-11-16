# Plan 4：前端重建 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 前端全量重建：菜单后端化、应用切换、面包屑、工作台丰富化、拆分专用页面、抽屉表单、28 条样式/内容整改，消除假数据/JSON 详情/ID 手填。

**Architecture:** 删除写死的 `menuGroups` 与通用 `ResourcePage`，改为后端菜单 + 逐实体专用页面。共享 `PageShell`/`DataTable`/`EntityDrawer` 三个组件承载统一页面状态与抽屉表单。

**Tech Stack:** React 19、Arco Design、TanStack Query、React Router 7、TypeScript、Vite。

**Spec:** `docs/superpowers/specs/2026-11-16-seeex-console-repair-design.md`

## Global Constraints

- 依赖后端接口：`/me/menus`、`/me/apps`、`/me/roles`、`/me/resources`、`/me/data-scopes`、`/audit-logs`、`/integrations/wecom/*`、`/files`（Plan 1/2/3 提供）。
- 全部用 Arco 组件，禁止手写基础控件；分页入参 `page/page_size`，响应 `{ items, total }`。
- 登录入参 `{ phone, password }`，token 读取 `access_token`（ADR-9）。
- 每个页面必须含 loading/empty/error/forbidden 四态；抽屉表单含未保存确认；URL 保存筛选与分页状态。
- 验证命令：`npm run lint` + `npm run build`（无单测框架）。

---

## File Structure

**Modify:**
- `src/layouts/Sidebar.tsx`、`src/layouts/Header.tsx`、`src/routes/router.tsx`
- `src/config/menu.ts`（删除或改造为后端菜单类型）
- `src/features/dashboard/DashboardPage.tsx`、`applications/ApplicationsPage.tsx`、`wecom/WecomPage.tsx`、`files/FilesPage.tsx`、`audit/AuditPage.tsx`
- `src/App.css`、`src/index.css`

**Create:**
- `src/components/PageShell.tsx`、`DataTable.tsx`、`EntityDrawer.tsx`
- `src/features/employees/EmployeesPage.tsx` + `employeesApi.ts`
- `src/features/departments/DepartmentsPage.tsx` + `departmentsApi.ts`
- `src/features/jobGrades/JobGradesPage.tsx` + `jobGradesApi.ts`
- `src/features/roles/RolesPage.tsx` + `rolesApi.ts`
- `src/features/resources/ResourcesPage.tsx` + `resourcesApi.ts`
- `src/features/applications/ApplicationDetailDrawer.tsx`

**Delete:**
- `src/features/resources/ResourcePage.tsx`（拆为五页后删除）

---

### Task 1: 菜单后端化 + 应用切换 + 面包屑

**Files:**
- Modify: `src/layouts/Sidebar.tsx`、`src/layouts/Header.tsx`、`src/config/menu.ts`
- Modify: `src/lib/api.ts`

**Interfaces:**
- Consumes: `GET /me/menus`（返回 `MenuView[]`）、`GET /me/apps`（返回 `AppView[]`）。
- Produces: `Sidebar` 渲染后端菜单树（系统平台分组 + 已接入应用分组），删除 `menuGroups`；`Header` 增加面包屑（从 `location` 派生）与用户菜单（`/me` 员工名 + 退出）。

- [ ] **Step 1: 实现菜单后端化**

`Sidebar`：用 `useQuery` 拉 `/me/menus`（系统菜单）与 `/me/apps`（应用切换），构造两级菜单；删除 `import { menuGroups }`。`api.ts` 新增类型 `MenuView`、`AppView`。`Header` 用 `useLocation` 生成面包屑，`Dropdown` 用户菜单显示 `/me` 的 `displayName`，退出调 `clearAccessToken`。

- [ ] **Step 2: lint + build**

Run: `cd seeex-tech-ui && npm run lint && npm run build`
Expected: 通过。

- [ ] **Step 3: Commit**

```bash
git add seeex-tech-ui/src/layouts seeex-tech-ui/src/config/menu.ts seeex-tech-ui/src/lib/api.ts
git commit -m "feat: backend-driven menu, app switcher and breadcrumb"
```

---

### Task 2: 工作台丰富化

**Files:**
- Modify: `src/features/dashboard/DashboardPage.tsx`

**Interfaces:**
- Consumes: `/me`（当前用户 + 权限摘要）、`/me/apps`（常用应用）、`/platform/apps`（应用数）。
- Produces: 工作台 = 当前用户卡 + 常用应用卡 + 最近访问（本地存储）+ 系统状态（企微 config + 文件/员工/部门统计）+ 权限摘要。

- [ ] **Step 1: 实现工作台**

`DashboardPage`：删除硬编码 `<Tag color="green">实时数据</Tag>`；新增响应式 `Grid.Col xs={24} sm={12} md={8}`；用 `/me` 显示员工名 + 角色 + 有效权限数；`/me/apps` 显示应用入口卡；`localStorage` 记录最近访问；员工/部门总数用 `page_size=1` 取 `total`。加 error 态 + 重试。

- [ ] **Step 2: lint + build**

Run: `cd seeex-tech-ui && npm run lint && npm run build`
Expected: 通过。

- [ ] **Step 3: Commit**

```bash
git add seeex-tech-ui/src/features/dashboard/DashboardPage.tsx
git commit -m "feat: enrich dashboard with apps, recent access and permission summary"
```

---

### Task 3: 通用页面脚手架 PageShell / DataTable / EntityDrawer

**Files:**
- Create: `src/components/PageShell.tsx`、`DataTable.tsx`、`EntityDrawer.tsx`

**Interfaces:**
- Produces: `PageShell({ title, description, breadcrumb, actions, loading, error })`；`DataTable({ columns, query, filters, rowKey, selection, pagination, onPageChange })` 内置 loading/empty/error/分页；`EntityDrawer({ mode, title, fields, initialValues, onSubmit, onCancel, loading })` 支持查看/新增/编辑三态 + 未保存确认 + 服务端字段错误。

- [ ] **Step 1: 实现三个组件**

`PageShell` 用 Arco `Card` 包裹页头 + 操作区；`DataTable` 用 `Table` + `Pagination` + `Empty` + `Spin`；`EntityDrawer` 用 `Drawer` + `Form`，`mode==='view'` 时只读。表单字段渲染统一走 `fields` 配置（`type: 'text'|'number'|'select'|'treeSelect'|'switch'`），`visible` 用 `Switch`（Boolean）。

- [ ] **Step 2: lint + build**

Run: `cd seeex-tech-ui && npm run lint && npm run build`
Expected: 通过。

- [ ] **Step 3: Commit**

```bash
git add seeex-tech-ui/src/components/PageShell.tsx seeex-tech-ui/src/components/DataTable.tsx seeex-tech-ui/src/components/EntityDrawer.tsx
git commit -m "feat: shared page shell, data table and entity drawer components"
```

---

### Task 4: EmployeesPage

**Files:**
- Create: `src/features/employees/EmployeesPage.tsx`、`employeesApi.ts`
- Modify: `src/routes/router.tsx`

**Interfaces:**
- Consumes: `/employees`（列表/详情/增删改）、`/departments`（部门树选项）、`/job-grades`（职级选项）、`/rbac/employees/{id}/permissions`。
- Produces: 员工页：多部门选择（部门树 + 各带职级）、手机号/邮箱、状态（ACTIVE/DISABLED/LEFT/LOCKED）、来源、权限来源入口。

- [ ] **Step 1: 实现员工页**

字段：`employeeNo`（只读，系统生成）、`displayName`（必填）、`internalMobile`/`internalEmail`、`memberships`（多部门 + 主部门 + 各带职级）、`status`（四态 Select）、`source`（只读）。行操作：详情/编辑/权限/删除。列表列：工号/姓名/部门/职级/状态/来源，加 `ellipsis`。

- [ ] **Step 2: lint + build**

Run: `cd seeex-tech-ui && npm run lint && npm run build`
Expected: 通过。

- [ ] **Step 3: Commit**

```bash
git add seeex-tech-ui/src/features/employees seeex-tech-ui/src/routes/router.tsx
git commit -m "feat: dedicated employees page with multi-department and status"
```

---

### Task 5: DepartmentsPage

**Files:**
- Create: `src/features/departments/DepartmentsPage.tsx`、`departmentsApi.ts`
- Modify: `src/routes/router.tsx`

**Interfaces:**
- Consumes: `/departments`（列表/树）、`/departments/{id}/employees`（员工列表，Plan 2 提供）。
- Produces: 部门页：左侧部门树 + 右侧详情与员工列表；移动、负责人、继承权限。

- [ ] **Step 1: 实现部门页**

左 `Tree`（全量 `page_size=500` 构建完整树，不截断）+ 右详情（名称/编码/负责人/状态 + 员工表格）；新增子部门、移动、编辑负责人；禁止选自身/子孙为上级（前端校验）。

- [ ] **Step 2: lint + build**

Run: `cd seeex-tech-ui && npm run lint && npm run build`
Expected: 通过。

- [ ] **Step 3: Commit**

```bash
git add seeex-tech-ui/src/features/departments seeex-tech-ui/src/routes/router.tsx
git commit -m "feat: dedicated departments page with tree and member list"
```

---

### Task 6: JobGradesPage

**Files:**
- Create: `src/features/jobGrades/JobGradesPage.tsx`、`jobGradesApi.ts`
- Modify: `src/routes/router.tsx`

**Interfaces:**
- Consumes: `/job-grades`（列表/增删改）、`/rbac/roles`（默认角色选项）。
- Produces: 职级页：默认角色绑定、企微职位映射、关联员工数、等级值校验。

- [ ] **Step 1: 实现职级页**

字段：`code`（只读）、`name`（必填）、`levelValue`（InputNumber + 唯一校验）、`defaultRoleId`（角色 Select）、`status`。列表列：名称/编码/等级/默认角色/员工数/状态。权限码修正：create 用 `job-grades:create`（非 `rbac:job-grades:create`）。

- [ ] **Step 2: lint + build**

Run: `cd seeex-tech-ui && npm run lint && npm run build`
Expected: 通过。

- [ ] **Step 3: Commit**

```bash
git add seeex-tech-ui/src/features/jobGrades seeex-tech-ui/src/routes/router.tsx
git commit -m "feat: dedicated job grades page with default role binding"
```

---

### Task 7: RolesPage

**Files:**
- Create: `src/features/roles/RolesPage.tsx`、`rolesApi.ts`
- Modify: `src/routes/router.tsx`

**Interfaces:**
- Consumes: `/rbac/roles`（列表/增删改）、`/rbac/roles/{id}/grants`、`/rbac/roles/{id}/grants/diff`（Plan 2）、`/rbac/assignments/*`。
- Produces: 角色页：应用范围、ALLOW/DENY 资源树、绑定人员/部门/职级、复制、反查、差异预览。

- [ ] **Step 1: 实现角色页**

列表列：名称/编码/资源数/人员数/状态。配置权限用 `PermissionTree`（应用切换 + 资源树 + 每个节点 ALLOW/DENY/继承标记 + 全选 + 搜索）；保存前调 `grants/diff` 显示变更前后差异；绑定人员/部门/职级用多选；支持复制角色。

- [ ] **Step 2: lint + build**

Run: `cd seeex-tech-ui && npm run lint && npm run build`
Expected: 通过。

- [ ] **Step 3: Commit**

```bash
git add seeex-tech-ui/src/features/roles seeex-tech-ui/src/routes/router.tsx
git commit -m "feat: dedicated roles page with allow/deny tree and diff preview"
```

---

### Task 8: ResourcesPage

**Files:**
- Create: `src/features/resources/ResourcesPage.tsx`、`resourcesApi.ts`
- Modify: `src/routes/router.tsx`

**Interfaces:**
- Consumes: `/rbac/resources`（列表/树/增删改）、`/platform/apps`（应用选项）。
- Produces: 资源页：树 + 详情抽屉、新增子资源、移动、拖拽排序、跨应用禁止、查看绑定角色；`visible` 用 Switch。

- [ ] **Step 1: 实现资源页**

左侧应用筛选 + 资源树（全量 `page_size=500`，非当页数据）；右侧详情抽屉（7 类资源、父资源 TreeSelect 按应用过滤、`visible` Switch、`icon`/`routePath`/`sortOrder`）；跨应用父资源前端拦截；查看绑定角色。修复 `visible` 从 `status` 类型改为 Boolean。

- [ ] **Step 2: lint + build**

Run: `cd seeex-tech-ui && npm run lint && npm run build`
Expected: 通过。

- [ ] **Step 3: Commit**

```bash
git add seeex-tech-ui/src/features/resources seeex-tech-ui/src/routes/router.tsx
git commit -m "feat: dedicated resources page with tree and visible switch"
```

---

### Task 9: ApplicationsPage（应用中心）

**Files:**
- Modify: `src/features/applications/ApplicationsPage.tsx`
- Create: `src/features/applications/ApplicationDetailDrawer.tsx`

**Interfaces:**
- Consumes: `/platform/apps`（列表/注册）、`/platform/apps/{code}/route-metadata/*`（版本/发布/审核/灰度）。
- Produces: 应用中心：卡片（Logo/名称/描述/类型/状态/负责人/版本）+ 分组；详情 Tabs（概览/资源/成员/权限/版本/认证/审计）；注册表单 CI token 仅创建时展示一次。

- [ ] **Step 1: 实现应用中心**

卡片网格 `Grid.Col xs={24} sm={12} lg={8}`，卡片含状态 Tag + 版本 Tag + 负责人 + 入口按钮；详情 `Drawer` 内 Tabs；注册 `Modal` 提交后展示 `ciToken`（复制按钮 + 「仅此一次」提示）。`baseUrl` 列加 `showOverflowTooltip`。

- [ ] **Step 2: lint + build**

Run: `cd seeex-tech-ui && npm run lint && npm run build`
Expected: 通过。

- [ ] **Step 3: Commit**

```bash
git add seeex-tech-ui/src/features/applications
git commit -m "feat: application center with cards, detail tabs and one-time CI token"
```

---

### Task 10: WecomPage（配置/同步/事件三页）

**Files:**
- Modify: `src/features/wecom/WecomPage.tsx`

**Interfaces:**
- Consumes: `/wecom/config`、`/integrations/wecom/sync/full`、`/integrations/wecom/sync/runs/*`、`/integrations/wecom/events/*`（Plan 3）。
- Produces: 三页/三 Tab：配置（脱敏 CorpID/AgentID/回调地址/连接检查/登录开关）、同步批次列表、事件列表（类型/状态/幂等号/错误/重试；原始数据仅审计权限可见）。

- [ ] **Step 1: 实现企微三页**

用 `Tabs` 分「连接配置 / 通讯录同步 / 事件回调」。配置页展示脱敏字段；同步页列表 `sync_run`（状态 Tag + 成功/失败数 + 重试按钮）；事件页列表 `sync_event`（幂等号/状态/重试次数/错误 + 重试）。修复 `config` 失败永久转圈：加 error 态 + 重试。

- [ ] **Step 2: lint + build**

Run: `cd seeex-tech-ui && npm run lint && npm run build`
Expected: 通过。

- [ ] **Step 3: Commit**

```bash
git add seeex-tech-ui/src/features/wecom/WecomPage.tsx
git commit -m "feat: wecom config, sync runs and event tabs with retry"
```

---

### Task 11: FilesPage（目录树 + 容量 + 权限来源）

**Files:**
- Modify: `src/features/files/FilesPage.tsx`、`fileApi.ts`

**Interfaces:**
- Consumes: `/files`（列表/上传/下载/删除）。
- Produces: 文件页：目录树 + 文件表格 + 类型/状态筛选 + 上传进度 + 空间容量 + 权限来源；`fileName` 加 ellipsis。

- [ ] **Step 1: 实现文件页**

左侧目录树 + 右侧表格（文件名/类型/大小/上传人/应用/状态/时间）；类型与状态筛选；上传进度条；批量删除确认；下载/删除权限按 `files:delete`/`files:view` 门控；`fileName` 列 `ellipsis`。

- [ ] **Step 2: lint + build**

Run: `cd seeex-tech-ui && npm run lint && npm run build`
Expected: 通过。

- [ ] **Step 3: Commit**

```bash
git add seeex-tech-ui/src/features/files
git commit -m "feat: file center with directory tree, filter and ellipsis"
```

---

### Task 12: AuditPage（筛选 + 详情 + 导出）

**Files:**
- Modify: `src/features/audit/AuditPage.tsx`

**Interfaces:**
- Consumes: `/audit-logs`（列表/详情，Plan 2）。
- Produces: 审计页：筛选（人员/应用/资源/动作/时间范围）+ 列表（操作者/动作/目标/结果/来源 IP/时间）+ 详情抽屉（请求摘要 + 前后 diff）+ 导出。

- [ ] **Step 1: 实现审计页**

筛选区 + `Table`（操作者/动作/资源/结果/来源 IP/时间）；详情 `Drawer` 展示 `request_summary` + `before_json`/`after_json` diff（不用原始 JSON 直接展示，做字段级对比渲染）；导出 CSV 按钮。

- [ ] **Step 2: lint + build**

Run: `cd seeex-tech-ui && npm run lint && npm run build`
Expected: 通过。

- [ ] **Step 3: Commit**

```bash
git add seeex-tech-ui/src/features/audit/AuditPage.tsx
git commit -m "feat: audit page with filters, detail drawer and export"
```

---

### Task 13: 样式 / 文案 / 状态整改（28 条）

**Files:**
- Modify: `src/App.css`、`src/index.css`、各页面（eyebrow 文案、ellipsis、error 态）
- Modify: `src/lib/utils.ts`、`src/lib/queryKeys.ts`、`package.json`（删死代码/依赖）

**Interfaces:**
- 无新增接口；纯整改。

- [ ] **Step 1: 高优先整改（4 条）**

① `visible` 字段改 Boolean（Task 8 已含）；② `code/employeeNo/permissionCode` 隐藏问题随后端自动生成解决（前端标记为只读不提交）；③ 职级 create 权限码改 `job-grades:create`（Task 6 已含）；④ `wecom:config:view`/`audit:view` 权限码随菜单后端化一并解决（Task 1 已含）。

- [ ] **Step 2: 中优先整改（8 条）**

`page_size` 参数名统一 `pageSize`（后端已读 `pageSize`）；`App.css` 删第 1-4 行压缩重复块，保留展开版；Dashboard 栅格加响应式断点；应用 `baseUrl` 加 ellipsis；资源/部门层级树用全量数据；Dashboard/Wecom 加 error 态；详情弹窗复用 `labelFor` 显示业务名。

- [ ] **Step 3: 低优先整改（16 条）**

eyebrow 全大写英文改统一中文（或统一规范）；删「管理员/实时数据」硬编码；删 `queryKeys`/`cn`/`request`/`definitionFor` 死代码；删未用 Tailwind/clsx/tailwind-merge（保留则移除依赖）；删死 CSS 与未用变量；合并两套建树函数；资源父级选项纳入 queryKey；移除 4 个未用 npm 依赖（`@hookform/resolvers`/`react-hook-form`/`zod`/`class-variance-authority`）；长列加 ellipsis；登录页死按钮加 tooltip 或移除；`AuditPage` 占位已由 Task 12 替换。

- [ ] **Step 4: lint + build + 全量回归**

Run: `cd seeex-tech-ui && npm run lint && npm run build`
Expected: 通过，无类型错误。

- [ ] **Step 5: Commit**

```bash
git add seeex-tech-ui/src
git commit -m "style: unify copy, ellipsis, states and remove dead code"
```

---

## Self-Review

- **Spec 覆盖**：§9.1→Task 1/2/3；§9.2 员工→Task 4、部门→Task 5、职级→Task 6、角色→Task 7、资源→Task 8、应用中心→Task 9、企微→Task 10、文件→Task 11、审计→Task 12；§9.3 28 条→Task 13（高/中/低全列）。
- **占位符扫描**：无 TBD；每 task 含组件职责、API 端点、验收命令。
- **类型一致性**：`MenuView`/`AppView` Task 1 定义并被 Task 2 引用；`PageShell`/`DataTable`/`EntityDrawer` props Task 3 定义并被 Task 4-12 引用；`employeesApi` 等命名按实体一致。
