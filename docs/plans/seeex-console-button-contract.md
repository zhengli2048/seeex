# SEEEX 页面按钮与接口实施契约

日期：2026-11-16。承接既有实施清单和页面交互规格；本文件收敛本轮接口、字段和按钮行为。开发完成与运行验收分开记录；本轮禁止启动服务、构建、测试或执行数据库迁移。

## 1 通用使用规则

- URL 前缀 `/api`；JSON 使用 camelCase；分页输入 `page`（从 1 开始）、`pageSize`（1–100），输出 `{items,total}`。文件旧接口保留 `page_size` 兼容。
- 所有按钮独立校验权限，服务端必须再次校验；查询失败展示错误与重试，禁止以空数组伪装查询成功。
- 查询输入变化回到第一页；重置清除筛选；刷新保留筛选；分页/筛选写 URL；选择范围改变清空行选择。
- 新增/编辑采用抽屉，登录和连接配置可内联；保存前校验，保存中禁止重复提交；失败保留表单；成功关闭并刷新关联列表、选项、当前权限及菜单。
- 删除、停用、授权替换、发布、回滚、凭证轮换需二次确认。关闭有修改的表单需确认。
- 详情使用字段标签和业务名称。关联对象使用名称选择器，编码创建时自动生成且编辑不可修改。
- `PUT` 表单为完整表单：nullable 字段传 `null` 表示清空。系统编码不接受修改。
- 状态用字符串选择器；Boolean 只用于 `visible`、`primary` 等布尔字段。

## 2 登录和工作台

| 按钮 | 权限 | 接口与参数 | 交互与成功结果 |
|---|---|---|---|
| 登录 | 公开 | POST `/auth/login` `{phone,password}` | 必填；真实账号允许 admin 等非手机号登录名；保存会话，读取 `/me`，跳工作台 |
| 回车 | 公开 | POST `/auth/login` `{phone,password}` | 必填；真实账号允许 admin 等非手机号登录名；保存会话，读取 `/me`，跳工作台 |
| 企业微信登录 | 公开 | GET `/auth/wecom/authorize` | 未配置禁用；跳授权地址；回调消费 code/state；失败保留登录页 |
| 退出 | 已登录 | POST `/auth/logout` `{refreshToken}` | 服务端撤销后清会话和 Query 缓存 |
| 刷新工作台 | dashboard:view | GET `/me/apps`、组织统计 | 分别展示失败，禁止失败显示为 0 |
| 打开应用 | 当前应用访问权 | GET `/sso/authorize?appCode&redirectPath&entry` | 拼入一次性交换码，外部应用建立自身会话 |

登录字段：phone 必填最长 64；password 必填，UTF-8 不超过 72 字节。用户信息使用 `/me` 返回的 displayName，不写死管理员。

## 3 员工管理

| 按钮 | 权限 | 接口与参数 | 交互与结果 |
|---|---|---|---|
| 查询 | employees:view | GET `/employees` `page,pageSize,keyword,status,departmentId,jobGradeId` | 名称/工号模糊查；部门和职级选择器；保留分页状态 |
| 重置 | employees:view | GET `/employees` `page,pageSize,keyword,status,departmentId,jobGradeId` | 清空筛选与选择，回第一页；名称/工号模糊查；部门和职级选择器；保留分页状态 |
| 分页 | employees:view | GET `/employees` `page,pageSize,keyword,status,departmentId,jobGradeId` | 改变 page，保留其他筛选并清空行选择；名称/工号模糊查；部门和职级选择器；保留分页状态 |
| 刷新 | employees:view | GET `/employees` `page,pageSize,keyword,status,departmentId,jobGradeId` | 保留筛选，重新读取；名称/工号模糊查；部门和职级选择器；保留分页状态 |
| 新增员工 | employees:create | POST `/employees` EmployeeForm | 编号自动生成；默认 ACTIVE |
| 编辑员工 | employees:update | GET、PUT `/employees/{id}` EmployeeForm | 从详情回填，支持清空组织/联系方式 |
| 详情 | employees:view | GET `/employees/{id}` | 展示联系方式、多部门、主部门、职级、来源和状态 |
| 停用 | employees:update | PUT `/employees/{id}` 完整 EmployeeForm，status=DISABLED | 显示影响数量；停用后禁止登录和授权 |
| 批量停用 | employees:update | PUT `/employees/{id}` 完整 EmployeeForm，status=DISABLED | 显示影响数量；停用后禁止登录和授权 |
| 删除 | employees:delete | DELETE `/employees` body `[id]` | 软删除，不删除历史审计 |
| 批量删除 | employees:delete | DELETE `/employees` body `[id]` | 软删除，不删除历史审计 |
| 角色 | 对应授权权限 | 跳 `/permissions?employeeId={id}` | 预选人员，不能丢失查询上下文 |
| 直接权限 | 对应授权权限 | 跳 `/permissions?employeeId={id}` | 预选人员，不能丢失查询上下文 |
| 权限分析 | 对应授权权限 | 跳 `/permissions?employeeId={id}` | 预选人员，不能丢失查询上下文 |
| 登录账号 | employees:update | GET/PUT `/employees/{id}/account` `{loginPhone,password,status}` | 新账号密码必填；编辑留空保留，密码不回读；独立于员工组织信息 |

