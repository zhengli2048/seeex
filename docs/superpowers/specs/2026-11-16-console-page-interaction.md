# SEEEX Console 页面功能与交互详细清单

> 日期：2026-11-16
> 本轮开发以 [页面按钮与接口实施契约](../../plans/seeex-console-button-contract.md) 收敛参数和字段；本页保留完整产品目标。运行验收未执行。
> 范围：`seeex-tech-ui`（React 19 + Arco Design + TanStack Query + React Router 7）12 个页面的「功能 + 交互」逐页清单，**细化到控件级**。
> 依据：`2026-11-16-seeex-console-repair-design.md`、`seeex-console-page-spec.md`、`2026-11-16-seeex-console-repair-plan-4-frontend.md`、当前 `src/features/**` 与后端 Controller 实况。
> 标记：接口路径带 `（规划）` 的为前端重建时预留对接、后端尚未落地的端点；其余为已具备或本阶段计划落地。
> 结构：每页 = **功能表 + 按钮清单 + 弹窗/抽屉字段明细（含字段属性）+ 表格列清单**。字段属性统一内嵌在「弹窗/抽屉字段明细表」中（字段名/标签/控件/必填/校验/默认/只读），可直接照此写组件代码。

---

## 0. 全局交互规范（所有页面统一遵守）

| 维度 | 规范 |
|---|---|
| 表单形态 | 所有表单统一用**抽屉 Drawer**（非 Modal）。支持 `view`（查看，全字段只读）、`create`（新增）、`edit`（编辑）三态。 |
| 关闭保护 | 抽屉关闭前若表单有未保存变更（dirty），`Modal.confirm`「放弃未保存的修改？」确认后才关闭。 |
| 字段错误 | 服务端字段级错误（如「手机号已存在」「等级值重复」）映射到对应 `Form.Item` 的 `error`；非字段级错误（CONFLICT/RATE_LIMITED）在抽屉顶部 `Alert` 展示。 |
| 保存状态 | 提交按钮 `loading` + 全表单禁止二次提交；成功：`Message.success` + 关闭抽屉 + `invalidateQueries`；失败：保留抽屉与已填内容，仅展示错误。 |
| 列表分页 | Table 必须**服务端分页**（`page`/`pageSize`，响应 `{ items, total }`）+ 服务端筛选 + 明确默认排序。 |
| 列表三态 | loading → `Spin`；empty → `Empty`；error → 错误块 + 重试；forbidden → 无权限占位；页头固定「刷新」。 |
| URL 状态 | 筛选、分页、选中的应用/节点等写入 URL query，刷新/回退可恢复。 |
| 按钮层级 | 主操作 `type="primary"`；次操作默认；危险 `status="danger"`；行内 `type="text"` + `Tooltip`。 |
| 二次确认 | 删除/批量/停用/重试/发布/回滚等写操作前 `Modal.confirm`/`Popconfirm`，文案含影响条数。 |
| 长文本 | 文件名、URL、名称、路径、描述列统一 `ellipsis` + `Tooltip`。 |
| 关联选择 | 关联对象一律远程选择器（`Select showSearch`/`TreeSelect`/`Cascader`），展示 `name + code`，禁止手填 ID。 |
| 权限门控 | 页面级（无 `*:view` → forbidden）+ 操作级（按钮按权限码渲染/禁用）；后端独立鉴权为准。 |
| 错误处理 | axios 拦截器 + `getApiMessage`：403 → 「暂无权限执行此操作」；401 → 自动 refresh，失败跳登录；网络失败 → error 态 + 重试。 |
| 详情展示 | 详情用业务对象 + 中文字段，禁止倾倒原始 JSON；前后差异用字段级 diff 渲染。 |

### 0.1 权限码总览

| 域 | view | create | update | delete | 专项 |
|---|---|---|---|---|---|
| 员工 employees | `employees:view` | `employees:create` | `employees:update` | `employees:delete` | `employees:grant`（直接授权）、`employees:permissions`（权限分析） |
| 部门 departments | `departments:view` | `departments:create` | `departments:update` | `departments:delete` | — |
| 职级 job-grades | `job-grades:view` | `job-grades:create` | `job-grades:update` | `job-grades:delete` | — |
| 角色 rbac:roles | `rbac:roles:view` | `rbac:roles:create` | `rbac:roles:update` | `rbac:roles:delete` | `rbac:roles:assign`（绑定主体）、`rbac:roles:grant`（配置资源） |
| 资源 rbac:resources | `rbac:resources:view` | `rbac:resources:create` | `rbac:resources:update` | `rbac:resources:delete` | — |
| 应用 platform:apps | `platform:apps:view` | `platform:apps:create` | `platform:apps:update` | — | `platform:apps:publish`（发布/回滚）、`platform:apps:review`（审核/灰度）、`platform:apps:ci`（轮换 CI token） |
| 企业微信 wecom | `wecom:config:view` | — | `wecom:config:update` | — | `wecom:sync:view`、`wecom:sync:run`、`wecom:events:view`、`wecom:events:retry`、`wecom:events:raw`（仅审计/安全角色） |
| 审计 audit | `audit:view` | — | — | — | `audit:export` |
| 文件 files | `files:view` | `files:create` | — | `files:delete` | — |
| 工作台 | `platform:dashboard:view` | — | — | — | — |

---

## 1. 登录页

### 1.1 功能表

| 功能点 | 交互方式 | Arco 组件 | 状态处理 | 权限控制 | 数据来源 |
|---|---|---|---|---|---|
| 本地账号登录 | 输入手机号 + 密码，点击「登录」或回车提交 | `Form`、`Input`、`Input.Password`、`Button` | loading：按钮 loading + 禁用；error：顶部 `Alert`；成功跳 `redirectPath` | 公开 | `POST /api/auth/login`（`{phone,password}` → `access_token`/`refresh_token`/`expires_in`/员工摘要/`has_permission`） |
| 企业微信登录 | 点击按钮跳企微 OAuth，回调换会话进入控制台 | `Button`、`Icon` | 未配置企微：禁用 + `Tooltip`；失败：`Alert` | 公开 | `GET /api/auth/wecom/authorize` → `GET /api/auth/wecom/callback` |
| 登录态恢复 | 持有效 token 访问登录页自动跳工作台；refresh 失败清 token 停留 | — | 恢复中全屏 `Spin` | 公开 | 本地 `localStorage` |
| 登录失败分支 | 账号停用（DISABLED/LEFT/LOCKED）提示「账号不可用」；无权限提示「登录成功但暂无权限」 | `Alert`、`Result` | 按 `code`/`error_type` 分支 | 公开 | `POST /api/auth/login` 错误码 |
| 退出登录 | 用户菜单「退出」：调登出 + 清 token + 跳登录 | `Dropdown`、`Menu` | 登出中 loading | 已登录 | `POST /api/auth/logout` |

### 1.2 按钮清单

| 按钮 | 位置 | 图标 | 权限码 | 触发动作 |
|---|---|---|---|---|
| 登录 | 登录表单底部 | — | 公开 | `form.submit()` 提交 `{phone,password}` |
| 企业微信登录 | 「或」分隔线下方 | `IconUser` | 公开 | `window.location` 跳转 `/api/auth/wecom/authorize` |

### 1.3 登录表单字段明细（内联表单，非抽屉）

| 字段名 | 标签 | 控件 | 必填 | 校验/规则 | 默认值 | 只读/可空 |
|---|---|---|---|---|---|---|
| phone | 账号/手机号 | `Input`（size=large） | 是 | 必填，格式 `^1\d{10}$` | 空 | 可空 |
| password | 密码 | `Input.Password`（size=large） | 是 | 必填，长度 6–64 | 空 | 可空 |

> 无抽屉/弹窗、无表格。

---

## 2. 工作台

### 2.1 功能表

