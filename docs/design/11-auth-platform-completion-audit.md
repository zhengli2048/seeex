# 认证基础平台完成度与应用接入审计

审计日期：2026-11-16。审计方式：只读代码、类型、接口、迁移与设计文档，不启动项目，不执行构建、测试、数据库迁移或外部服务联调。

本文对照用户最新目标，区分代码已具备的能力、静态检查发现的断点，以及仍需交付的能力。存在页面、接口或数据表，只表示相应代码已编写，不能据此宣称运行验收通过。

## 1 平台定位与完成判定

Seeex 的目标是统一认证与基础能力平台。企业微信作为身份和组织来源，员工、部门、职级、角色、资源、文件与审计由基础平台管理；业务系统以独立应用接入。一个应用可以同时提供 Web 与后台管理入口。

用户应看到以下导航结构，节点均由服务器根据当前身份与有效权限生成：

```text
工作台
基础平台
  组织管理
    员工 / 部门 / 职级
  权限管理
    角色 / 资源 / 权限分析
  应用管理 / 企业微信 / 文件中心 / 审计日志
应用 A
  Web
    分类
      页面 / 子页面
  后台管理
    分类
      页面 / 子页面
应用 B
  ...
```

“类似钉钉”在本文中指统一入口、应用注册、身份接入、独立应用权限、按用户展示应用与菜单。当前系统采用自定义 SSO 交换协议，本文不将其称为 OIDC、OAuth 授权服务器或完整商业身份产品。

完成闭环必须同时满足：

1. 平台管理员可以在页面新增应用、维护地址与负责人、登记不同端的路由与权限、审核发布和停用。
2. 用户只能看到自己有权访问的应用、端、分类和页面；深层授权节点保留必要祖先，空分组不显示。
3. Web 与后台是独立授权维度，不能因拥有 Web 页面权限而自动获得后台入口。
4. 进入应用通过短期单次凭证证明身份；目标应用的每个敏感 API 继续独立鉴权。
5. 用户停用、权限撤销、应用停用可以影响后续鉴权；目标应用不能长期信任登录时保存的菜单列表。
6. 查询失败、提交失败、无权限、登录失效和网络错误均有可见反馈，不能以空表格或无响应代替错误。

## 2 初次静态审计支持矩阵

本节记录本轮修改前已核实的基线；本轮落地项见第 7 节，不能将基线缺陷与修复后状态混为一谈。

| 能力 | 初次审计状态 | 已具备的代码 | 尚缺的闭环 |
|---|---|---|---|
| 本平台登录与会话 | 部分具备 | 登录、刷新、注销、JWT、数据库会话及员工启用状态检查 | 外部应用会话与平台注销的持续关联、标准联邦协议未实现 |
| 当前用户有效权限 | 已编写 | 人员角色、部门角色、职级默认角色、人员直接授权；ALLOW 合并、DENY 优先；角色、人员、资源状态过滤 | 数据范围未落实到业务查询；不能代替外部应用 API 授权 |
| 页面新增和编辑应用 | 已编写 | 应用编码、名称、描述、类型、Logo、负责人、状态、Web/后台/API/仓库地址、SSO 回调路径 | 多管理员、成员、策略独立页面与应用归属授权 |
| 应用版本接入 | 已编写 | 手工与 CI 上报、CI 凭证摘要、版本保存、审核、发布、灰度、回滚、当前版本指针 | 菜单层级、端和分类最初没有进入元数据模型 |
| 用户可见应用 | 部分具备 | `/me/apps` 根据有权访问的路由过滤应用 | 最初没有独立端权限与分类层级，也不是完整导航聚合接口 |
| 用户可见菜单 | 部分具备 | `/me/menus` 过滤启用应用、发布版本、灰度、visible 与有效权限码 | Sidebar 仍用静态平台菜单，外部路由平铺在“已接入应用”下 |
| Web 与后台入口 | 配置具备、授权不足 | 两种入口地址及 `/sso/authorize?entry=WEB/ADMIN` | UI 调用默认 WEB；后端两种 entry 复用相同路径检查 |
| SSO 单次交换 | 已编写 | 真实员工身份、60 秒码、数据库只存摘要、应用校验、过期检查、条件更新防重放 | 最初只返回身份，未在消费时重新检查员工和入口权限，无外部应用持续授权接口 |
| 外部应用后端授权 | 缺失 | 平台内部 `ManagementAuthorizationService` 可按权限码检查 | 缺独立应用服务凭证、本应用权限接口、逐 API 检查契约 |
| 资源树与菜单一致性 | 不完整 | 资源有父子关系与授权树，路由发布可同步 MENU 资源 | 可见菜单最初读取发布 JSON，不读取资源树；手工隐藏、移动、改名与路由发布结果可能不一致 |
| 数据范围 | 仅有骨架 | `SELF/CUSTOM/DEPARTMENT/DEPARTMENT_AND_DESCENDANTS/ALL` 枚举及最宽范围合并 | 部门/员工集合始终为空；无业务查询消费，不得标为已完成 |
| 前端错误反馈 | 部分具备 | API 包装失败转 Promise reject，401 刷新；部分页面 Alert、Message 与表单顶部错误 | 最初没有 Query/Mutation 统一兜底，部分异步刷新/查询没有可见错误处理 |