登录账号字段：loginPhone 必填 ≤32，password 新账号必填，至少 8 字符且 UTF-8 不超过 72 字节，status=ACTIVE/DISABLED。

EmployeeForm：employeeNo 只读；displayName 必填 1–128；internalMobile 可空，非空为 11 位手机号；internalEmail 可空且邮箱格式；status=ACTIVE/DISABLED/LEFT/LOCKED；primaryDepartmentId、jobGradeId 可空；memberships 为 `{departmentId,jobGradeId,primary}` 数组，部门不得重复，有关系时恰好一个主部门。主部门与 employee 冗余字段由后端同步。来源 source=LOCAL/WECOM，只读。

## 4 部门管理

| 按钮 | 权限 | 接口与参数 | 交互与结果 |
|---|---|---|---|
| 查询 | departments:view | GET `/departments` `page,pageSize,keyword,status` | 树使用完整分页采集，不能静默截断为前 100 项 |
| 重置 | departments:view | GET `/departments` `page,pageSize,keyword,status` | 清空筛选与选择，回第一页；树使用完整分页采集，不能静默截断为前 100 项 |
| 刷新 | departments:view | GET `/departments` `page,pageSize,keyword,status` | 保留筛选，重新读取；树使用完整分页采集，不能静默截断为前 100 项 |
| 新增根 | departments:create | POST `/departments` DepartmentForm | 子部门带入 parentId |
| 子部门 | departments:create | POST `/departments` DepartmentForm | 子部门带入 parentId |
| 编辑 | departments:update | PUT `/departments/{id}` DepartmentForm | 禁止自身/后代为父级；null 父级可移回根 |
| 移动 | departments:update | PUT `/departments/{id}` DepartmentForm | 禁止自身/后代为父级；null 父级可移回根 |
| 详情 | departments:view、employees:view | GET `/departments/{id}`；跳员工页 departmentId | 当前树节点也有操作入口，根节点可以管理 |
| 查看员工 | departments:view、employees:view | GET `/departments/{id}`；跳员工页 departmentId | 当前树节点也有操作入口，根节点可以管理 |
| 绑定角色 | rbac:roles:assign | GET/PUT `/rbac/assignments/DEPARTMENT/{id}` body `[roleId]` | 等待原值加载；支持清空；保留非 MANUAL 来源 |
| 删除 | departments:delete | DELETE `/departments` body `[id]` | 有子部门或在职关联员工时拒绝并提示 |
| 批量删除 | departments:delete | DELETE `/departments` body `[id]` | 有子部门或在职关联员工时拒绝并提示 |

DepartmentForm：code 只读；name 必填 1–128；parentId 可空；leaderEmployeeId 可空，选择员工；status=ACTIVE/INACTIVE。创建与编辑都保存负责人。

## 5 职级管理