| 功能点 | 交互方式 | Arco 组件 | 状态处理 | 权限控制 | 数据来源 |
|---|---|---|---|---|---|
| 当前用户卡 | 姓名/工号/角色数/有效权限数；头像进用户菜单 | `Card`、`Avatar`、`Statistic`、`Tag` | loading `Skeleton`；error 卡片内重试 | `platform:dashboard:view` | `GET /api/me`、`GET /api/permissions` |
| 常用应用 | 应用入口卡（Logo/名称/入口），点击跳转（跨应用走 SSO） | `Card`、`Grid`、`Button` | empty `Empty` | `platform:dashboard:view` | `GET /api/me/apps`；跳转 `GET /api/sso/authorize` |
| 最近访问 | 最近页面/应用列表，点击直达 | `List`、`Timeline`、`Tag` | empty `Empty` | `platform:dashboard:view` | 前端 `localStorage` |
| 待办事项 | 待审核版本/死信事件/同步冲突等，点击跳转 | `List`、`Badge`、`Tag` | 三态；error 单独重试 | `platform:dashboard:view` | `GET /api/me/todos`（规划） |
| 系统状态 | 企微连接/最近同步/系统健康状态点 | `Statistic`、`Tag`、`Badge` | 未配置显示「未配置」非报错 | `platform:dashboard:view` | `GET /api/wecom/config`、`GET /api/integrations/wecom/sync/runs` |
| 权限摘要 | 有效权限数 + 来源分布（员工/部门/职级） | `Statistic`、`Descriptions`、`Tag` | loading `Spin` | `platform:dashboard:view` | `GET /api/permissions` |
| 快捷统计 | 员工/部门/应用总数 | `Statistic`、`Grid`（响应式） | 三态；0 显示 0 | `platform:dashboard:view` | `GET /api/employees?page=1&page_size=1` 等 |

### 2.2 按钮清单

| 按钮 | 位置 | 图标 | 权限码 | 触发动作 |
|---|---|---|---|---|
| 应用入口 | 常用应用卡内 | `IconRight` | `platform:dashboard:view` | 跳转应用（SSO `GET /api/sso/authorize`） |
| 刷新 | 系统状态卡 | `IconRefresh` | `platform:dashboard:view` | 重新拉取各状态接口 |

> 工作台无抽屉、无 Table（统计卡 + 列表卡片）。

---

## 3. 员工管理

### 3.1 功能表

| 功能点 | 交互方式 | Arco 组件 | 状态处理 | 权限控制 | 数据来源 |
|---|---|---|---|---|---|
| 员工列表 | 服务端分页 + 关键字/状态筛选；行点击打开详情 | `Table`、`Input.Search`、`Select`、`Pagination`、`Tag` | 三态 + 刷新；长列 `ellipsis`；URL 保存 | `employees:view` | `GET /api/employees` |
| 新增员工 | 抽屉表单，提交刷新 | `Drawer`、`Form`、`TreeSelect`、`Select`、`Radio` | dirty 确认；saving 防重复；字段错误映射 | `employees:create` | `POST /api/employees` |
| 编辑员工 | 抽屉复用，工号/来源只读 | `Drawer`、`Form`、`Descriptions` | 同新增 | `employees:update` | `PUT /api/employees/{id}` |
| 员工详情 | 抽屉查看态 + 权限来源入口 | `Drawer`、`Descriptions`、`Table`、`Tag` | 只读 | `employees:view` | `GET /api/employees/{id}` |
| 多部门选择 | 每行=部门 TreeSelect + 职级 Select + 主部门 Radio，可增删行 | `TreeSelect`、`Select`、`Radio`、`Button` | 主部门至多一个；部门重复校验 | create/update | `GET /api/departments?page_size=500`、`GET /api/job-grades?page_size=500` |
| 状态管理 | 四态下拉/行内切换，停用前二次确认 | `Select`、`Tag`、`Popconfirm` | 成功 Message + 刷新 | `employees:update` | `PUT /api/employees/{id}` |
| 批量删除 | 勾选多行 → 批量删除 → 确认 | `Table` 行选、`Modal.confirm` | 显示选中数 | `employees:delete` | `DELETE /api/employees` |
| 角色绑定 | 抽屉多选角色，记录来源 MANUAL | `Drawer`、`Select mode="multiple"`、`Tag` | dirty 确认 | `rbac:roles:assign` | `GET/PUT /api/rbac/assignments/EMPLOYEE/{id}` |
| 直接授权 | 应用选择 + 资源树 ALLOW/DENY | `Drawer`、`Select`、`Tree`、`Tag` | 按应用隔离；差异预览 | `employees:grant` | `GET/PUT /api/rbac/employees/{id}/grants` |
| 最终权限分析 | 跳转权限分析页 | 跳转 | 携带 employeeId | `employees:permissions` | `GET /api/rbac/employees/{id}/permissions` |
| 审计入口 | 跳审计页按该员工过滤 | `Button type="text"` | 携带 `operator` | `audit:view` | `GET /api/audit-logs?operator=` |

### 3.2 按钮清单

| 按钮 | 位置 | 图标 | 权限码 | 触发动作 |
|---|---|---|---|---|
| 新增员工 | 页头 | `IconPlus` | `employees:create` | 打开新增抽屉（create） |
| 刷新 | 页头 | `IconRefresh` | `employees:view` | `refetch` 列表 |
| 批量删除 | 工具栏 | `IconDelete` | `employees:delete` | 二次确认后 `DELETE /api/employees` |
| 详情 | 行内 | `IconEye` | `employees:view` | 打开详情抽屉（view） |
| 编辑 | 行内 | `IconEdit` | `employees:update` | 打开编辑抽屉（edit） |
| 角色 | 行内 | `IconUserGroup` | `rbac:roles:assign` | 打开角色绑定抽屉 |
| 权限 | 行内 | `IconKey` | `employees:grant` | 打开直接授权抽屉 |
| 权限分析 | 行内 | `IconSearch` | `employees:permissions` | 跳权限分析页 |
| 删除 | 行内 | `IconDelete`（danger） | `employees:delete` | 二次确认删除 |

### 3.3 弹窗/抽屉字段明细

**3.3.1 员工新增/编辑抽屉**（触发：新增/编辑按钮；查看态复用只读）

| 字段名 | 标签 | 控件 | 必填 | 校验/规则 | 默认值 | 只读/可空 |
|---|---|---|---|---|---|---|
| employeeNo | 工号 | `Input` | 否 | — | 系统生成 | 只读，不提交 |
| displayName | 姓名 | `Input` | 是 | 长度 1–64 | 空 | — |
| internalMobile | 手机号 | `Input` | 否 | `^1\d{10}$`，服务端唯一 | 空 | 可空 |
| internalEmail | 邮箱 | `Input` | 否 | email 格式 | 空 | 可空 |
| memberships[].departmentId | 所属部门 | `TreeSelect showSearch` | 是（行内） | 部门重复校验 | 空 | — |
| memberships[].jobGradeId | 该部门职级 | `Select showSearch` | 否 | — | 空 | 可空 |
| memberships[].primary | 主部门 | `Radio`（每行单选） | 是 | 全表至多一个 primary | 首行 true | — |
| status | 状态 | `Select` | 是 | 四态枚举 | `ACTIVE` | — |
| source | 来源 | `Tag`/`Input` | 否 | — | `LOCAL` | 只读 |

- 抽屉按钮：提交（`primary`，`loading`=saving）/ 取消；dirty 取消二次确认；服务端字段错误回填对应 `Form.Item.error`，非字段错误顶部 `Alert`。

**3.3.2 角色绑定抽屉**（触发：行内「角色」）

| 字段名 | 标签 | 控件 | 必填 | 校验 | 默认值 | 只读 |
|---|---|---|---|---|---|---|
| roleIds | 角色 | `Select mode="multiple" showSearch` | 否 | — | 当前已绑角色 | — |