## 3 已确认断点与代码证据

### 3.1 导航不是按应用分类的服务器菜单树

初次读取的 Sidebar 同时请求权限与 `/me/menus`，仍通过 `menuGroups` 决定基础导航，外部应用所有路由放到同一个子菜单。静态配置标签为“系统平台”，也未表达用户要求的“基础平台 → 分类 → 页面”结构。

- `seeex-tech-ui/src/layouts/Sidebar.tsx` 初次审计第 16–19、28–40 行。
- `seeex-tech-ui/src/config/menu.ts` 初次审计第 7–22 行。
- 修复设计已经要求 MENU 资源组树、保留授权子节点祖先、删除静态菜单源；参见 `docs/superpowers/specs/2026-11-16-seeex-console-repair-design.md` 第 124–131、158–159 行。最新用户要求进一步明确应用直接作为一级分组，优先于“已接入应用”的旧展示约定。

初次 `RouteMetadata` 只有 `routeCode/name/path/permissionCode/icon/openType/visible/sortOrder`；缺少 `entry`、父路由和分类。发布时所有路由同步为平级 MENU 资源，因此仅换用 Tree 组件不能修复数据模型缺口。

- `seeex-tech-backend/web/src/main/java/com/seeex/tech/web/registry/ApplicationRegistryService.java` 初次审计第 261–271、340–352 行。

### 3.2 Web 与后台没有独立权限边界

初次 `/sso/authorize` 接受 `entry`，但只按 `appCode + redirectPath` 查找有权限的菜单，再选择 Web 或后台地址。同一路径出现在两种端时，Web 页面权限即可通过后台入口授权检查。该结论指平台入口门禁不足；目标应用自身是否拒绝敏感操作，需要目标应用独立鉴权，不能由中心菜单过滤推断。

- `seeex-tech-backend/web/src/main/java/com/seeex/tech/web/registry/ApplicationRegistryController.java` 初次审计第 112–124 行。

初次 `openApplication` 默认 `entry=WEB`，所有使用点均未传递后台类型；同时始终新开窗口，未执行元数据 `CURRENT_TAB/NEW_TAB/EXTERNAL` 的打开语义。

- `seeex-tech-ui/src/lib/management.ts` 初次审计第 38–46 行。
- `seeex-tech-ui/src/features/applications/ApplicationsPage.tsx` 初次审计第 99 行。
- `seeex-tech-ui/src/features/dashboard/DashboardPage.tsx` 初次审计第 26 行。

### 3.3 SSO 证明身份，不能证明之后每次操作仍被允许

初次交换码只绑定 `subjectId/appCode/redirectPath`。消费时校验应用、时限和重放，返回身份、应用、路径与到期时间；没有入口维度、当前有效权限复核、应用服务凭证或持续授权查询。平台会话 JWT 也未构成目标应用可直接使用的、面向各应用受众的授权令牌。

- `seeex-tech-backend/web/src/main/java/com/seeex/tech/web/registry/ApplicationRegistryService.java` 初次审计第 196–226、353–354 行。
- `seeex-tech-backend/web/src/main/java/com/seeex/tech/web/auth/JwtService.java` 第 26–39 行。

目标应用不能通过“平台曾显示过菜单”“用户曾成功 SSO”“本地会话仍存在”来放行敏感 API。SSO 建立登录态后，还要按本应用资源权限继续鉴权；权限撤销后，下一次敏感请求应重新得到拒绝。