| 按钮 | 权限 | 接口与参数 | 交互与结果 |
|---|---|---|---|
| 查询 | job-grades:view | GET `/job-grades` `page,pageSize,keyword,status` | 服务端分页 |
| 重置 | job-grades:view | GET `/job-grades` `page,pageSize,keyword,status` | 清空筛选与选择，回第一页；服务端分页 |
| 分页 | job-grades:view | GET `/job-grades` `page,pageSize,keyword,status` | 改变 page，保留其他筛选并清空行选择；服务端分页 |
| 刷新 | job-grades:view | GET `/job-grades` `page,pageSize,keyword,status` | 保留筛选，重新读取；服务端分页 |
| 新增 | 对应 create/update/view | POST `/job-grades`；GET/PUT `/job-grades/{id}` | 编码只读；默认角色名称选择器 |
| 编辑 | 对应 create/update/view | POST `/job-grades`；GET/PUT `/job-grades/{id}` | 编码只读；默认角色名称选择器 |
| 详情 | 对应 create/update/view | POST `/job-grades`；GET/PUT `/job-grades/{id}` | 编码只读；默认角色名称选择器 |
| 查看关联人员 | employees:view | 跳员工页 jobGradeId | 筛选对应职级 |
| 删除 | job-grades:delete | DELETE `/job-grades` `[id]` | 有员工使用时阻止 |
| 批量删除 | job-grades:delete | DELETE `/job-grades` `[id]` | 有员工使用时阻止 |

JobGradeForm：code 只读；name 必填；levelValue 为 0–9999 整数且有效职级不重复；defaultRoleId 可空，只绑定一个默认角色；status=ACTIVE/INACTIVE。职级不再写 JOB_GRADE 主体角色绑定。

## 6 角色管理

| 按钮 | 权限 | 接口与参数 | 交互与结果 |
|---|---|---|---|
| 查询 | rbac:roles:view | GET `/rbac/roles` `page,pageSize,keyword,status` | 服务端分页 |
| 重置 | rbac:roles:view | GET `/rbac/roles` `page,pageSize,keyword,status` | 清空筛选与选择，回第一页；服务端分页 |
| 分页 | rbac:roles:view | GET `/rbac/roles` `page,pageSize,keyword,status` | 改变 page，保留其他筛选并清空行选择；服务端分页 |
| 刷新 | rbac:roles:view | GET `/rbac/roles` `page,pageSize,keyword,status` | 保留筛选，重新读取；服务端分页 |
| 新增 | 对应 create/update/view | POST `/rbac/roles`；GET/PUT `/rbac/roles/{id}` | code 只读；name 必填；status=ACTIVE/INACTIVE |
| 编辑 | 对应 create/update/view | POST `/rbac/roles`；GET/PUT `/rbac/roles/{id}` | code 只读；name 必填；status=ACTIVE/INACTIVE |
| 详情 | 对应 create/update/view | POST `/rbac/roles`；GET/PUT `/rbac/roles/{id}` | code 只读；name 必填；status=ACTIVE/INACTIVE |
| 配置权限 | rbac:roles:grant | GET `/rbac/roles/{id}/grants` | 选应用后按应用回填 ALLOW/DENY，不清掉原选择 |
| 保存权限 | rbac:roles:grant | PUT `/rbac/roles/{id}/grants?applicationCode` `[{resourceId,effect}]` | 确认增加/移除数量；空数组清空当前应用；范围外保留，跨应用资源拒绝 |
| 查看使用主体 | rbac:roles:view | GET `/rbac/roles/{id}/usage` | 展示人员、部门、职级名称和来源 |
| 删除 | rbac:roles:delete | DELETE `/rbac/roles` `[id]` | 有主体/默认职级引用时阻止；软删除 |
| 批量删除 | rbac:roles:delete | DELETE `/rbac/roles` `[id]` | 有主体/默认职级引用时阻止；软删除 |

角色停用后不能参与权限计算。替换人工绑定只删除 MANUAL 记录，不覆盖系统来源。

## 7 资源管理