**3.3.3 直接授权抽屉**（触发：行内「权限」）

| 字段名 | 标签 | 控件 | 必填 | 校验 | 默认值 | 只读 |
|---|---|---|---|---|---|---|
| applicationCode | 应用边界 | `Select showSearch` | 是 | — | 空 | — |
| allowIds / denyIds | 允许/拒绝资源 | `Tree`（checkable，ALLOW/DENY 两栏） | 否 | — | 当前 grants | — |

**3.3.4 删除确认**（`Modal.confirm`，触发：删除/批量删除）。

### 3.4 表格列清单

| 列名 | 数据字段 | 排序 | 筛选 | 省略 |
|---|---|---|---|---|
| 工号 | `employeeNo` | 可（created_at 兜底） | — | 否 |
| 姓名 | `displayName` | — | — | 否 |
| 部门 | `departmentName`（主部门） | — | — | 是 |
| 职级 | `jobGradeName` | — | — | 否 |
| 手机号 | `internalMobile` | — | — | 否 |
| 邮箱 | `internalEmail` | — | — | 是 |
| 状态 | `status` | — | Select（四态） | 否 |
| 来源 | `source` | — | Select（LOCAL/WECOM） | 否 |
| 角色数 | `roleCount` | 可 | — | 否 |
| 最近登录 | `lastLoginAt` | 可（默认倒序） | — | 否 |
| 操作 | — | — | — | — |

---

## 4. 部门管理

### 4.1 功能表

| 功能点 | 交互方式 | Arco 组件 | 状态处理 | 权限控制 | 数据来源 |
|---|---|---|---|---|---|
| 部门树（左） | 全量树，节点展开收起，点击联动右侧 | `Tree`、`Spin`、`Badge` | 三态；刷新重载全量 | `departments:view` | `GET /api/departments?page_size=500` |
| 部门详情（右） | 名称/编码/负责人/状态 + 操作 | `Descriptions`、`Card`、`Tag` | 未选中 `Empty` | `departments:view` | `GET /api/departments/{id}` |
| 新增根/子部门 | 抽屉，子部门自动带入父节点 | `Drawer`、`Form`、`TreeSelect` | dirty 确认；保存刷新树 | `departments:create` | `POST /api/departments` |
| 移动部门 | 选新上级，禁自身/子孙 | `Drawer`、`TreeSelect` | 非法上级禁用 + 拦截 | `departments:update` | `PUT /api/departments/{id}` |
| 编辑负责人 | 远程员工选择，仅组织属性 | `Drawer`、`Select showSearch` | saving 防重复 | `departments:update` | `PUT /api/departments/{id}` |
| 员工列表（右） | 当前部门员工表格 | `Table`、`Pagination`、`Tag` | 分页三态 | `departments:view` | `GET /api/departments/{id}/employees`（规划） |
| 绑定角色 | 抽屉多选角色（部门角色） | `Drawer`、`Select mode="multiple"` | dirty 确认 | `rbac:roles:assign` | `GET/PUT /api/rbac/assignments/DEPARTMENT/{id}` |
| 继承权限 | 展示本部门及子部门继承角色/权限 | `Descriptions`、`Table`、`Tag` | 三态 | `departments:view` | `GET /api/rbac/assignments/DEPARTMENT/{id}` + 聚合（规划） |
| 删除部门 | 有子/员工禁止删；二次确认 | `Popconfirm` | CONFLICT 提示 | `departments:delete` | `DELETE /api/departments` |

### 4.2 按钮清单

| 按钮 | 位置 | 图标 | 权限码 | 触发动作 |
|---|---|---|---|---|
| 新增部门 | 页头 | `IconPlus` | `departments:create` | 打开新增抽屉 |
| 新增子部门 | 页头/树节点 hover | `IconFolderAdd` | `departments:create` | 打开新增抽屉并带入父节点 |
| 刷新 | 页头 | `IconRefresh` | `departments:view` | 重载树 |
| 编辑 | 右侧详情 | `IconEdit` | `departments:update` | 打开编辑抽屉 |
| 移动 | 右侧详情/树节点 | `IconSwap` | `departments:update` | 打开移动抽屉 |
| 绑定角色 | 右侧详情 | `IconUserGroup` | `rbac:roles:assign` | 打开角色绑定抽屉 |
| 删除 | 树节点 | `IconDelete`（danger） | `departments:delete` | 二次确认删除 |

### 4.3 弹窗/抽屉字段明细

**4.3.1 新增/编辑部门抽屉**

| 字段名 | 标签 | 控件 | 必填 | 校验/规则 | 默认值 | 只读/可空 |
|---|---|---|---|---|---|---|
| code | 编码 | `Input` | 否 | — | 系统生成 | 只读 |
| name | 名称 | `Input` | 是 | 1–64 | 空 | — |
| parentId | 上级部门 | `TreeSelect showSearch` | 否 | 移动时禁自身/子孙 | 新增子部门带入 | 根部门可空 |
| leaderEmployeeId | 负责人 | `Select showSearch`（远程员工） | 否 | — | 空 | 可空 |
| status | 状态 | `Switch` | 是 | — | `true`（ACTIVE） | — |

**4.3.2 移动部门抽屉**：仅 `parentId`（`TreeSelect`，禁用自身与子孙节点）。

**4.3.3 角色绑定抽屉**：`roleIds`（`Select mode="multiple" showSearch`）。

### 4.4 表格列清单（右侧员工列表）

| 列名 | 数据字段 | 排序 | 筛选 | 省略 |
|---|---|---|---|---|
| 姓名 | `displayName` | — | — | 否 |
| 工号 | `employeeNo` | — | — | 否 |
| 职级 | `jobGradeName` | — | — | 否 |
| 状态 | `status` | — | Select | 否 |

---

## 5. 职级管理

### 5.1 功能表

| 功能点 | 交互方式 | Arco 组件 | 状态处理 | 权限控制 | 数据来源 |
|---|---|---|---|---|---|
| 职级列表 | 分页 + 关键字/状态筛选 | `Table`、`Input.Search`、`Select`、`Pagination`、`Tag` | 三态 + 刷新；URL 保存 | `job-grades:view` | `GET /api/job-grades` |
| 新增职级 | 抽屉，编码只读生成 | `Drawer`、`Form`、`InputNumber` | dirty；等级值唯一冲突字段错误 | `job-grades:create` | `POST /api/job-grades` |
| 编辑职级 | 复用抽屉，编码只读 | `Drawer`、`Form` | 保存刷新 | `job-grades:update` | `PUT /api/job-grades/{id}` |
| 默认角色绑定 | 单选角色下拉 | `Select showSearch` | 变更提示「仅对之后继承生效」 | `job-grades:update` | `GET /api/rbac/roles?page_size=100`、`PUT /api/job-grades/{id}` |
| 企微职位映射 | 文本/外部编码映射 | `Input`、`Select`、`Tag` | 未联调标注 | `job-grades:update` | `PUT /api/job-grades/{id}/wecom-mapping`（规划） |
| 关联员工数 | 列表列 + 点击看员工列表抽屉 | `Drawer`、`Table` | 分页三态 | `job-grades:view` | `GET /api/employees?jobGradeId=`（规划） |
| 删除职级 | 有关联员工禁删；二次确认 | `Popconfirm` | CONFLICT 提示 | `job-grades:delete` | `DELETE /api/job-grades` |

### 5.2 按钮清单

| 按钮 | 位置 | 图标 | 权限码 | 触发动作 |
|---|---|---|---|---|
| 新增职级 | 页头 | `IconPlus` | `job-grades:create` | 打开新增抽屉 |
| 刷新 | 页头 | `IconRefresh` | `job-grades:view` | 刷新列表 |
| 详情 | 行内 | `IconEye` | `job-grades:view` | 打开详情抽屉 |
| 编辑 | 行内 | `IconEdit` | `job-grades:update` | 打开编辑抽屉 |
| 关联员工 | 行内（员工数列） | `IconUserGroup` | `job-grades:view` | 打开关联员工列表抽屉 |
| 删除 | 行内 | `IconDelete`（danger） | `job-grades:delete` | 二次确认删除 |

