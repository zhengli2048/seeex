# 应用治理、数据范围和文件归属实施契约

2026-11-16。本轮仅开发和静态检查；没有运行构建、类型检查、测试、服务、迁移或外部调用。本文更新既有清单中这四项的代码状态，不代表运行验收。

## 页面和按钮

| 入口/按钮 | 接口与权限 | 参数、交互与边界 |
|---|---|---|
| 应用管理入口 | GET `/api/me/application-management`；GET `/api/platform/apps` | 平台用户按原权限查看，显式应用管理员只看到自己可管理的应用；导航自动提供应用管理入口 |
| 应用详情 → 管理员与权限策略 | GET `/api/platform/apps/{app}/governance` | 返回 admins、policies、canManageAdmins、options；字典只有必要名称及资源，不含手机和凭证 |
| 保存管理员 | PUT `.../governance/admins`；platform:apps:update | body 数字员工ID数组；整应用替换；二次确认；空数组清空；管理员必须有效，重复ID拒绝；应用管理员不能委任自己或其他管理员 |
| 放弃管理员修改 | 本地状态 | 恢复服务端管理员，不发送请求 |
| 新增策略 | POST `.../governance/policies` | 策略名≤128、subjectType=EMPLOYEE/ROLE、subjectId、resourceId、effect=ALLOW/DENY、scopeType、departmentIds、employeeIds、status=ACTIVE/INACTIVE |
| 编辑/启停策略 | PUT `.../governance/policies/{id}` | 保留ID和应用身份；关联对象用名称选择器，资源用本应用 TreeSelect；保存失败保留草稿；停用后不参与后续计算 |
| 删除策略 | DELETE `.../governance/policies/{id}` | 确认后删除关联策略；其他授权来源保留；不能删除其他应用策略 |
| 计算当前结果 | GET `.../governance/evaluate?employeeId&resourceId` | 显示真实 allowed、scope.type、部门名和人员名；变更选择或保存策略后清除旧预览 |
| 角色行 → 数据范围 | GET `/api/rbac/roles/{id}/data-scopes`；rbac:roles:view | 返回该角色全部资源范围；加载失败可重试，禁止提交 |
| 保存当前资源范围 | PUT 同上；rbac:roles:grant | `{resourceCode,scopeType,departmentIds,employeeIds}`；仅替换该资源，保留其他资源配置；确认后立即生效 |
| 删除当前范围 | DELETE 同上 `?resourceCode`；rbac:roles:grant | 恢复该角色该资源的默认本人范围；不能改变功能授权 |
| 文件中心切换应用 | GET `/api/me/file-applications` | 仅显示基础平台及当前有权访问/管理的启用应用；切换清空选中行、页码、详情；上传/删除中禁用切换 |
| 文件查询/上传/确认/下载/删除 | 原 `/api/files` 系列接口增加 `applicationCode`，默认 platform | 维持 files:view/create/delete；服务端还检查应用访问资格。列表、详情字段包含 applicationCode；下载使用行归属应用；其他参数不变 |

应用管理员可以维护本应用基础信息、路由版本、审核发布和应用凭证，不能由此获得组织、平台角色或其他应用管理权限。负责人字段不自动赋予管理员身份。基础平台权限仍通过原角色模型管理。

## 权限策略和实际数据范围

- 策略主体必须是有效员工或角色，资源必须是本应用有效资源，服务端验证所有关联；非本应用策略ID拒绝访问。
- PermissionService 在人员、部门、职级角色和直接授权之外合并有效应用策略；只有启用应用策略参与计算，DENY 始终覆盖所有来源 ALLOW，权限分析来源显示策略名称。
- scopeType 为 SELF、CUSTOM、DEPARTMENT、DEPARTMENT_AND_DESCENDANTS、ALL。默认 SELF；CUSTOM 使用实际部门/人员集合，空集合表示无可见行。多个匹配角色和允许策略取集合并集；ALL 显式表示无限制。
- 部门范围以有效员工部门关系计算，后代遍历使用集合去重，停用/删除部门不展开。自定义配置最多1000项，写入前验证部门及员工有效性。
- 功能权限和行范围分别判断；仅配置范围不会获得资源权限。员工/部门列表在数据库分页前约束范围，详情、更新、删除、账号管理及人员/部门RBAC访问再次检查目标ID；批量操作先验证全部目标。员工调动、部门移动的目标部门也校验范围。
- 迁移给既有 platform_admin 初始化 employees:view、departments:view 的 ALL，保留平台组织管理能力；其他角色没有配置时为本人。
- 文件仍保持“应用 + 租户 + 本人”私有边界；数据范围 ALL 不绕过私有文件归属。

## 外部应用行权限协议

新增两个仅供可信应用后端使用的接口（与已有 `/check` 同样使用 X-App-Code、X-App-Secret）：

1. POST `/api/app-auth/data-scope`，body `{subjectId,entry,permissionCode}`。先验证功能权限，再返回 `{type,departmentIds,employeeIds}`；应用必须把范围落实到分页前的数据库条件，不能在分页后仅隐藏列表。
2. POST `/api/app-auth/check-data`，body 在上述字段外增加 `targetEmployeeId`、`targetDepartmentId`（至少一个有效值）。返回 `{allowed,checkedAt,...}`；目标归属必须由应用后端读取真实记录，不能信任浏览器填报。用于详情、编辑、删除与导出再次检查；失败必须拒绝。

本仓库之外的应用仍需实现以上调用，平台无法自动修改外部应用的业务SQL、对象归属或会话。新增接口不意味着外部应用已经联调完成。

## 文件边界与迁移

- web 网关从真实 Authentication 取得身份，验证 applicationCode 后以 X-App-Code 传给 file 服务；浏览器自带同名头不会被转发。
- file 服务在元数据创建、幂等重放、列表、确认、下载、单个删除和整批删除预检中使用同一应用边界；改传应用不能访问原对象。幂等键跨应用冲突明确拒绝。
- web V22 创建 application_admin、application_policy，并初始化平台管理员的数据范围。file V2 增加 application_code 和索引，历史文件归 platform。两份迁移在各自服务数据库执行，均未执行。
- 管理员关系、策略和范围写操作记录审计；服务凭证不写入这些记录。

## 静态核对与限制

已检查 DTO 字段、API 路径、方法与页面调用、Mapper 扫描、迁移列名、作用域条件、空集合、关联加载失败、草稿保留、按钮权限和危险操作确认。新增范围默认本人及自定义集合并集的回归用例源码，未执行。

静态检查不能证明编译、数据库兼容、浏览器布局或实际请求成功。其他清单缺口（组织统计字段、企微职级映射/差异预览、审计高级筛选与导出、统一外部应用注销等）没有因本轮四项实现而被标记完成。