| 按钮 | 权限 | 接口与参数 | 交互与结果 |
|---|---|---|---|
| 切换应用 | rbac:resources:view | GET `/rbac/resources` `page,pageSize,applicationCode,status,keyword` | 读取完整树，按 sortOrder 排序；切换前保护未保存表单 |
| 刷新 | rbac:resources:view | GET `/rbac/resources` `page,pageSize,applicationCode,status,keyword` | 保留筛选，重新读取；读取完整树，按 sortOrder 排序；切换前保护未保存表单 |
| 新增 | rbac:resources:create | POST `/rbac/resources` ResourceForm | 子节点带入应用与父级 |
| 新增子资源 | rbac:resources:create | POST `/rbac/resources` ResourceForm | 子节点带入应用与父级 |
| 详情 | 对应 view/update | GET/PUT `/rbac/resources/{id}` | 编辑完整回填；父级限制同应用且排除自身和后代 |
| 编辑 | 对应 view/update | GET/PUT `/rbac/resources/{id}` | 编辑完整回填；父级限制同应用且排除自身和后代 |
| 移动 | 对应 view/update | GET/PUT `/rbac/resources/{id}` | 编辑完整回填；父级限制同应用且排除自身和后代 |
| 查看绑定 | rbac:resources:view | GET `/rbac/resources/{id}/usage` | 展示角色和直接授权人员 |
| 删除 | rbac:resources:delete | DELETE `/rbac/resources` `[id]` | 有子资源/授权时阻止；明确提示关联对象 |

ResourceForm：code、permissionCode 创建自动生成、编辑只读；name 必填；entryType=WEB/ADMIN（基础平台固定 ADMIN，已有资源不可改端）；menuCategory 必填、默认常用功能、≤64；resourceType=APPLICATION/MODULE/PAGE/MENU/ACTION/API/DATA_SCOPE；applicationCode 必填；parentId 可空、限同应用同端同分类（平台不限制分类）；routePath 对 MENU/PAGE 必填、其余可空，非空须为安全的单 `/` 开头站内路径；icon 可空；sortOrder 整数；visible/publicResource 为 Boolean；status=ACTIVE/INACTIVE。发布元数据的 permissionCode 按应用注册成资源，不能串应用复用。

## 8 权限分析

| 按钮 | 权限 | 接口与参数 | 交互与结果 |
|---|---|---|---|
| 选择员工 | employees:view | GET `/rbac/employees/{id}/permissions` | 可从员工页带入 employeeId；按应用展示最终/允许/拒绝和来源名称 |
| 应用 | employees:view | GET `/rbac/employees/{id}/permissions` | 可从员工页带入 employeeId；按应用展示最终/允许/拒绝和来源名称 |
| 刷新 | employees:view | GET `/rbac/employees/{id}/permissions` | 保留筛选，重新读取；可从员工页带入 employeeId；按应用展示最终/允许/拒绝和来源名称 |
| 管理人员角色 | rbac:roles:assign | GET/PUT `/rbac/assignments/EMPLOYEE/{id}` `[roleId]` | 空数组必须能清空；保留自动来源 |
| 管理直接权限 | employees:grant | GET/PUT `/rbac/employees/{id}/grants?applicationCode` | ALLOW/DENY 分开；保存前差异确认；范围外不被覆盖 |

权限模型：有效员工 → 有效人工/部门角色 + 各部门职级默认角色 → 有效资源 ALLOW − DENY。来源展示角色、部门、职级名称。已删除/停用主体不参与。数据范围是独立能力，未落到业务查询前不能标为完成。

## 9 应用中心和接入