### 5.3 抽屉字段明细（新增/编辑/详情三态）

| 字段名 | 标签 | 控件 | 必填 | 校验/规则 | 默认值 | 只读/可空 |
|---|---|---|---|---|---|---|
| code | 编码 | `Input` | 否 | — | 系统生成 | 只读 |
| name | 名称 | `Input` | 是 | 1–64 | 空 | — |
| levelValue | 等级值 | `InputNumber` | 是 | 正整数，服务端唯一 | 空 | — |
| defaultRoleId | 默认角色 | `Select showSearch`（远程角色） | 否 | — | 空 | 可空 |
| wecomPosition | 企微职位映射 | `Input` | 否 | — | 空 | 可空 |
| status | 状态 | `Switch` | 是 | — | `true` | — |

### 5.4 表格列清单

| 列名 | 数据字段 | 排序 | 筛选 | 省略 |
|---|---|---|---|---|
| 名称 | `name` | — | — | 否 |
| 编码 | `code` | — | — | 否 |
| 等级值 | `levelValue` | 可 | — | 否 |
| 默认角色 | `defaultRoleName` | — | — | 否 |
| 企微映射 | `wecomPosition` | — | — | 是 |
| 员工数 | `employeeCount` | 可 | — | 否 |
| 状态 | `status` | — | Select | 否 |
| 操作 | — | — | — | — |

---

## 6. 角色管理

### 6.1 功能表

| 功能点 | 交互方式 | Arco 组件 | 状态处理 | 权限控制 | 数据来源 |
|---|---|---|---|---|---|
| 角色列表 | 分页 + 关键字/状态筛选 | `Table`、`Input.Search`、`Select`、`Pagination`、`Tag` | 三态 + 刷新；URL 保存 | `rbac:roles:view` | `GET /api/rbac/roles` |
| 新增角色 | 抽屉：名称/应用范围/类型/状态 | `Drawer`、`Form`、`Select mode="multiple"`、`Radio` | dirty；字段错误 | `rbac:roles:create` | `POST /api/rbac/roles` |
| 编辑角色 | 复用抽屉，SYSTEM 类型只读 | `Drawer`、`Form` | 保存刷新 | `rbac:roles:update` | `PUT /api/rbac/roles/{id}` |
| 配置资源权限 | 应用选择 + 资源树 ALLOW/DENY + 继承标记 + 搜索 + 全选；**保存前差异预览** | `Drawer`、`Select`、`Tree`、`Radio`、`Switch`、`Input.Search`、`Alert` | 差异预览 added/removed；saving 防重复 | `rbac:roles:grant` | `GET/PUT /api/rbac/roles/{id}/grants`、`GET /api/rbac/roles/{id}/grants/diff`（规划）、`GET /api/rbac/resources?applicationCode=` |
| 绑定人员/部门/职级 | 三个 Tab 多选保存 | `Drawer`、`Tabs`、`Select mode="multiple"`、`TreeSelect` | dirty 确认 | `rbac:roles:assign` | `GET/PUT /api/rbac/assignments/*` |
| 复制角色 | 预填抽屉（名称+副本） | `Drawer`、`Form` | saving；成功刷新 | `rbac:roles:create` | `POST /api/rbac/roles/{id}/copy`（规划） |
| 反查被谁使用 | 抽屉展示引用该角色的员工/部门/职级 | `Drawer`、`Table`、`Tabs`、`Tag` | 分页三态 | `rbac:roles:view` | `GET /api/rbac/roles/{id}/subjects`（规划） |
| 停用/启用 | 行内切换，二次确认 | `Popconfirm`、`Tag` | 提示影响范围 | `rbac:roles:update` | `PUT /api/rbac/roles/{id}` |
| 删除角色 | 有绑定禁删；二次确认 | `Popconfirm` | CONFLICT 提示 | `rbac:roles:delete` | `DELETE /api/rbac/roles` |
| 审计入口 | 跳审计页按 ROLE 过滤 | `Button type="text"` | 携带参数 | `audit:view` | `GET /api/audit-logs?resourceType=ROLE&resourceId=` |

### 6.2 按钮清单

| 按钮 | 位置 | 图标 | 权限码 | 触发动作 |
|---|---|---|---|---|
| 新增角色 | 页头 | `IconPlus` | `rbac:roles:create` | 打开新增抽屉 |
| 刷新 | 页头 | `IconRefresh` | `rbac:roles:view` | 刷新列表 |
| 详情 | 行内 | `IconEye` | `rbac:roles:view` | 打开详情抽屉 |
| 编辑 | 行内 | `IconEdit` | `rbac:roles:update` | 打开编辑抽屉 |
| 配置权限 | 行内 | `IconKey` | `rbac:roles:grant` | 打开配置权限抽屉 |
| 绑定主体 | 行内 | `IconUserGroup` | `rbac:roles:assign` | 打开绑定抽屉 |
| 复制 | 行内 | `IconCopy` | `rbac:roles:create` | 打开复制抽屉 |
| 反查 | 行内 | `IconSearch` | `rbac:roles:view` | 打开反查抽屉 |
| 停用/启用 | 行内 | `IconLock`/`IconUnlock` | `rbac:roles:update` | 二次确认切换状态 |
| 删除 | 行内 | `IconDelete`（danger） | `rbac:roles:delete` | 二次确认删除 |
| 审计 | 行内 | `IconFile` | `audit:view` | 跳审计页 |

### 6.3 抽屉字段明细

**6.3.1 新增/编辑角色抽屉**

| 字段名 | 标签 | 控件 | 必填 | 校验/规则 | 默认值 | 只读/可空 |
|---|---|---|---|---|---|---|
| code | 编码 | `Input` | 否 | — | 系统生成 | 只读 |
| name | 名称 | `Input` | 是 | 1–64 | 空 | — |
| applicationCodes | 应用范围 | `Select mode="multiple" showSearch` | 否 | 空=全应用（按后端口径） | 空 | 可空 |
| roleType | 类型 | `Radio` | 是 | SYSTEM/CUSTOM | `CUSTOM` | SYSTEM 只读 |
| status | 状态 | `Switch` | 是 | — | `true` | — |

**6.3.2 配置权限抽屉**

| 字段名 | 标签 | 控件 | 必填 | 校验 | 默认值 | 只读 |
|---|---|---|---|---|---|---|
| applicationCode | 应用边界 | `Select showSearch` | 是 | — | 空 | — |
| grantNodes | 资源授权 | `Tree`（每节点 ALLOW/DENY/继承标记）+ `Radio` + `Switch`（全选） | 否 | 节点三态 | 当前 grants | — |

- 抽屉按钮：保存（`primary`，提交前调 `grants/diff` 弹差异预览确认）、取消。

**6.3.3 绑定主体抽屉**：`Tabs`（人员/部门/职级），每 Tab 一个 `Select mode="multiple"` / `TreeSelect`。

**6.3.4 复制角色抽屉**：`name`（默认「原名称-副本」，必填）。

**6.3.5 反查抽屉**：`Tabs`（员工/部门/职级）+ 每 Tab `Table`。

### 6.4 表格列清单

| 列名 | 数据字段 | 排序 | 筛选 | 省略 |
|---|---|---|---|---|
| 名称 | `name` | — | — | 否 |
| 编码 | `code` | — | — | 否 |
| 应用范围 | `applicationNames` | — | — | 是 |
| 类型 | `roleType` | — | Select | 否 |
| 人员数 | `employeeCount` | 可 | — | 否 |
| 部门数 | `departmentCount` | 可 | — | 否 |
| 职级数 | `jobGradeCount` | 可 | — | 否 |
| 权限数 | `resourceCount` | 可 | — | 否 |
| 状态 | `status` | — | Select | 否 |
| 操作 | — | — | — | — |