### 3.4 菜单与资源有两个可变来源

初次 `menus()` 读取当前发布版本的路由 JSON；资源页更新的 `visible/parentId/name/routePath/sortOrder` 不直接驱动该菜单。权限计算只过滤 ACTIVE 与未删除，不因资源 `visible=false` 自动撤权；这是合理的权限/可见性区分，但菜单端必须读取正确的可见性来源。

发布同步又将已有资源置为 ACTIVE，覆盖名称、路径、图标和可见性，且没有停用不再出现的旧路由资源。因此需要明确发布元数据与平台人工资源维护各自的字段所有权，避免“隐藏后仍显示”或“停用后被重新启用”。

- `seeex-tech-backend/web/src/main/java/com/seeex/tech/web/registry/ApplicationRegistryService.java` 初次审计第 186–193、261–271 行。
- `seeex-tech-backend/web/src/main/java/com/seeex/tech/web/rbac/PermissionService.java` 第 151–153 行。

### 3.5 应用负责人只是资料字段

初次应用管理接口检查全局 `platform:apps:*` 权限，负责人只保存到应用记录。没有按 appCode 限定的管理员关系及“仅管理自己的应用”校验，应用详情也没有管理员、成员、策略页面。这是完成度缺口，不应把负责人表单等同于应用级管理权限。

- `seeex-tech-backend/web/src/main/java/com/seeex/tech/web/registry/ApplicationRegistryController.java` 第 28–92 行附近的管理操作权限检查。
- `seeex-tech-backend/web/src/main/java/com/seeex/tech/web/registry/ApplicationRegistryService.java` 初次审计第 275–286 行。
- `seeex-tech-ui/src/features/applications/ApplicationsPage.tsx` 初次审计第 94–115 行。

### 3.6 数据范围没有业务执行闭环

`DataScopeService.resolve` 只计算最宽枚举，总是返回空 `departmentIds/employeeIds`；`PermissionService.resolveDataScope` 之外未检索到业务使用点。CUSTOM 指定集合、部门后代展开、业务查询约束和越权访问拒绝仍未完成。

- `seeex-tech-backend/web/src/main/java/com/seeex/tech/web/rbac/DataScopeService.java` 第 20–35 行。
- `seeex-tech-backend/web/src/main/java/com/seeex/tech/web/rbac/PermissionService.java` 第 99–101 行。
- `docs/plans/seeex-console-button-contract.md` 初次审计第 226 行也将此列为未完成。

## 4 应用与导航字段契约

以下为交付时必须核对的字段语义；最终名称应与源码 DTO 同步，不能只写文档而不校验接口参数。

| 对象 | 必需字段或维度 | 约束与使用 |
|---|---|---|
| 应用 | appCode、name、status、baseUrl、adminUrl、apiUrl、ssoCallbackPath、logo、description、ownerEmployeeId | appCode 稳定唯一；HTTP(S) 地址验证；停用后导航、SSO、服务端授权均拒绝；无后台地址不能显示后台入口 |
| 应用类型 | type | 类型描述应用业务属性；不能用 WEB/ADMIN 类型字段取代实际入口授权维度 |
| 路由 | routeCode、name、path、permissionCode、entry、parentRouteCode、category、icon、openType、visible、sortOrder | entry 为 WEB 或 ADMIN；父子必须同应用、同端；禁止自指、循环、悬空父级；路由路径按端去重，routeCode 按明确范围唯一 |
| 权限资源 | applicationCode、resourceType、permissionCode、parentId、status、visible | MENU/BUTTON/API 的显示与授权职责独立；菜单隐藏不代表 API 公开，菜单显示也不代表 API 放行 |
| 导航节点 | 稳定 key、名称、类型、图标、children、应用/端/类别、path、openType | 服务器完成授权过滤与保留祖先；前端递归渲染，不另行维护一套应用菜单 |
| SSO 码 | 摘要、subjectId、appCode、entry、redirectPath、expiresAt、usedAt | 单次消费，短期有效；消费时重新检查员工、应用和对应端路径权限 |
| 应用服务凭证 | 应用绑定、摘要、用途、创建/轮换时间 | 与 CI 凭证及用户 JWT 分开；轮换后旧凭证立即失效；只在创建/轮换响应中展示原文 |
| 授权结果 | subjectId、appCode、effective permission codes 或 allowed、资源/端信息 | 只返回调用应用自己的有效权限；不能跨应用探查，也不能仅凭请求方提交 appCode 信任其身份 |