| 按钮 | 权限 | 接口与参数 | 交互与结果 |
|---|---|---|---|
| 查询 | platform:apps:view | GET `/platform/apps` | 搜索名称/编码，按状态过滤；包含停用应用以便恢复 |
| 刷新 | platform:apps:view | GET `/platform/apps` | 保留筛选，重新读取；搜索名称/编码，按状态过滤；包含停用应用以便恢复 |
| 注册应用 | platform:apps:create | POST `/platform/apps` ApplicationForm | 抽屉保存后仅展示一次 ciToken 与 clientSecret，分别复制并妥善保存；关闭后不回显 |
| 编辑 | platform:apps:update | PUT `/platform/apps/{appCode}` ApplicationForm | 编码不可改，停用前确认；持久化后菜单立即失效 |
| 启停 | platform:apps:update | PUT `/platform/apps/{appCode}` ApplicationForm | 编码不可改，停用前确认；持久化后菜单立即失效 |
| 查看详情 | platform:apps:view | GET `/platform/apps/{appCode}` | Tabs：基本信息、路由版本、认证与接入说明 |
| 进入 Web/后台 | 当前应用访问权 | GET `/sso/authorize` `appCode,redirectPath,entry` | entry=WEB/ADMIN；真实用户，60 秒交换码；外部服务消费后建会话 |
| 查看版本 | platform:apps:view | GET `/platform/apps/{appCode}/route-metadata` | 展示路由名称/路径/权限/审核/比例/当前版本 |
| 菜单预览 | platform:apps:view | GET `/platform/apps/{appCode}/route-metadata` | 展示路由名称/路径/权限/审核/比例/当前版本 |
| 手工导入路由 | platform:apps:update | POST `/platform/apps/{appCode}/route-metadata/manual` MetadataForm | 使用结构化字段逐条添加，不要求输入 JSON |
| CI 上报 | 应用 CI 凭证 | POST `/platform/apps/{appCode}/route-metadata` + X-CI-Token | 无需用户 Bearer；同版本相同内容幂等，不同内容冲突 |
| 审核通过 | platform:apps:review | POST `.../{version}/review` `{approved}` | 二次确认，数据库保存 |
| 拒绝 | platform:apps:review | POST `.../{version}/review` `{approved}` | 二次确认，数据库保存 |
| 发布 | platform:apps:publish | POST `.../{version}/publish` 或 `/rollback` | 审核通过才发布；明确当前版本；回滚确实切换版本 |
| 回滚 | platform:apps:publish | POST `.../{version}/publish` 或 `/rollback` | 审核通过才发布；明确当前版本；回滚确实切换版本 |
| 保存灰度 | platform:apps:review | POST `.../{version}/rollout` `{percent}` | 0–100 整数；输入草稿后点击保存并确认，稳定用户分桶 |
| 轮换 | platform:apps:ci | POST `/platform/apps/{appCode}/ci-credentials/rotate` | 二次确认；旧 token 失效；新 token 仅一次展示 |
| 复制 CI token | 当前一次性响应 | 本地 Clipboard API，无网络 | 复制失败可手动复制，不触发再次轮换 |
| 轮换服务凭证 | platform:apps:auth | POST `/platform/apps/{appCode}/service-credentials/rotate` | 二次确认；旧应用后端凭证失效；clientSecret 仅一次展示 |
| 复制服务凭证 | 当前一次性响应 | 本地 Clipboard API，无网络 | 仅保存到应用后端，禁止配置在浏览器代码 |
| 选择所属端 | platform:apps:update | MetadataForm.routes[].entry | WEB/ADMIN；未配置 adminUrl 禁选后台；切换时清空父路由 |
| 编辑菜单分类 | platform:apps:update | MetadataForm.routes[].category | 默认常用功能，最多64字；变更时清空父路由 |
| 选择上级路由 | platform:apps:update | MetadataForm.routes[].parentRouteCode | 同端同分类 TreeSelect，排除自己和后代；留空为分类下一级 |
| 移除路由 | platform:apps:update | 从草稿数组移除；导入时整体提交 | 至少保留一条；引用已删除父级时提交明确报错 |

ApplicationForm：appCode 必填、`[a-z][a-z0-9-]{1,63}`，platform 为保留编码；name 必填 1–128；description 可空 ≤500；type=WEB/ADMIN/AI/OTHER；logo 可空 HTTP(S) URL；ownerEmployeeId 可空员工选择；status=ACTIVE/INACTIVE；baseUrl 必填 HTTP(S)，作为 Web 入口；adminUrl 可空 HTTP(S)，作为后台管理入口；apiUrl 可空 HTTP(S)，作为后端服务地址；repositoryUrl 可空 HTTP(S)；ssoCallbackPath 默认 `/sso/callback`，≤256字、不带查询串和片段的安全站内路径。URL 不允许账号密码、脚本协议或外站回调。

MetadataForm：version 必填，1–128 位字母/数字/点/下划线/连字符；gitCommit 可空；environment=DEV/TEST/PROD；baseUrl 为已登记 Web 地址；routes 数组：routeCode（1–64 位字母/数字/点/下划线/连字符）、name、path 必填，permissionCode 可空但不默认公开，icon 可空，openType=EXTERNAL/CURRENT_TAB/NEW_TAB，visible Boolean，sortOrder 整数；entry=WEB/ADMIN（旧报文默认WEB），category 默认常用功能，parentRouteCode 可空、限同端同分类且不可成环。routeCode 在版本内唯一，path 在同端内唯一；权限码全版本唯一，默认 `app:{appCode}:{web|admin}:{routeCode}:view`。必须显式授权资源后才出现菜单。发布版本同步资源属性和父级，并停用本次移除的历史路由资源；发布会覆盖同一路由的人工展示调整。