---

## 7. 资源管理（重点：应用选择器 + 树形层级，非平铺列表）

### 7.1 功能表

| 功能点 | 交互方式 | Arco 组件 | 状态处理 | 权限控制 | 数据来源 |
|---|---|---|---|---|---|
| 应用选择器 | 顶部 Select 选应用，切换重置树 | `Select showSearch`、`Empty` | 应用列表三态；未选提示「请先选择应用」 | `rbac:resources:view` | `GET /api/platform/apps` |
| 资源树 | `应用→模块→页面→菜单/操作/接口`；节点展开收起，默认展开两层；图标按类型区分 | `Tree`、`Icon`、`Tag` | 三态；**全量** `page_size=500` 组树 | `rbac:resources:view` | `GET /api/rbac/resources?applicationCode=&page_size=500` |
| 节点详情 | 点节点 → 右侧详情抽屉 + 绑定角色列表 | `Tree`、`Drawer`、`Descriptions`、`Table` | 未选中 `Empty` | `rbac:resources:view` | `GET /api/rbac/resources/{id}`、`GET /api/rbac/resources/{id}/roles`（规划） |
| 新增子节点 | 树节点操作，父自动带入；类型按父级约束 | `Drawer`、`Form`、`TreeSelect` | dirty；字段错误；编码/权限编码只读生成 | `rbac:resources:create` | `POST /api/rbac/resources` |
| 编辑节点 | 抽屉编辑；`visible` 用 Switch（Boolean） | `Drawer`、`Form`、`Switch` | 保存刷新树 | `rbac:resources:update` | `PUT /api/rbac/resources/{id}` |
| 移动节点 | 父资源 TreeSelect 同应用过滤；跨应用拦截 | `Drawer`、`TreeSelect` | 跨应用父节点禁用 | `rbac:resources:update` | `PUT /api/rbac/resources/{id}` |
| 层级规则 | 按钮可挂 MENU/PAGE 下；API/DATA_SCOPE 挂 PAGE 下 | `Select`/`TreeSelect` 选项过滤 | 非法父类型不可选 | create/update | 前端规则 + 后端校验 |
| 拖拽排序 | 同级拖拽，提交 sort_order；跨层/跨应用禁止 | `Tree`（draggable）、`Message` | 变更高亮；非法回弹 | `rbac:resources:update` | `PUT /api/rbac/resources/{id}/move`（规划） |
| 删除资源 | 有子/已绑角色禁删；二次确认 | `Popconfirm` | CONFLICT 提示 | `rbac:resources:delete` | `DELETE /api/rbac/resources` |

### 7.2 按钮清单

| 按钮 | 位置 | 图标 | 权限码 | 触发动作 |
|---|---|---|---|---|
| 应用选择 | 页头（Select，非按钮） | — | `rbac:resources:view` | 切换资源树数据源 |
| 新增子节点 | 页头/树节点 hover | `IconPlus` | `rbac:resources:create` | 打开新增子资源抽屉（带入父节点） |
| 刷新 | 页头 | `IconRefresh` | `rbac:resources:view` | 重载树 |
| 详情 | 树节点点击 | `IconEye` | `rbac:resources:view` | 打开详情抽屉 |
| 编辑 | 详情抽屉内/节点 hover | `IconEdit` | `rbac:resources:update` | 打开编辑抽屉 |
| 移动 | 详情抽屉内/节点 hover | `IconSwap` | `rbac:resources:update` | 打开移动抽屉 |
| 删除 | 节点 hover/详情抽屉内 | `IconDelete`（danger） | `rbac:resources:delete` | 二次确认删除 |

### 7.3 抽屉字段明细

**7.3.1 资源详情抽屉**（只读 + 绑定角色列表 + 操作按钮）。

**7.3.2 新增子资源/编辑资源抽屉**

| 字段名 | 标签 | 控件 | 必填 | 校验/规则 | 默认值 | 只读/可空 |
|---|---|---|---|---|---|---|
| code | 编码 | `Input` | 否 | — | 系统生成 | 只读 |
| permissionCode | 权限编码 | `Input` | 否 | 空时取 code | 系统生成 | 只读 |
| name | 名称 | `Input` | 是 | 1–64 | 空 | — |
| resourceType | 资源类型 | `Select` | 是 | 7 类枚举（按父级约束过滤） | `MODULE`（新应用） | 编辑态只读 |
| applicationCode | 所属应用 | `Select showSearch` | 是 | 父资源不得跨应用 | 应用选择器值 | 编辑态只读 |
| parentId | 父资源 | `TreeSelect showSearch` | 否 | 同应用过滤；类型合法 | 带入父节点 | 根节点可空 |
| routePath | 路由 | `Input` | 否 | 路径格式 | 空 | 可空 |
| icon | 图标标识 | `Input` | 否 | — | 空 | 可空 |
| sortOrder | 排序 | `InputNumber` | 否 | 整数 | `0` | — |
| visible | 菜单显示 | `Switch` | 是 | Boolean（非字符串） | `true` | — |
| isPublic | 是否公开 | `Switch` | 是 | Boolean | `false` | — |
| status | 状态 | `Switch` | 是 | — | `true` | — |

**7.3.3 移动抽屉**：仅 `parentId`（`TreeSelect`，同应用过滤，禁自身/子孙）。

### 7.4 表格列清单

> 本页主体为树形结构，无主 Table。节点展示字段：`name` + 类型 `Tag` + 图标 + 状态点；详情抽屉内「绑定角色」子表列如下。

| 列名 | 数据字段 | 排序 | 筛选 | 省略 |
|---|---|---|---|---|
| 角色名称 | `roleName` | — | — | 否 |
| 编码 | `roleCode` | — | — | 否 |
| 授权效果 | `effect`（ALLOW/DENY） | — | Select | 否 |

---

## 8. 应用中心

### 8.1 功能表

| 功能点 | 交互方式 | Arco 组件 | 状态处理 | 权限控制 | 数据来源 |
|---|---|---|---|---|---|
| 应用卡片 | 卡片网格：Logo/名称/描述/类型/状态/负责人/版本/入口；点击进详情 | `Grid`、`Card`、`Tag`、`Avatar`、`Button` | 三态 + 刷新；描述/URL `ellipsis` | `platform:apps:view` | `GET /api/platform/apps` |
| 应用分组 | 按类型分组，分组可折叠 | `Collapse`、`Tabs`、`Tag` | 空分组隐藏 | `platform:apps:view` | `GET /api/platform/apps`（type 字段） |
| 注册应用 | 注册表单，提交后**弹层展示 CI token 仅此一次** | `Modal`、`Form`、`Upload`、`Result` | CI token 关闭不可回看 | `platform:apps:create` | `POST /api/platform/apps` |
| 详情 Tabs | 概览/资源/成员/权限/版本/认证/审计 | `Drawer`、`Tabs` | 各 Tab 独立三态 | `platform:apps:view` | 见下 |
| 版本管理 | 版本列表 + 发布/回滚/审核/灰度 | `Table`、`Tag`、`Slider`、`Popconfirm` | 发布/回滚二次确认 | `platform:apps:publish`/`platform:apps:review` | route-metadata 系列端点 |
| 认证管理 | CI token 状态（仅 hash）+ 轮换（旧失效） | `Descriptions`、`Button`、`Popconfirm` | 新 token 仅一次展示 | `platform:apps:ci` | `POST /api/platform/apps/{appCode}/ci-credentials/rotate` |
| CI 元数据上报说明 | 展示上报命令/示例（只读） | `Alert`、`Typography` | — | `platform:apps:view` | `POST /api/platform/apps/{appCode}/route-metadata`（CI 侧） |

