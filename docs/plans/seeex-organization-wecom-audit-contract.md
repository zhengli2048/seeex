# 组织、企微与审计剩余项实施契约

2026-11-16。本轮只开发、静态检查。未运行 lint、类型检查、构建、测试、服务、数据库迁移或企业微信/OSS。本文更新旧清单中对应功能状态；代码已编写不代表运行验收通过。

## 组织字段与统计

| 页面 | 响应补充 | 展示与统计口径 |
|---|---|---|
| 员工 | source、updatedAt | 来源名称、更新时间；部门筛选改 TreeSelect，字符串key转换数字ID |
| 部门 | parentName、employeeCount、updatedAt | 上级名称、当前可见范围内在职员工数、更新时间；部门关联去重，包含主部门兜底；无 employees:view 不返回人数，页面明确提示权限不足 |
| 职级 | source、updatedAt | 来源与更新时间；本地创建的职级为LOCAL，企微映射不会把本地职级伪装为企微创建 |
| 角色 | resourceCount、employeeCount、updatedAt | 资源数为角色关联授权条目数（包括ALLOW/DENY，不等于最终可用权限数）；人员数为直接绑定、有效部门角色、有效职级默认角色关联的在职人员并集，去重，不等于各人最终权限 |

现有列表和详情接口字段同步扩展，新增字段不作为可编辑业务表单输入。更新时间由数据库维护，ORM禁止覆盖为旧的读取时间。迁移新增职级/角色更新时间，存量初值为迁移时间，不冒充真实历史修改时间。员工和部门复用原有时间列。

## 企业微信职级映射按钮/API

入口：企业微信 → 连接配置 → 职位与本地职级映射。

| 按钮 | 方法/路径（前缀/api） | 权限与参数 |
|---|---|---|
| 查询/刷新映射 | GET `/wecom/directory/mappings` | wecom:config:view；返回 items 与有效职级 grades 名称选择项 |
| 新增 | POST `/wecom/directory/mappings` | wecom:config:update；`{externalPosition,jobGradeId,status}`，职位1–128字、去首尾空白、区分大小写唯一；职级为有效本地ID |
| 编辑/启停 | PUT `/wecom/directory/mappings/{id}` | 同上；status=ACTIVE/INACTIVE；读失败、提交中禁止保存；错误保留草稿 |
| 删除 | DELETE `/wecom/directory/mappings/{id}` | wecom:config:update，二次确认；不清空已有员工历史职级 |

全量同步与增量事件复用同一映射函数。映射到有效职级时更新成员各部门职级和主职级；未映射、映射停用或目标失效时保留原职级，新部门关系优先保留原主职级。职级默认角色通过现有权限计算生效，不重复插入人工角色。删除映射不等于撤销此前职级，应另行调整员工职级。

## 同步差异预览

入口：企业微信 → 通讯录同步 → 同步差异预览。

- “读取并预览差异”：POST `/api/wecom/directory/preview`，同时要求 wecom:sync:view 和 wecom:sync:run；只读远端和本地数据，不修改组织。
- 返回 capturedAt、employeeCount、departmentCount、items、notice。items字段为 kind、key、name、operation、changes、notice；changes包含field、before、after。
- 支持 ADD、UPDATE、OUT_OF_SCOPE、CONFLICT 筛选，分页20条，展开逐字段变更。联系方式仅提示发生变更，不下发电话号码/邮箱；未变化对象不列入差异。
- 对比部门名称/上级/状态，以及员工姓名/部门/状态/映射职级/联系方式。本地已删除身份、字段长度冲突明确列为CONFLICT。
- 所有远端部门成员读取成功后才返回；部分读取失败整次预览失败，不能产生“缺失即删除”的误报。上限2000部门、10000成员，超过明确报错。预览请求120秒超时，不自动重试。
- “预览后发起同步”：确认说明后调用现有 POST `/api/integrations/wecom/sync/full`；使用 wecom:sync:run。预览是只读时间点，真正同步重新读取最新通讯录，可能与预览不同，不声称按固定快照应用。
- “不预览直接同步”保留为明确入口。远端可见范围外本地对象标为OUT_OF_SCOPE，既不自动删除部门，也不推断员工离职。
- 本轮没有真实请求企业微信，完整通讯录权限、接口返回与预览耗时尚未联调验证。

## 审计筛选、详情与导出

| 操作 | 方法/路径（前缀/api） | 参数/交互 |
|---|---|---|
| 筛选字典 | GET `/audit-logs/options`；audit:view | 操作人姓名/id、应用名称/code、已记录资源路径；不要求组织管理权限，不返回联系方式 |
| 查询/刷新/分页 | GET `/audit-logs`；audit:view | page、pageSize、operator、action、resourceType、applicationCode、resourceKey、result、from、to |
| 重置 | 本地清空条件 | 页码同时复位 |
| 详情 | GET `/audit-logs/{id}`；audit:view | 操作者名称、应用、资源路径、结果、请求摘要、脱敏前后字段；展开嵌套字段并展示差异表，最多8层/300个字段，深层对象明确省略 |
| 导出筛选结果 | GET `/audit-logs/export`；audit:view + audit:export | 共享列表筛选，忽略分页导出全部匹配行；CSV UTF-8 BOM，固定文件名；超过10000行明确拒绝，不静默截断；失败显示后端具体原因 |

result=SUCCESS/FAILED；时间需有效且from≤to。新增审计通过请求上下文记录applicationCode、resourceKey、resourceId、可选合法请求编号；应用操作提取路径应用编码，文件操作取已验证应用上下文。历史日志缺失的字段保持未知，不伪造补全。

导出只包含时间、操作人、应用、动作、对象、资源标识、结果、来源IP、请求编号，不包含原始请求、凭证或完整JSON。所有CSV单元格转义引号，对公式前缀防注入。导出动作审计不记录CSV正文；响应no-store。权限分析、组织快照也受数据范围校验，避免失败请求的审计读取绕过目标范围。

## 上轮权限/组件复查

- 文件 X-App-Code 改为仅取网关校验后绑定的服务端属性，不从原始query或浏览器header直接取值；元数据层仍按应用/租户/所有者隔离。
- 停用应用的角色/人员直接权限也从 PermissionService 快照排除，与策略、导航和应用服务鉴权一致。
- 审计捕获员工、部门及RBAC绑定前快照时，执行相同人员/部门范围检查。
- TreeSelect 使用字符串key、treeCheckable、treeCheckStrictly；Switch checked与表单 triggerPropName=checked 对齐。参考 [Arco官方TreeSelect类型](https://raw.githubusercontent.com/arco-design/arco-design/main/components/TreeSelect/interface.ts)。未安装本地依赖，不能据此声明当前依赖版本类型检查已通过。
- 表单/字典查询失败禁止提交；错误提示使用统一去重接口；CSV失败Blob先解析后端JSON错误再显示。

## 迁移与静态检查

新增 web V23：职级来源/更新时间、角色更新时间、审计应用/资源字段与索引、职级映射表、audit:export资源及平台管理员授权。迁移未执行。新增CSV上限/公式防护和预览远端读取不完整的回归用例源码，未执行。

已做相对导入、明显未使用导入、Java括号、DTO调用字段、Mapper扫描、迁移列名及关键文件diff空白静态核对。未执行编译、类型检查或浏览器视觉验收。部门树定位分页、应用成员独立页/全应用差异与审计Tab、文件上传人名称/目录/容量、外部应用统一注销和仓库外业务接入仍保留为未完成，不把本轮新增页面当作整个平台验收通过。