交换接口 POST `/sso/token/exchange`，请求头 `X-App-Secret`，请求体 `{code,appCode,entry}`；回调 URL 包含 code、appCode、entry。交换码绑定应用和所属端，未过期、未使用、应用仍启用且员工当前仍拥有该端页面权限才成功，数据库条件更新保证单次消费。结果为 `{subjectId,appCode,redirectPath,expiresAt,entry,permissions}`；只交付本应用、本端有效资源权限码。目标应用自行建立已验证本地会话。交换码只存 hash，不回传 CI 或服务凭证。

## 10 企业微信

| 按钮 | 权限 | 接口与参数 | 交互与结果 |
|---|---|---|---|
| 读取 | wecom:config:view/update | GET/PUT `/wecom/config` | corpId、agentId、redirectUri、token、secret、aesKey、loginEnabled；密钥只写不读，空值保留 |
| 保存配置 | wecom:config:view/update | GET/PUT `/wecom/config` | corpId、agentId、redirectUri、token、secret、aesKey、loginEnabled；密钥只写不读，空值保留 |
| 连接检查 | wecom:config:update | POST `/wecom/config/check` | 真实调用，不能假成功 |
| 全量同步 | wecom:sync:run/view | POST `/integrations/wecom/sync/full`；GET `/integrations/wecom/sync/runs` | 显示批次进度、成功失败计数；页面标注未真实联调 |
| 刷新 | wecom:sync:run/view | POST `/integrations/wecom/sync/full`；GET `/integrations/wecom/sync/runs` | 保留筛选，重新读取；显示批次进度、成功失败计数；页面标注未真实联调 |
| 事件详情 | wecom:events:view | GET `/integrations/wecom/events` | 分页状态过滤，普通列表不含 payload |
| 刷新 | wecom:events:view | GET `/integrations/wecom/events` | 保留筛选，重新读取；分页状态过滤，普通列表不含 payload |
| 失败重试 | wecom:events:retry | POST `/integrations/wecom/events/{id}/retry` | 仅 RETRY_WAIT/DEAD_LETTER，确认后回队列 |
| 受控原文 | wecom:events:raw | GET `/integrations/wecom/events/{id}/raw` | 独立鉴权、审计、不可默认展示 |

事件处理、失败退避、死信、回源 upsert 是验收项；只有状态字符串而无处理逻辑不得勾选完成。按用户约束不真实联调。

## 11 文件中心

| 按钮 | 权限 | 接口与参数 | 交互与结果 |
|---|---|---|---|
| 查询 | files:view | GET `/files` `page,page_size,keyword,status,contentType` | 状态和类型筛选，名称搜索 |
| 重置 | files:view | GET `/files` `page,page_size,keyword,status,contentType` | 清空筛选与选择，回第一页；状态和类型筛选，名称搜索 |
| 分页 | files:view | GET `/files` `page,page_size,keyword,status,contentType` | 改变 page，保留其他筛选并清空行选择；状态和类型筛选，名称搜索 |
| 刷新 | files:view | GET `/files` `page,page_size,keyword,status,contentType` | 保留筛选，重新读取；状态和类型筛选，名称搜索 |
| 上传 | files:create | POST `/files/upload-url` `fileName,contentType,size` + Idempotency-Key；PUT 签名 URL；POST `/files/confirm?objectKey` | 单文件 1B–50MB；PNG/JPEG/WebP/PDF/TXT；显示进度，成功确认后刷新 |
| 详情 | files:view | 列表返回完整 FileItem | 中文展示文件名、大小、类型、归属、时间、状态 |
| 下载 | files:view | GET `/files/download-url?objectKey` | 仅 CONFIRMED；保留原文件名；失败提示 |
| 删除 | files:delete | DELETE `/files/batch` body `[objectKey]` | 确认条数；先校验全部归属/状态再删存储对象；失败保留列表与选择 |
| 批量删除 | files:delete | DELETE `/files/batch` body `[objectKey]` | 确认条数；先校验全部归属/状态再删存储对象；失败保留列表与选择 |

用户和租户身份由网关从认证上下文生成，不能信任浏览器身份头。上传确认校验 OSS 实际对象；幂等冲突返回已持久化的对象键。文件目录、容量统计作为扩展项，未有对应模型前不伪造。