### 8.2 按钮清单

| 按钮 | 位置 | 图标 | 权限码 | 触发动作 |
|---|---|---|---|---|
| 注册应用 | 页头 | `IconPlus` | `platform:apps:create` | 打开注册 Modal |
| 刷新 | 页头 | `IconRefresh` | `platform:apps:view` | 刷新卡片 |
| 应用入口 | 卡片内 | `IconRight` | `platform:apps:view` | SSO 跳转应用 |
| 发布 | 版本 Tab 行内 | `IconSend` | `platform:apps:publish` | 二次确认发布版本 |
| 回滚 | 版本 Tab 行内 | `IconLeft` | `platform:apps:publish` | 二次确认回滚 |
| 审核通过/拒绝 | 版本 Tab 行内 | `IconCheckCircle`/`IconCloseCircle` | `platform:apps:review` | 二次确认审核 |
| 灰度 | 版本 Tab 行内 | `IconApps` | `platform:apps:review` | 打开灰度比例弹窗 |
| 轮换 CI token | 认证 Tab | `IconRefresh` | `platform:apps:ci` | 二次确认轮换 |
| 复制 token | CI token 展示弹层 | `IconCopy` | `platform:apps:create`/`ci` | 复制到剪贴板 |

### 8.3 弹窗/抽屉字段明细

**8.3.1 注册应用 Modal**

| 字段名 | 标签 | 控件 | 必填 | 校验/规则 | 默认值 | 只读/可空 |
|---|---|---|---|---|---|---|
| appCode | 编码 | `Input` | 是 | 唯一，创建后不可改 | 空 | 编辑态只读 |
| name | 名称 | `Input` | 是 | 1–64 | 空 | — |
| description | 描述 | `Input.TextArea` | 否 | ≤200 | 空 | 可空 |
| type | 类型 | `Select` | 是 | 枚举 | 空 | — |
| ownerEmployeeId | 负责人 | `Select showSearch`（远程员工） | 否 | — | 空 | 可空 |
| logo | Logo | `Upload`（图片） | 否 | 图片格式 | 空 | 可空 |
| repositoryUrl | 仓库地址 | `Input` | 否 | URL 格式 | 空 | 可空 |

- 提交成功 → `Modal` 内容切换为 `Result` 展示 `ciToken` + 复制按钮 + 「仅此一次，关闭后不再显示」。

**8.3.2 灰度比例弹窗**：`percent`（`Slider` 0–100，默认 0）。

**8.3.3 应用详情 Drawer**：Tabs 内嵌，无独立表单字段（概览/认证用 `Descriptions`；成员 Tab 用增删选择器）。

### 8.4 表格列清单（详情 Tabs 内子表）

**版本列表**

| 列名 | 数据字段 | 排序 | 筛选 | 省略 |
|---|---|---|---|---|
| 版本号 | `version` | 可（倒序） | — | 否 |
| 状态 | `status` | — | Select | 否 |
| 环境 | `environment` | — | Select | 否 |
| 发布时间 | `publishedAt` | 可 | — | 否 |
| 操作 | — | — | — | — |

**成员列表**

| 列名 | 数据字段 | 排序 | 筛选 | 省略 |
|---|---|---|---|---|
| 姓名 | `displayName` | — | — | 否 |
| 工号 | `employeeNo` | — | — | 否 |
| 角色 | `roleName` | — | — | 否 |
| 操作 | — | — | — | — |

**审计列表**（复用审计日志列，见第 11 章）。

---

## 9. 企业微信（重点：配置保留并完善）

### 9.1 功能表

| 功能点 | 交互方式 | Arco 组件 | 状态处理 | 权限控制 | 数据来源 |
|---|---|---|---|---|---|
| 页面结构 | 三 Tab：连接配置/通讯录同步/事件回调 | `Tabs` | 默认 Tab 按 URL | — | — |
| 配置表单 | CorpID/AgentID/回调地址可读写；Secret/AESKey 只写不读（`****`）；保存 | `Form`、`Input`、`Input.Password` | 留空不覆盖；dirty 确认；saving 防重复 | `wecom:config:update` | `GET/PUT /api/wecom/config`（PUT 规划） |
| 连接检查 | 调后端校验 CorpID/Secret，返回成功/errcode | `Button`、`Alert`、`Spin` | 检查中 loading；结果 Alert | `wecom:config:update` | `POST /api/wecom/config/check`（规划） |
| 登录开关 | 启用/停用企微登录 | `Switch` | 纳入表单保存 | `wecom:config:update` | `PUT /api/wecom/config`（规划） |
| 连接状态 | 顶部状态卡 + 回调路径；未联调标注 | `Card`、`Tag` | 失败 error + 重试（修复永久转圈） | `wecom:config:view` | `GET /api/wecom/config` |
| 同步批次列表 | 批次号/时间/状态/成功数/失败数/错误 | `Table`、`Pagination`、`Tag`、`Drawer` | 分页三态；RUNNING 轮询 | `wecom:sync:view` | `GET /api/integrations/wecom/sync/runs`（规划） |
| 发起同步 | 「发起全量同步」二次确认 | `Button`、`Modal.confirm` | 提交后跳详情轮询 | `wecom:sync:run` | `POST /api/integrations/wecom/sync/full` |
| 批次重试 | 失败/部分成功批次重试 | `Popconfirm`、`Button` | 二次确认 | `wecom:sync:run` | `POST /api/integrations/wecom/sync/runs/{runId}/retry`（规划） |
| 事件列表 | 类型/时间/状态/幂等号/重试次数/错误 | `Table`、`Select`、`Pagination`、`Tag` | 分页三态；幂等号 ellipsis + 复制 | `wecom:events:view` | `GET /api/integrations/wecom/events`（规划） |
| 事件重试 | RETRY_WAIT/DEAD_LETTER 行内重试 | `Popconfirm`、`Button` | 非失败态禁用；CONFLICT 提示 | `wecom:events:retry` | `POST /api/integrations/wecom/events/{id}/retry` |
| 原始数据 | 详情抽屉；「查看原始数据」仅审计权限可见 | `Drawer`、`Typography`、`Button` | 代码块展示；记审计 | `wecom:events:raw` | `GET /api/integrations/wecom/events/{id}/raw` |

### 9.2 按钮清单

| 按钮 | 位置 | 图标 | 权限码 | 触发动作 |
|---|---|---|---|---|
| 保存配置 | 配置 Tab 表单底部 | `IconSave` | `wecom:config:update` | 提交配置 |
| 连接检查 | 配置 Tab | `IconSync` | `wecom:config:update` | 调连接检查接口 |
| 发起全量同步 | 同步 Tab 页头 | `IconRefresh` | `wecom:sync:run` | 二次确认后发起同步 |
| 批次重试 | 批次行内 | `IconSync` | `wecom:sync:run` | 二次确认重试 |
| 事件重试 | 事件行内 | `IconSync` | `wecom:events:retry` | 二次确认重试 |
| 查看原始数据 | 事件详情抽屉内 | `IconEye` | `wecom:events:raw` | 调 raw 接口展示原文 |

### 9.3 弹窗/抽屉字段明细

**9.3.1 连接配置表单**（Tab 内 Form，非抽屉）

| 字段名 | 标签 | 控件 | 必填 | 校验/规则 | 默认值 | 只读/可空 |
|---|---|---|---|---|---|---|
| corpId | CorpID | `Input` | 是 | 非空 | 已配置值 | 可读写 |
| agentId | AgentID | `Input` | 是 | 非空 | 已配置值 | 可读写 |
| callbackUrl | 回调地址 | `Input` | 否 | — | 服务端生成 | 只读展示 |
| secret | Secret | `Input.Password` | 否 | 留空=不修改 | `****` | 只写不读 |
| aesKey | AESKey | `Input.Password` | 否 | 留空=不修改 | `****` | 只写不读 |
| loginEnabled | 登录开关 | `Switch` | 是 | — | 当前值 | — |

