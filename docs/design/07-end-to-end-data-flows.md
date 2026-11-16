# Seeex 端到端数据流程设计

本文把认证、组织同步、权限计算、动态菜单、业务 API、数据范围和审计串成可执行的数据流。所有企业微信字段以 [官方核对记录](../research/wecom-official-api.md) 为准。

## 1. 总体数据流

```mermaid
flowchart LR
  W[企业微信] -->|OAuth / 通讯录事件| G[后端网关]
  U[浏览器] -->|登录 / 页面 / API| G
  G --> A[认证模块]
  G --> P[权限模块]
  G --> B[业务模块]
  A --> S[(auth_session)]
  A --> E[(employee/account)]
  P --> R[(RBAC tables)]
  P --> D[(data_scope)]
  B --> DB[(业务数据库)]
  G --> L[(audit_log)]
  W --> SY[同步与事件模块]
  SY --> E
  SY --> R
  SY --> L
  SY --> EV[(sync_event)]
```

系统中有三类事实来源：

| 数据 | 权威来源 | 系统处理 |
| --- | --- | --- |
| 企业微信 userid、部门关系、外部状态 | 企业微信 | 保存外部快照，按同步规则更新 |
| 员工内部展示字段、角色、资源、数据范围 | 系统数据库 | 本地维护 |
| 登录会话、权限快照、当前 Tab | 系统运行时 | 有效期或缓存，不作为组织事实 |

## 2. 本地手机号登录

```mermaid
sequenceDiagram
  participant B as 浏览器
  participant A as 认证模块
  participant DB as 数据库
  participant P as PermissionService
  B->>A: POST /auth/login(phone,password)
  A->>DB: 查询有效 employee_account
  A->>A: 校验密码、状态、锁定策略
  A->>DB: 创建 auth_session(Token 哈希)
  A->>P: 计算 has_permission
  P->>DB: 读取有效角色和资源
  A-->>B: Access/Refresh Token + 员工摘要
```

规则：

1. 不泄露手机号是否存在；
2. 离职、禁用和锁定账号拒绝登录；
3. 无角色仍返回认证成功，但 `has_permission=false`；
4. Access/Refresh Token 只返回一次，数据库只存哈希；
5. 登录成功/失败、锁定和解锁写审计。

## 3. 企业微信 OAuth 登录

```mermaid
sequenceDiagram
  participant B as 浏览器
  participant A as 认证模块
  participant W as 企业微信 API
  participant DB as 数据库
  B->>A: GET /auth/wecom/authorize
  A->>DB: 保存一次性 state 哈希
  A-->>B: authorize_url
  B->>W: 企业微信授权
  W-->>A: GET callback(code,state)
  A->>DB: 校验并消费 state
  A->>W: auth/getuserinfo(access_token,code)
  W-->>A: userid
  A->>W: user/get(access_token,userid)
  W-->>A: 成员详情
  A->>DB: 外部身份匹配/冲突/创建员工
  A->>DB: 创建 auth_session
  A-->>B: 一次性会话交换码
  B->>A: 换取本系统 Token
```

外部身份分支：

```text
0 条匹配 → 创建 employee + employee_account + external_identity
1 条匹配 → 更新外部快照并登录
多条匹配 → 写 external_identity_conflict，不自动合并
```

## 4. 企业微信全量同步

```mermaid
flowchart TD
  S1[创建 FULL sync_run] --> S2[获取 access_token]
  S2 --> S3[读取部门树]
  S3 --> S4[递归调用部门成员列表]
  S4 --> S5[收集 UserID]
  S5 --> S6[逐个读取成员详情]
  S6 --> S7[Upsert 部门和员工外部快照]
  S7 --> S8[Upsert 多部门关系和主部门]
  S8 --> S9[解析 position/extattr 职级映射]
  S9 --> S10[重算自动角色]
  S10 --> S11{权限覆盖完整?}
  S11 -->|是| S12[标记确认缺失的外部记录]
  S11 -->|否| S13[PARTIAL，不停用未返回员工]
  S12 --> S14[写审计并结束]
  S13 --> S14
```

全量同步只有在确认应用可见范围完整、分页无错误、无未处理冲突时，才允许把外部确实缺失的员工标记为 `LEFT`。

## 5. 事件增量数据流

```mermaid
sequenceDiagram
  participant W as 企业微信
  participant C as CallbackController
  participant EV as sync_event
  participant Q as 异步处理器
  participant WAPI as 企业微信 API
  participant D as 领域同步服务
  W->>C: XML 回调
  C->>C: 验签 + AES 解密
  C->>EV: 保存加密原文和幂等键
  C-->>W: 快速成功确认
  Q->>EV: claim RECEIVED 事件
  Q->>WAPI: 按 UserID/部门 ID 回源详情
  WAPI-->>Q: 完整资料
  Q->>D: 统一 upsert 命令
  D->>D: 员工/部门/职级/自动角色
  D->>EV: SUCCESS 或 RETRY_WAIT
```

事件只携带 ID 时，不得把缺失字段写成空值覆盖已有外部快照。

## 6. 员工匹配与冲突流

```mermaid
flowchart TD
  I[provider + external_id] --> M{匹配数量}
  M -->|0| P[按已确认手机号辅助匹配]
  P -->|0| C[创建员工和外部身份]
  P -->|1| U[更新匹配员工]
  P -->|多条| X[写冲突表并暂停身份同步]
  M -->|1| U
  M -->|多条| X
```