## 12 审计日志

| 按钮 | 权限 | 接口与参数 | 交互与结果 |
|---|---|---|---|
| 查询 | audit:view | GET `/audit-logs` `page,pageSize,operator,action,resourceType,from,to` | 失败明确显示，不能固定空页面 |
| 重置 | audit:view | GET `/audit-logs` `page,pageSize,operator,action,resourceType,from,to` | 清空筛选与选择，回第一页；失败明确显示，不能固定空页面 |
| 分页 | audit:view | GET `/audit-logs` `page,pageSize,operator,action,resourceType,from,to` | 改变 page，保留其他筛选并清空行选择；失败明确显示，不能固定空页面 |
| 刷新 | audit:view | GET `/audit-logs` `page,pageSize,operator,action,resourceType,from,to` | 保留筛选，重新读取；失败明确显示，不能固定空页面 |
| 查看详情 | audit:view | GET `/audit-logs/{id}` | 操作者、来源 IP、动作、对象、结果、时间、请求摘要；前后差异按字段展示 |

写操作接入审计；摘要不包含密码、CI token、Secret/AESKey、手机号全文。导出为扩展项，未接接口前不展示假按钮。

## 13 开发与验收记录

| 范围 | 开发状态 | 运行验收 |
|---|---|---|
| 应用新增、地址配置、版本菜单、SSO | 已编写前后端代码并静态复核 | 按用户要求不运行 |
| 组织字段、筛选、继承与关系校验 | 已编写前后端代码并静态复核 | 按用户要求不运行 |
| 角色/资源/人员授权交互与边界 | 已编写前后端代码并静态复核 | 按用户要求不运行 |
| 文件归属、删除顺序、筛选详情 | 已编写前后端代码并静态复核 | 按用户要求不运行 |
| 审计页面与基础记录 | 已编写查询、脱敏记录与部分对象前后快照 | 按用户要求不运行 |
| 企业微信配置、OAuth、全量/增量同步、重试 | 已编写接口、加密配置、后台消费者和每日校准 | 按用户要求不运行、不真实联调 |

## 14 静态复核范围与保留项

- Arco 参数依据官方 Tree、TreeSelect、Form、Drawer 类型定义核对。Tree 使用 string key/checkedKeys，TreeSelect 使用 string value；Switch 使用 triggerPropName=checked，状态使用字符串 Select。未安装或启动前端运行环境，未做视觉渲染验收。
- 新增迁移 V19、V20、V21，均未执行。V21 增加资源所属端、菜单分类、交换码端、应用服务凭证摘要及平台导航模块；旧应用凭证需要在应用详情主动轮换取得。V19 增加应用字段、当前版本、审核比例、权限码和历史关系回填；V20 增加加密配置、OAuth 状态、同步时间和失败计数。主键使用数据库 AUTO，与 MySQL 建表语义一致。
- 企业微信页面保存配置需要服务端 `WECOM_SETTINGS_KEY`（Base64 编码的 32 字节密钥）；现有环境变量仍作为未保存配置时的回退。目标应用需要实现登记的 SSO 回调路径和交换后本地建会话。
- 新增应用版本回读/审核门禁、文件整批归属预检的回归用例源码；全部未执行。未运行 Maven、npm、服务、OSS、企业微信或数据库迁移。
- 原有大清单中的外部职级映射维护、同步差异预览、数据范围落到业务查询、应用多管理员与成员/策略独立页、公告/待办、文件目录容量和审计导出仍作为未完成项保留；本轮不据静态编辑标记这些能力完成。