**9.3.2 批次详情抽屉**（只读）：批次号/状态/时间/成功数/失败数/错误明细列表。

**9.3.3 事件详情抽屉**（只读）：类型/状态/幂等号/重试次数/错误/接收时间/处理时间 + 「查看原始数据」按钮。

### 9.4 表格列清单

**同步批次列表**

| 列名 | 数据字段 | 排序 | 筛选 | 省略 |
|---|---|---|---|---|
| 批次号 | `runId` | — | — | 是 |
| 时间 | `createdAt` | 可（倒序） | — | 否 |
| 状态 | `status` | — | Select（5 态） | 否 |
| 成功数 | `successCount` | — | — | 否 |
| 失败数 | `failCount` | — | — | 否 |
| 错误 | `error` | — | — | 是 |
| 操作 | — | — | — | — |

**事件回调列表**

| 列名 | 数据字段 | 排序 | 筛选 | 省略 |
|---|---|---|---|---|
| 类型 | `eventType` | — | Select | 否 |
| 时间 | `receivedAt` | 可（倒序） | — | 否 |
| 状态 | `status` | — | Select（5 态） | 否 |
| 幂等号 | `eventNo` | — | — | 是（可复制） |
| 重试次数 | `retryCount` | 可 | — | 否 |
| 错误 | `errorMessage` | — | — | 是 |
| 操作 | — | — | — | — |

---

## 10. 文件中心

### 10.1 功能表

| 功能点 | 交互方式 | Arco 组件 | 状态处理 | 权限控制 | 数据来源 |
|---|---|---|---|---|---|
| 目录树（左） | 按目录/业务类型聚合，点击筛选；根「全部」 | `Tree`、`Spin` | 三态 | `files:view` | `GET /api/files/folders`（规划） |
| 文件表格（右） | 分页；文件名/类型/大小/上传人/应用/状态/时间；fileName ellipsis | `Table`、`Pagination`、`Tag`、`Tooltip` | 三态 + 刷新；URL 保存 | `files:view` | `GET /api/files` |
| 类型/状态筛选 | 类型 + 状态下拉 | `Select` | 切换重置页码 | `files:view` | `GET /api/files`（筛选参数） |
| 上传文件 | 选文件 → 申请 URL → 直传（进度）→ confirm | `Button`、`Upload`（隐藏 input）、`Progress` | 进度条；超限/类型即时提示 | `files:create` | `POST /api/files/upload-url`、PUT 预签名、`POST /api/files/confirm` |
| 下载文件 | 行内下载，新窗口打开 | `Button type="text"`、`Icon` | 失败 Message | `files:view` | `GET /api/files/download-url` |
| 删除文件 | 行内删除，二次确认 | `Popconfirm` | 删除刷新 | `files:delete` | `DELETE /api/files/batch` |
| 批量删除 | 勾选多行 → 批量删除 | `Table` 行选、`Modal.confirm` | 显示条数 | `files:delete` | `DELETE /api/files/batch` |
| 空间容量 | 已用/总量进度条 | `Progress`、`Statistic` | 容量不足禁用上传 | `files:view` | `GET /api/files/quota`（规划） |
| 权限来源 | 详情抽屉（归属/可见性/权限来源） | `Drawer`、`Descriptions`、`Tag` | 只读 | `files:view` | `GET /api/files/{id}`（规划） |

### 10.2 按钮清单

| 按钮 | 位置 | 图标 | 权限码 | 触发动作 |
|---|---|---|---|---|
| 上传文件 | 页头 | `IconUpload` | `files:create` | 触发隐藏文件选择 |
| 刷新 | 页头 | `IconRefresh` | `files:view` | 刷新列表 |
| 批量删除 | 工具栏 | `IconDelete`（danger） | `files:delete` | 二次确认批量删除 |
| 下载 | 行内 | `IconDownload` | `files:view` | 取下载 URL 新窗口打开 |
| 删除 | 行内 | `IconDelete`（danger） | `files:delete` | 二次确认删除 |
| 详情 | 行内 | `IconEye` | `files:view` | 打开权限来源抽屉 |

### 10.3 弹窗/抽屉字段明细

> 上传无表单字段（文件选择 input）；详情抽屉只读字段如下。

| 字段名 | 标签 | 控件 | 必填 | 校验 | 默认值 | 只读 |
|---|---|---|---|---|---|---|
| fileName | 文件名 | `Descriptions` | — | — | — | 只读 |
| contentType | 类型 | `Descriptions` | — | — | — | 只读 |
| fileSize | 大小 | `Descriptions` | — | — | — | 只读 |
| uploaderName | 上传人 | `Descriptions` | — | — | — | 只读 |
| bizType / bizId | 业务归属 | `Descriptions` | — | — | — | 只读 |
| visibility | 可见性 | `Tag` | — | — | — | 只读 |
| status | 状态 | `Tag` | — | — | — | 只读 |
| permissionSource | 权限来源 | `Tag` | — | — | — | 只读 |

### 10.4 表格列清单

| 列名 | 数据字段 | 排序 | 筛选 | 省略 |
|---|---|---|---|---|
| 文件名 | `fileName` | — | — | 是 |
| 类型 | `contentType` | — | Select | 否 |
| 大小 | `fileSize` | 可 | — | 否 |
| 上传人 | `uploaderName` | — | — | 否 |
| 所属应用 | `appName`/`bizType` | — | Select | 否 |
| 状态 | `status` | — | Select（PENDING/CONFIRMED/DELETED） | 否 |
| 时间 | `createdAt` | 可（倒序） | — | 否 |
| 操作 | — | — | — | — |

---

## 11. 审计日志

### 11.1 功能表

| 功能点 | 交互方式 | Arco 组件 | 状态处理 | 权限控制 | 数据来源 |
|---|---|---|---|---|---|
| 筛选区 | 人员/应用/资源类型/动作/时间范围 + 关键字 | `Form`、`Select showSearch`、`Select`、`DatePicker.RangePicker`、`Input.Search` | 筛选重置页码；URL 保存 | `audit:view` | `GET /api/audit-logs` |
| 审计列表 | 分页；时间/操作人/动作/资源类型/对象/结果/来源 IP/摘要 | `Table`、`Pagination`、`Tag`、`Tooltip` | 三态 + 刷新；摘要 ellipsis | `audit:view` | `GET /api/audit-logs` |
| 详情抽屉 | request_summary + before/after **字段级 diff** + 来源 IP + 结果 | `Drawer`、`Descriptions`、`Table`（diff）、`Tag` | 新增/删除/变更颜色区分 | `audit:view` | `GET /api/audit-logs/{id}` |
| 导出 | 按当前筛选导出 CSV | `Button`、`Message` | 导出中 loading；无数据提示 | `audit:export` | `GET /api/audit-logs/export`（规划） |
| 按对象追踪 | 详情内「该对象全部审计」 | `Button type="text"` | 跳转带筛选 | `audit:view` | `GET /api/audit-logs?resourceType=&resourceId=` |
| 按操作人追踪 | 操作人列点击 | 列链接 | 跳转带 `operator` | `audit:view` | `GET /api/audit-logs?operator=` |

### 11.2 按钮清单

| 按钮 | 位置 | 图标 | 权限码 | 触发动作 |
|---|---|---|---|---|
| 查询 | 筛选区 | `IconSearch` | `audit:view` | 应用筛选并重置页码 |
| 重置 | 筛选区 | `IconRefresh` | `audit:view` | 清空筛选 |
| 导出 | 页头 | `IconExport` | `audit:export` | 导出当前结果 |
| 详情 | 行内 | `IconEye` | `audit:view` | 打开详情抽屉 |
| 按对象追踪 | 详情抽屉内 | `IconLink` | `audit:view` | 跳转过滤 |