## 5 外部应用后端接入契约

推荐闭环为：中心登录 → 用户选择有权访问的应用端与页面 → 中心生成单次码 → 目标应用服务端交换身份 → 目标应用建立本地会话 → 每次敏感 API 使用本地会话内的稳定 subjectId 向中心查询或检查本应用权限。

必须落实以下边界：

1. 浏览器不得保存或提交应用服务凭证；凭证只能在目标应用服务端使用。
2. 目标应用的业务 API 不得信任客户端提交的 subjectId、appCode、role 或 permission；subjectId 来自已经验证的本地会话。
3. `POST /api/app-auth/permissions` 使用 `X-App-Code/X-App-Secret` 与 `{subjectId, entry}`，返回该应用、该端的 `allow/deny/effective/checkedAt`；`POST /api/app-auth/check` 增加 `permissionCode`，返回明确的 `allowed`。这两个接口只信任已验证的应用服务凭证。
4. 应用不能用自己的凭证查询另一应用的权限。CI 凭证仅用于元数据上报，不能交换身份或查询业务权限。
5. 中心拒绝、超时或不可用时，目标应用敏感 API 应拒绝本次操作，并返回可识别错误；不能将失败当成默认允许。
6. 如引入缓存，必须明确最长陈旧窗口与撤权策略；未落实撤权通知或版本失效前，不得宣称实时撤权。
7. 按钮、页面、API 权限分别注册和检查。菜单过滤只改善导航体验，API 继续由服务端强制检查。
8. 交换响应中的过期时间不代表目标应用会话自动被中心统一注销；全局登出、应用本地会话撤销、应用会话审计需要独立实现。
9. 数据范围在业务查询接入前仍是未完成项；仅返回权限码不代表已经限制了员工、部门和文件的数据可见范围。

## 6 页面按钮与错误反馈验收

### 6.1 应用接入与导航按钮

| 页面/动作 | 权限与参数 | 前端交互与验收 |
|---|---|---|
| 新增应用 | `platform:apps:create`；应用资料字段 | 页面抽屉校验必填、长度和 URL；保存中禁重提；失败保留已填内容；创建后只显示一次凭证 |
| 编辑应用 | `platform:apps:update`；固定 appCode、可编辑资料 | 编码只读；地址更新后刷新详情和导航；停用需明确确认与后端强制阻断 |
| 查看详情 | `platform:apps:view` | Web、后台、API、回调、负责人、状态和当前版本显示完整；不回显凭证 |
| 添加路由 | `platform:apps:update`；entry/category/parentRouteCode 与路由字段 | 同端 Tree/TreeSelect 选父级，排除本节点与后代；显式展示端、分类、排序和可见开关 |
| 移除路由 | 同上 | 至少保留合法元数据；移除父节点时明确处理子节点，不产生孤儿节点 |
| 上报版本 | CI 凭证或有权手工录入 | 字段校验失败定位到路由行；区分上报成功与已审核发布 |
| 审核通过/拒绝 | `platform:apps:review` | 对象和版本清晰；失败不误报成功；当前版本限制与后端规则一致 |
| 发布/回滚 | `platform:apps:publish` | 审核门禁；操作确认；成功后刷新当前版本、权限资源及导航 |
| 调整灰度 | `platform:apps:review`；0–100 整数 | 不因输入中间态反复提交；确认当前百分比，失败保留真实服务端值 |
| 轮换 CI 凭证 | `platform:apps:ci` | 明确旧凭证失效；仅显示一次；复制失败可手工复制；不得写入日志或 URL |
| 轮换应用服务凭证 | `platform:apps:auth`；POST `/platform/apps/{appCode}/service-credentials/rotate` | 与 CI token 分开说明用途；只在服务端配置，不提供浏览器持久保存 |
| 打开 Web 页面 | 当前 WEB 路由权限；appCode/entry/redirectPath | 采用 WEB 登记地址；尊重 openType；SSO 失败关闭预开空白页并给出原因 |
| 打开后台页面 | 当前 ADMIN 路由权限；appCode/entry/redirectPath | 只有 ADMIN 有权节点可见；拥有同路径 WEB 权限不得通过后台 SSO |
| 展开导航树 | 服务器导航节点 | Arco `Menu.SubMenu/Menu.Item` 或 Tree 的 key 唯一稳定；祖先可展开，分类不触发 SSO，叶节点才跳转 |
| 刷新/重试导航 | 当前登录身份 | 加载、空权限与加载失败状态分开；失败提供重试；不能静默显示过期权限菜单 |