Arco 官方接口来源：[Tree](https://github.com/arco-design/arco-design/blob/main/components/Tree/interface.ts)、[TreeSelect](https://github.com/arco-design/arco-design/blob/main/components/TreeSelect/interface.ts)、[Form](https://github.com/arco-design/arco-design/blob/main/components/Form/interface.tsx)、[Drawer](https://github.com/arco-design/arco-design/blob/main/components/Drawer/interface.ts)。


## 15 统一认证平台导航与持续授权（本轮补齐）

| 用户操作/接口 | 参数和返回 | 前端或调用方行为 |
|---|---|---|
| 加载左侧导航 | GET `/me/navigation` → NavNode[] | 后端过滤并返回完整树；失败显示错误与重试，不回退到静态菜单 |
| 展开工作空间/分类 | GROUP：key、label、children | 仅展开；不把分组 key 当作 URL |
| 打开基础平台页面 | INTERNAL：path | React Router 站内跳转；工作台对已登录员工始终提供 |
| 打开外部应用页面 | EXTERNAL：appCode、entry、path、openType | SSO 授权后跳转；CURRENT_TAB 当前窗口，NEW_TAB/EXTERNAL 新窗口；失败关闭预开的空白页并提示 |
| 查看本应用权限 | POST `/app-auth/permissions`，X-App-Code、X-App-Secret；body `{subjectId,entry}` | 返回 appCode、subjectId、entry、allow、deny、effective、checkedAt；subjectId 必须来自应用自身已验证会话 |
| 检查业务 API 权限 | POST `/app-auth/check`，同上请求头；body `{subjectId,entry,permissionCode}` | 返回 allowed、checkedAt 等；allowed=false、网络失败或服务错误均必须拒绝敏感操作 |
| 切换授权树所属端 | 本地过滤资源 entryType；最终 PUT 仍提交当前应用完整数组 | 不清空其他端草稿；停用资源可保留或移除原授权，不可新授；保存其他应用不受影响 |
| 清空当前显示范围 | 本地只移除当前过滤范围的草稿 | 再次保存前显示差异；全部端清空另有明确按钮与确认 |

NavNode 字段：key、label、kind=GROUP/INTERNAL/EXTERNAL、appCode、entry、path、openType、children（始终数组）。导航顺序为工作台、基础平台、应用 A、应用 B；应用内再按 Web/后台、分类、父子资源组织。无权访问的父页面只作为无点击地址的祖先容器；隐藏、停用、删除或断链祖先不把孩子提升为根节点。工作台的应用入口使用同一祖先展示规则。

权限与菜单分开：visible 只控制入口展示；已授权但隐藏的页面仍可通过 SSO 校验。应用后端权限接口按本应用、本端 ACTIVE 资源返回实时权限，不受菜单可见性和显示灰度控制，因此可以验证 ACTION/API。应用停用后服务凭证调用会被拒绝，员工停用或权限撤回后后续判定为不允许。目标应用必须实际接入该接口；平台不能替不存在于本仓库中的目标应用自动实现逐 API 鉴权或统一注销。

## 16 全局错误反馈与静态核对

- API 和刷新请求设置20秒超时，避免无响应时无限等待。
- Axios 将 HTTP 失败和 HTTP 200 内非 OK 业务码统一为 ApiError，保留后端 message/code/error_type 与可选 request_id/field_errors。
- React Query 的 QueryCache、MutationCache 提供统一错误提示兜底；局部错误框仍保留，重复弹出提示按错误对象及短时间同内容去重。
- 登录请求的401直接显示后端原因；已登录业务请求401通过单次共享刷新请求重试，最终失效清理本地身份与查询缓存，并在登录页明确提示。
- 查询失败与没有数据、没有权限分别展示；关联选项读取失败时禁止保存，避免把读取失败误当空集合提交。刷新失败与已成功保存分别反馈。
- 不在页面显示后端堆栈、内部异常对象或请求凭证。
- 按 Arco 官方 [Menu 类型定义](https://github.com/arco-design/arco-design/blob/main/components/Menu/interface.tsx)、[TreeSelect 文档](https://arco.design/react/components/tree-select) 核对字符串 key、受控值、菜单事件及树选择参数；Form 中 Switch 通过 checked 绑定。仓库未安装依赖，无法进行对应本地版本类型检查；未运行 npm、Maven 或页面。


## 应用治理后续实施更新

应用管理员、权限策略、角色与应用数据范围、文件应用归属已继续编写。此前保留项中这四项的状态以 [应用治理按钮与接口契约](./seeex-application-governance-contract.md) 为准；该文档列出字段、权限、页面行为、迁移和外部应用接入边界。代码仅静态核对，运行验收未执行。


后续静态更新：组织字段/统计、企微职级映射/同步差异预览、审计筛选/CSV导出已继续编写，状态与契约见 [组织、企微与审计实施说明](./seeex-organization-wecom-audit-contract.md)。旧未完成描述属之前基线，不据此宣称运行通过。