### 11.3 详情抽屉（只读，无表单字段）

- 触发：行内「详情」。展示 `request_summary`（`Descriptions`）、`before_json`/`after_json` 字段级 diff（`Table`，列：字段路径 / 变更前 / 变更后 / 变更类型 Tag）、`source_ip`、`result`。

### 11.4 表格列清单

| 列名 | 数据字段 | 排序 | 筛选 | 省略 |
|---|---|---|---|---|
| 操作时间 | `createdAt` | 可（倒序） | 时间范围 | 否 |
| 操作人 | `operatorName` | — | 远程员工选择 | 否 |
| 动作 | `action` | — | Select | 否 |
| 资源类型 | `resourceType` | — | Select | 否 |
| 对象名称 | `resourceName` | — | — | 是 |
| 结果 | `result` | — | Select（成功/失败） | 否 |
| 来源 IP | `sourceIp` | — | — | 否 |
| 变更摘要 | `requestSummary` | — | — | 是 |
| 操作 | — | — | — | — |

---

## 12. 权限分析

### 12.1 功能表

| 功能点 | 交互方式 | Arco 组件 | 状态处理 | 权限控制 | 数据来源 |
|---|---|---|---|---|---|
| 选择人员 | 远程搜索选择器，选择触发计算 | `Select showSearch` | 未选 `Empty` | `employees:permissions` | `GET /api/employees?status=ACTIVE` |
| 人员摘要 | 姓名/工号/部门/职级 | `Tag`、`Descriptions` | — | `employees:permissions` | `GET /api/employees/{id}` 等 |
| 组织关系 | 部门 + 各带职级 + 主部门标记 | `Table`、`Tag` | 空态「未设置部门」 | `employees:permissions` | `GET /api/employees/{id}`（memberships） |
| 角色来源 | 直绑/部门/职级默认角色，标来源 | `Table`、`Tag`、`Collapse` | 来源色区分 | `employees:permissions` | `GET /api/rbac/assignments/EMPLOYEE/{id}`、`GET /api/rbac/roles` |
| 直接授权 | 员工直接 ALLOW/DENY | `Table`、`Tag` | 空态 | `employees:permissions` | `GET /api/rbac/employees/{id}/grants` |
| 最终权限计算 | 允许/拒绝/最终生效三区 | `Tag`、`Table`、`Tabs` | loading `Spin`；error 重试 | `employees:permissions` | `GET /api/rbac/employees/{id}/permissions` |
| 来源与覆盖原因 | 每条权限来源 + DENY 覆盖原因 | `Table`、`Tag` | 被覆盖标记「已拒绝」 | `employees:permissions` | 同上（sources） |
| 管理人员角色 | 抽屉多选角色 | `Drawer`、`Select mode="multiple"` | dirty；保存重算 | `rbac:roles:assign` | `PUT /api/rbac/assignments/EMPLOYEE/{id}` |
| 管理直接权限 | 应用 + 资源树 ALLOW/DENY | `Drawer`、`Select`、`Tree`、`Radio` | 保存重算；差异预览 | `employees:grant` | `PUT /api/rbac/employees/{id}/grants` |

### 12.2 按钮清单

| 按钮 | 位置 | 图标 | 权限码 | 触发动作 |
|---|---|---|---|---|
| 选择人员 | 顶部（Select） | — | `employees:permissions` | 触发权限计算 |
| 管理人员角色 | 摘要卡 | `IconUserGroup` | `rbac:roles:assign` | 打开角色管理抽屉 |
| 管理直接权限 | 摘要卡 | `IconKey` | `employees:grant` | 打开直接授权抽屉 |
| 刷新 | 摘要卡 | `IconRefresh` | `employees:permissions` | 重算快照 |

### 12.3 抽屉字段明细

**12.3.1 管理人员角色抽屉**：`roleIds`（`Select mode="multiple" showSearch`）。

**12.3.2 管理直接权限抽屉**

| 字段名 | 标签 | 控件 | 必填 | 校验 | 默认值 | 只读 |
|---|---|---|---|---|---|---|
| applicationCode | 应用边界 | `Select showSearch` | 是 | — | 空 | — |
| allowIds / denyIds | 允许/拒绝资源 | `Tree`（checkable） | 否 | — | 当前 grants | — |

### 12.4 表格列清单（分区块多个子表）

**组织关系 / 角色来源**

| 列名 | 数据字段 | 排序 | 筛选 | 省略 |
|---|---|---|---|---|
| 部门/角色名称 | `name` | — | — | 否 |
| 职级 | `jobGradeName`（组织） | — | — | 否 |
| 主部门 | `primary`（组织） | — | — | 否 |
| 来源 | `source`（角色） | — | Select | 否 |

**直接授权 / 最终权限 / 来源覆盖**

| 列名 | 数据字段 | 排序 | 筛选 | 省略 |
|---|---|---|---|---|
| 权限编码 | `permissionCode` | — | — | 否 |
| 效果/结果 | `effect` / `result` | — | Select | 否 |
| 来源 | `sources` | — | — | 是（多来源 join） |

---

## 附：接口路径速查（数据来源汇总）

| 域 | 端点（`/api` 前缀省略） |
|---|---|
| 认证 | `POST /auth/login`、`POST /auth/refresh`、`POST /auth/logout`、`GET /auth/wecom/authorize`、`GET /auth/wecom/callback` |
| 平台 | `GET /me`、`GET /permissions`、`GET /me/menus`、`GET /me/apps`、`GET /sso/authorize` |
| 组织 | `GET/POST /employees`、`GET/PUT /employees/{id}`、`DELETE /employees`、`GET/POST /departments`、`GET/PUT /departments/{id}`、`DELETE /departments`、`GET /departments/{id}/employees`（规划）、`GET/POST /job-grades`、`GET/PUT /job-grades/{id}`、`DELETE /job-grades` |
| RBAC | `GET/POST /rbac/roles`、`GET/PUT /rbac/roles/{id}`、`DELETE /rbac/roles`、`GET/PUT /rbac/roles/{id}/grants`、`GET /rbac/roles/{id}/grants/diff`（规划）、`GET /rbac/roles/{id}/subjects`（规划）、`GET/PUT /rbac/employees/{id}/grants`、`GET/PUT /rbac/assignments/{subjectType}/{subjectId}`、`GET /rbac/employees/{id}/permissions`、`GET/POST /rbac/resources`、`GET/PUT /rbac/resources/{id}`、`DELETE /rbac/resources` |
| 应用注册 | `GET/POST /platform/apps`、`GET /platform/apps/{appCode}`（规划）、`POST /platform/apps/{appCode}/route-metadata`、`GET /platform/apps/{appCode}/route-metadata`（规划）、`POST .../{version}/publish`、`/rollback`、`/review`、`/rollout`、`POST /platform/apps/{appCode}/ci-credentials/rotate`、`POST /sso/token/exchange` |
| 企业微信 | `GET /wecom/config`、`PUT /wecom/config`（规划）、`POST /wecom/config/check`（规划）、`POST /integrations/wecom/sync/full`、`GET /integrations/wecom/sync/runs`（规划）、`GET /integrations/wecom/sync/runs/{runId}`、`POST /integrations/wecom/sync/runs/{runId}/retry`（规划）、`GET /integrations/wecom/events`（规划）、`POST /integrations/wecom/events/{id}/retry`、`GET /integrations/wecom/events/{id}/raw` |
| 文件 | `GET /files`、`POST /files/upload-url`、`POST /files/confirm`、`GET /files/download-url`、`DELETE /files/batch`、`GET /files/folders`（规划）、`GET /files/quota`（规划） |
| 审计 | `GET /audit-logs`、`GET /audit-logs/{id}`、`GET /audit-logs/export`（规划） |