### 6.2 错误反馈验收矩阵

初次审计中 `api.ts` 已把业务错误转为拒绝，部分操作也调用 Message；因此不能说完全没有错误代码。但“Promise 被拒绝”不等于“用户看到了错误”。验收须沿按钮、查询与异步刷新逐项确认。

| 场景 | 必须看到的行为 |
|---|---|
| HTTP 200，但业务 code 非 OK | 按失败处理；不触发成功提示或关闭编辑器；展示服务端安全错误信息 |
| 400/422 参数错误 | 表单字段或路由行定位错误，保留输入；无法定位时在表单顶部显示原因 |
| 401 可刷新 | 合并并发刷新请求，只重试一次；刷新成功恢复原请求 |
| 401 刷新失败 | 清除登录信息并进入登录页；统一提示登录失效，避免多个重复 toast |
| 403 | 提示无操作权限；不混淆为“无数据”；必要时刷新权限/导航并保留当前上下文 |
| 404/409 | 提示对象不存在、已变更或冲突；可刷新重查，不能显示操作成功 |
| 429 | 提示请求过于频繁，避免按钮连续重试；后端有等待信息时展示 |
| 500/网络断开/超时 | 提示服务或网络错误；查询提供重试，提交保留表单；提供 request_id 便于排查 |
| 后台查询/刷新失败 | 页面 Alert 或统一错误兜底；不得只在控制台报错或静默空白 |
| SSO 失败/弹窗被阻止 | 提示失败原因；授权失败关闭空白窗；按打开策略执行可用的跳转回退 |
| Clipboard/下载失败 | 有明确错误或可替代操作；不提前显示复制/下载成功 |
| 组件运行异常 | 有错误边界和恢复入口；不能整页白屏 |

静态依据：初次 `seeex-tech-ui/src/lib/api.ts` 第 41–71 行具有拦截和错误文案函数；`src/app/AppProviders.tsx` 第 6 行仅创建默认 QueryClient，尚未提供全局 Query/Mutation 错误兜底。最终是否逐项闭环，以第 7 节与当前源码为准。

## 7 本轮落地与静态复核记录

| 改进项 | 当前记录 | 验证边界 |
|---|---|---|
| 工作台/基础平台/各应用导航聚合 | 已新增 `NavigationService/NavigationController`，GET `/api/me/navigation`；Sidebar 已对接 | 已静态核对字段；未启动项目、未进行视觉或实际点击验收 |
| 路由端、分类、父子层级与前端树 | 已新增 entry/category/parentRouteCode、资源所属端/分类、Arco 菜单与父级选择树 | 旧版本默认 WEB/常用功能；无自身权限的合法祖先保留为 GROUP；隐藏/停用/删除/缺失/跨应用祖先阻断子树；手工 MODULE 须与子级同端、同分类 |
| WEB/ADMIN 独立 SSO 授权 | 已按 appCode+entry+path 校验；交换码绑定 entry；消费后以当前权限复核 | 同一路径两端需要不同权限码；未实际交换联调 |
| 独立应用服务凭证与持续授权接口 | 已编写独立凭证创建/轮换、交换凭证校验和 `/api/app-auth/permissions`、`/check` | 只返回本应用本端权限；目标应用仍须逐敏感 API 调用；不标为 OIDC |
| 资源树驱动当前菜单 | 当前发布版本限制可用路由身份，资源表提供人工维护后的名称、路径、可见性、排序和层级；导航支持手工 MODULE | 发布重新同步会覆盖已登记路由属性及父级；版本移除资源标为 INACTIVE。字段所有权已明确：资源调整即时生效，下次发布以该版本元数据覆盖；新权限身份不继承旧授权 |
| 查询/操作统一错误反馈 | 已编写 ApiError 归一、Query/Mutation 全局提示、提示去重、401 刷新与登录失效提示、导航重试；登录与根路由增加受控错误边界和 404 页面 | 接口未返回字段错误时只能显示表单顶部错误；request_id 只在服务端提供时展示；错误页支持重试、刷新与返回入口，不显示原始异常或堆栈；未实际触发渲染验收 |
| 数据范围落实到业务查询 | 未完成 | 不因新增授权接口而标为完成 |
| 应用多管理员/成员/策略独立页 | 未完成 | 负责人资料字段不等于应用管理关系 |
| 统一注销各应用本地会话 | 未完成 | 目标应用会话需独立接入撤销协议 |
| 目标应用回调与每个敏感 API 实际接入 | 需目标应用实现与后续联调 | 平台新增接口不代表所有外部应用已完成接入 |