姓名永远不能作为自动合并依据。冲突不阻断其他员工同步，当前已有员工账号可以继续使用。

## 7. RBAC 权限计算数据流

```mermaid
flowchart LR
  E[employee] --> SR[主体角色绑定]
  D[员工部门关系] --> SR
  G[job_grade] --> SR
  SR --> R[有效 role 集合]
  R --> RR[role_resource]
  RR --> A[ALLOW 集合]
  RR --> X[DENY 集合]
  A --> F[ALLOW - DENY]
  X --> F
  F --> M[菜单/资源响应]
  F --> API[后端 API 鉴权]
  R --> DS[data_scope]
  DS --> Q[业务查询过滤]
```

有效角色必须满足：未删除、未撤销、在生效时间内、未过期。权限模块向调用方只暴露已计算的快照和判断接口，不暴露授权 SQL 中间结果。

## 8. 动态菜单数据流

```text
请求 /me/menus
→ 加载 employee 有效权限码
→ 加载有效 menu_item + resource
→ 移除不可见、禁用或无页面权限节点
→ 父节点无直接权限但有可见子节点则保留
→ 按 parent_id + sort_order 构建树
→ 返回前端
```

菜单隐藏只影响导航；页面路由和 API 仍必须分别鉴权。

## 9. API 与数据范围数据流

```mermaid
sequenceDiagram
  participant B as 浏览器
  participant G as API 网关
  participant P as PermissionService
  participant DS as DataScopeService
  participant S as 业务服务
  participant DB as 业务数据库
  B->>G: Bearer Token + 请求
  G->>P: hasPermission(employeeId, apiCode)
  P-->>G: allow/deny
  G->>DS: resolve(employeeId, resourceCode)
  DS-->>G: DataScope
  G->>S: employeeId + DataScope
  S->>DB: 加入统一过滤条件
  DB-->>S: 结果
  S-->>G: 业务响应
  G-->>B: ApiResponse
```

数据范围合并顺序：

```text
ALL > DEPARTMENT_AND_DESCENDANTS > DEPARTMENT > CUSTOM > SELF
```

`CUSTOM` 固定结构：

```json
{
  "department_ids": [10, 20],
  "employee_ids": [1001, 1002]
}
```

业务模块不能直接读取 `scope_config` 或让前端提交 SQL 条件。

## 10. 自动角色和权限缓存流

```text
企业微信职级/部门变化
→ 生成同步批次
→ 撤销当前来源失效的自动绑定
→ 保留 MANUAL 绑定
→ 写入新的 JOB_GRADE/DEPARTMENT/WE_COM 绑定
→ 失效 employee 权限缓存
→ 下一次请求重新计算
```

以下变化必须清理权限和菜单缓存：

- 员工角色变化；
- 部门角色变化；
- 职级变化；
- 职级默认角色变化；
- 角色资源变化；
- 员工部门关系变化；
- 资源/菜单状态变化。

## 11. 失败和恢复数据流

```mermaid
flowchart TD
  F[同步/事件失败] --> C{错误分类}
  C -->|临时错误| R[RETRY_WAIT + 指数退避]
  R -->|未超过次数| P[重新 PROCESSING]
  R -->|超过次数| DL[DEAD_LETTER]
  C -->|权限/参数/冲突| ER[失败记录或冲突表]
  DL --> H[管理员人工重试]
  H --> P
  P --> OK[SUCCESS]
```

失败时保持已有有效员工、部门和权限数据；权限计算或数据范围服务失败时默认拒绝，不得退化为全部允许。

## 12. 审计数据流

必须写审计的动作：

```text
登录成功/失败
账号锁定/解锁
手机号变更
外部身份绑定/解绑/冲突处理
员工状态变化
角色授予/撤销
资源/菜单变化
职级映射变化
同步开始/完成/失败/重试
企业微信配置变化
事件解密
```

审计写入与关键状态更新必须在同一业务事务中完成；异步事件的接收审计和处理审计分开记录。

## 13. 数据一致性不变量

以下规则必须在服务层测试，并由数据库约束尽量兜底：

1. 有效账号手机号不重复；
2. 员工最多一个有效主账号；
3. 员工最多一个有效主部门；
4. `employee.primary_department_id` 与主部门关系一致；
5. 外部身份多匹配不得自动合并；
6. 员工编号创建后不可修改；
7. 自动同步不得删除人工角色；
8. 未登记 API 默认拒绝；
9. 数据范围解析失败不得返回 `ALL`；
10. 同一事件只能产生一次有效领域变更。

## 14. 端到端验收场景

| 编号 | 场景 | 期望 |
| --- | --- | --- |
| DF-001 | 本地手机号登录 | 创建会话、计算权限、返回员工摘要 |
| DF-002 | OAuth 首次登录 | 回源成员详情并自动建员 |
| DF-003 | OAuth 多身份匹配 | 登录不自动合并，写冲突记录 |
| DF-004 | 全量同步权限不足 | 批次 PARTIAL，不批量停用员工 |
| DF-005 | 重复成员事件 | 只产生一次有效 upsert |
| DF-006 | 更新事件缺少手机号 | 回源详情，不清空已有外部快照 |
| DF-007 | 职级变化 | 撤销旧自动角色，保留人工角色 |
| DF-008 | API 无权限 | 后端返回 AUTHORIZATION_DENIED |
| DF-009 | 数据范围 CUSTOM | 查询只覆盖配置的部门/员工 |
| DF-010 | 处理失败 | 指数重试，超过上限进入死信 |