### 7.1 统一导航前端契约

`GET /api/me/navigation` 不接受 subjectId 等身份参数，身份来自已验证登录会话；返回 `ApiResponse<NavNode[]>`。工作台始终存在，基础平台与外部应用的空分组不返回。

```json
{
  "key": "app:recruitment:route:positions",
  "label": "岗位管理",
  "kind": "EXTERNAL",
  "appCode": "recruitment",
  "entry": "ADMIN",
  "path": "/positions",
  "openType": "NEW_TAB",
  "children": []
}
```

- `kind=GROUP`：只展开容器，`path/openType` 为 null，不发起 SSO。
- `kind=INTERNAL`：使用前端路由跳转 `path`。工作台为 `/`；权限分析已核对真实路由为 `/permissions`。
- `kind=EXTERNAL`：把 `appCode/path/entry/openType` 传给应用打开函数，再通过 SSO 授权跳转。
- 可点击 MENU 也可能有 children；Sidebar 以“打开某页面”子项保留父页操作。无自身权限但有授权后代的合法祖先转为 GROUP。
- 每个外部应用单独作为顶层，顺序为应用编码；应用内 WEB 业务端优先、ADMIN 管理后台其次；分类按其根节点排序稳定生成，分类内按 sortOrder 与稳定 key 排序。
- 资源/模块隐藏、停用、软删除、父级缺失、跨应用父级不会提升子节点。跨端、跨分类父子以及不可达循环不显示；递归深度上限 64，损坏数据不形成无限递归。
- 基础平台普通页面地址完全来自资源表。只有始终显示的工作台、复用员工查看权限的权限分析是显式页面关联。

源码位置：`seeex-tech-backend/web/src/main/java/com/seeex/tech/web/navigation/NavigationService.java`、`NavigationController.java`；前端消费位置：`seeex-tech-ui/src/layouts/Sidebar.tsx`。本节属于静态检查记录，运行验收保持未完成。

## 8 相关基线

- [当前模型基线](./00-current-model.md)
- [RBAC 设计](./02-rbac-authorization.md)
- [认证与回调契约](./05-auth-and-callback-contract.md)
- [控制台修复设计](../superpowers/specs/2026-11-16-seeex-console-repair-design.md)
- [按钮级功能与接口清单](../plans/seeex-console-button-contract.md)
- [应用路由接入说明](../integration/application-route-metadata.md)

若历史文档与本文的最新平台定位冲突，以用户最新要求为准；接口事实以当前源码为准。本文不会因完成静态编辑而将未运行验收的项目标记为验证通过。


最终静态复核：补齐 SSO 回调查询串/片段及长度校验、路由空项和 icon/gitCommit 长度校验；发布资源编码包含所属端与权限身份，避免改端/改权限时碰撞。修正权限分析 Empty 导入；API/刷新超时20秒。相对导入、重复/未使用导入文本扫描、Java括号、POM XML和关键文件 diff 空白检查未发现问题。以上不属于编译、类型检查或运行验证。完整页面剩余项见实施清单第15节。


后续静态实现更新：应用管理员、权限策略、实际数据范围及文件应用归属已继续开发，旧审计矩阵的未完成状态属于此前基线。当前按钮、接口、实现范围与外部接入限制见 [应用治理实施契约](../plans/seeex-application-governance-contract.md)。本更新仍不代表运行通过。


后续静态更新：组织字段/统计、企微职级映射/同步差异预览、审计筛选/CSV导出已继续编写，状态与契约见 [组织、企微与审计实施说明](../plans/seeex-organization-wecom-audit-contract.md)。旧未完成描述属之前基线，不据此宣称运行通过。
