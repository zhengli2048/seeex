# 新应用申请、审批、办理与定向交付：实现及按钮契约

日期：2026-11-17。状态：本轮已编写前后端、迁移和回归测试源码，并做静态检查；未编译、类型检查、执行测试、启动、迁移或真实服务联调。

## 1 实际实现范围与方案调整

原第12号方案推荐Flowable+bpmn-js。本轮实际采用 **MyBatis-Plus持久化的受限顺序审批状态机 + Arco Tree/属性面板**，没有集成Flowable或BPMN设计器，也不提供任意流程表达式、脚本、Java委托和HTTP节点。原因是在明确禁止运行的条件下不把引擎/Jackson/事务兼容性作为未经核对的隐含依赖。此调整是实现选择，不能把原计划的引擎接入勾为完成。

已覆盖：申请草稿、预检、固定版本提交、ALL/ANY、条件跳过、固定员工/角色继承成员/部门负责人、兜底、退回重提、撤回、历史修订与审批图、本人待办、平台办理、注册与管理员绑定、加密一次领取、确认收件、轮换补发、站内消息、可选企微Outbox、超时提醒、候选/身份异常与运维恢复、未注册前的取消。

首期采用更保守的安全规则：所有新接入路径都必须保留安全审批节点，不能只靠申请人填TEST/INTERNAL绕过审核；至少一个无条件必经审批节点。环境/数据级别/应用类型条件仍可控制额外节点，条件类别之间AND、类别内部OR。没有通用风险表达式引擎。

## 2 页面与每个主要按钮

| 页面 | 按钮/交互 | API及参数 | 权限/状态/失败处理 |
|---|---|---|---|
| 我的申请 `/application-requests` | 查询、状态/起止时间、分页、刷新、重置 | GET `/api/me/app-requests`：page/pageSize/state/keyword/from/to | own:view，强制本人，不接受scope扩大；错误态重试，不伪装空列表 |
| 新建/复制申请 | 新建、复制为新草稿、保存草稿、取消 | POST `/api/app-requests`；Idempotency-Key | create+own:view+本人交付/通知读取权；申请人由会话固定；复制清空编码，需重新选择 |
| 草稿表单 | 编辑、人员搜索、域名标签、附件搜索/翻页/添加/移除 | PUT `/api/app-requests/{id}/draft`：form、expectedVersion；GET `/api/onboarding/options` | 本人DRAFT/RETURNED；名称字典50项，已选回填；保存失败保留输入及原版本；附件仅本人已确认平台文件 |
| 草稿/详情 | 预检并确认提交、重新载入并预检 | POST `/api/app-requests/{id}/validate`：expectedVersion | 检查身份、材料、编码、有效环境流程和候选；纯只读，显示流程版本、节点候选、办理人与运维员 |
| 提交确认 | 确认提交审批 | POST `.../{id}/submit`：expectedVersion、definitionId；Idempotency-Key | 再次校验并锁定环境绑定，预留编码、提交修订、任务同事务；流程变化409须重新确认 |
| 申请详情 | 删除草稿、撤回、复制 | DELETE `.../{id}/draft?expectedVersion`；POST `.../{id}/withdraw`：expectedVersion/comment | 删除仅从未提交DRAFT；撤回仅IN_APPROVAL且原因必填；退回重提重新经过全部必要审批 |
| 我的审批 `/approval-tasks` | 待处理/已处理切换、分页、刷新、查看材料 | GET `/api/me/approval-tasks`：category=PENDING/DONE、page/pageSize | tasks:view，只查本人候选快照；任务动作还检查当前角色/部门/员工资格 |
| 审批详情 | 领取任务、通过、驳回、退回补充 | POST `/api/approval-tasks/{id}/claim`；`/decision`：expectedVersion/decision/comment | tasks:act+当前资格；排除申请人；先领取再决策；驳回/退回意见必填≤1000字；旧任务409 |
| 审批图/历史 | 节点展开、任务和意见分页、查看历史尝试 | GET `/api/app-requests/{id}`；`/{id}/attempts/{attempt}` | 同一申请ACL；历史任务allowedActions为空；条件跳过不显示成审批通过 |
| 平台办理 `/application-fulfillments` | 查询、分页、刷新、领取办理 | GET `/api/application-fulfillments`；POST `/{id}/claim`：expectedVersion | fulfill:view/act+本人候选快照及实时来源；不允许申请人自己办理 |
| 办理表单 | 填写/编辑、保存草稿、放弃、校验已保存配置 | PUT `.../{id}/config`：form/expectedVersion；POST `/validate` | 仅当前领取人；允许保存不完整草稿但有长度限制；完成前地址须匹配批准域名/端；生产地址HTTPS |
| 办理确认 | 完成办理并交付 | POST `.../{id}/complete`：expectedVersion；Idempotency-Key | 前端先校验同版本已保存配置，后端再严格校验；创建应用/管理员/密文/站内消息同事务；响应无明文凭证 |
| 我的交付 | 查看普通接入信息、刷新状态 | GET `/api/me/app-deliveries/{requestId}` | own:view且recipient=原申请人；无管理员代领；no-store |
| 交付弹窗 | 领取敏感信息、显示/遮罩、分别复制、关闭清除 | POST `.../{id}/redeem`：deliveryId/expectedVersion；Idempotency-Key | CAS只消费一次；Secret不进入React Query缓存/URL/localStorage；关闭、页面隐藏、离开、身份切换或3分钟后清理；复制失败可手动复制 |
| 收件 | 确认收件 | POST `.../{id}/acknowledge`：deliveryId/expectedVersion | 必须先领取；确认后COMPLETED并解除首次交付门禁；已读通知不是收件 |
| 补发 | 轮换并补发、确认旧凭证失效 | POST `/api/application-fulfillments/{id}/reissue`：expectedVersion/comment/confirmRotation=true；Idempotency-Key | reissue+办理资格；仍只给原收件人；两个旧凭证立即失效；重复请求不重复轮换 |
| 异常管理 `/application-requests/manage` | 全量查询、恢复异常、取消未交付申请 | POST `/api/app-requests/{id}/recover`；POST `/api/application-fulfillments/{id}/cancel` | manage且为固定流程配置的实时运维候选；原因必填；未通过恢复重开完整审批，不能直接批准；已注册应用不可取消释放编码 |
| 流程配置 `/workflow-definitions` | 新建、编辑草稿、复制新版本、历史版本、查看模型 | GET/POST `/api/workflow/definitions`；POST创建携带Idempotency-Key；GET `/{id}`；PUT `/{id}/draft`带expectedVersion | view/write分开；已发布模型不可改；版本号服务端生成；创建键按创建人唯一且绑定正文摘要；草稿结构必须可安全渲染 |
| 流程编辑器 | 增删、上移/下移、选择节点、撤销/重做、候选/条件/提醒配置、保存 | model.nodes、fulfillers、operators | 最多20节点，编码唯一；树key与临时编辑编码分离；无脚本输入；办理/本人交付不能删除 |
| 模型校验/预览 | 校验已保存草稿、预览样例路径 | POST `/{id}/validate`：expectedVersion；`/preview`：ApplicationForm | write；不启动申请/通知；显示实际匹配候选并排除申请人；缺候选/兜底报错 |
| 发布 | 选择环境、查看旧绑定和节点/顺序/人员来源差异、填写原因、发布 | POST `/{id}/publish`：expectedVersion/expectedBindingVersion/environments/comment | publish；环境绑定集合CAS，差异读取成功才能确认；只影响新提交 |
| 停用 | 停用新申请入口、填写原因 | POST `/{id}/disable`：expectedVersion/expectedBindingVersion/comment | publish；解绑新实例入口，在途按固定版本继续 |
| 通知中心 `/inbox` | 全部/未读、分页、刷新、查看申请、标记已读 | GET `/api/me/inbox`；POST `/{id}/read`：expectedVersion | own:view，强制本人，内容无凭证；目标申请再次校验ACL |
| 通知运维 `/notification-jobs` | 筛选、分页、刷新、重试原通知 | GET `/api/notification-jobs`；POST `/{id}/retry`：expectedVersion/comment | operate；仅死信/跳过/关闭状态可重排；不重建应用、不轮换凭证；外部渠道需先启用 |
| 应用中心 | 申请接入、紧急直建、编辑/轮换门禁 | 普通入口跳到申请；POST `/api/platform/apps?reason=...` | 直建仅override且原因必填，不默认授予；不能抢占活跃/已注册编码；首次交付未确认时普通编辑和轮换禁用 |

所有API继续使用code/message/data；分页items/total。写操作失败不显示成功，不自动重试副作用。幂等键只在当前页面内存中绑定动作+正文；同键不同对象或正文409。任务过期、并发动作、草稿版本过期需明确重新载入；不能后台刷新后静默更新草稿版本。

## 3 字段与上限

申请：name≤128，proposedAppCode=`[a-z][a-z0-9-]{1,63}`且非platform；description≤500，businessPurpose≤2000；type=WEB/ADMIN/AI/OTHER；requestedEntries非空且包含WEB，可加ADMIN；environment=DEV/TEST/PROD；requestedDomains≤20个精确小写域名，无协议/端口/路径/通配；requestedAdminEmployeeIds包含本人、有效且唯一、≤100；dataClassification=INTERNAL/CONFIDENTIAL；attachmentKeys≤10，总计≤100MB。

申请人/主部门由服务端派生，提交后审批尝试固定修订、流程版本与主部门ID。各节点激活时固定候选任务，执行时再核对实时来源；后续节点仍按固定模型解析当前有效人员。最多50次提交/异常重开；草稿不占用应用编码，提交开始预留，退回/驳回/撤回/未注册取消释放。注册成功后编码所有权永久保留。

办理：baseUrl必填，获批ADMIN时adminUrl必填；apiUrl/repositoryUrl/logo可选，地址≤512且HTTP(S)、无userinfo/query/fragment，所有填写地址均限定批准域名；PROD使用HTTPS；ssoCallbackPath≤256、安全站内路径；integrationNotes≤2000，不填写凭证。owner、管理员、端、收件人不能在办理表改动。

节点：key≤64、唯一；name≤128；mode=ALL/ANY；reviewers来源EMPLOYEE/ROLE/DEPARTMENT_LEADER，兜底员工明确；选择器每集合≤200，实际候选≤200；dueHours=1–720，只提醒不自动通过；securityReview标识安全节点。fulfillers与operators单独配置；运维员需manage和本人通知权。

## 4 事务、ACL及并发

- 申请、任务决策、领取、办理和收件均在服务事务内，CAS先锁申请行。ALL一票拒绝，ANY首个通过取消其余、全部拒绝才驳回。退回结束本次尝试，重新提交生成不可变修订。
- 预检与正式提交分开；提交对环境绑定单独CAS加锁，发布/停用不能在它中途切换流程。版本及业务绑定的替换同事务。
- 编码主键唯一，同时约束申请与紧急直建；已注册所有权保留，避免RR旧快照在并发直建后重新预留同编码。application_registry.originating_request_id唯一。
- 外部附件读取在事务外进行，再由状态与版本确保材料未变；下载始终重新验证申请ACL、绑定及原所有者私有文件归属。文件服务只接受服务凭证，不接收浏览器自报owner。
- 注册服务生成的明文凭证只在办理事务内部传给AES-GCM服务，独立32字节密钥、随机nonce、request/recipient/version AAD和key_id。默认24小时领取，过期清除密文。GET和审计均无密文/明文。
- 领取用deliveryId+版本防止补发后版本从0开始的ABA问题；申请行CAS使领取、补发与确认串行。响应丢失不承诺可回查，需有审计的轮换补发。
- 首次交付前，应用普通编辑/CI及服务凭证轮换接口拒绝，避免旧交付包失效或信息变化。补发使用受控内部路径；确认收件与解除门禁同事务。
- 站内消息与注册同事务，外部Outbox只含摘要与站内链接；独立租约、令牌、5次退避/死信和人工重试。外部发送为at-least-once，不宣称严格一次到达。
- 定时扫描仅在实际新增提醒/异常时推进版本。会签中任何未决候选失效、或签无人有效、申请人停用、办理人失权转异常；通知原申请人及配置的有效运维员。运维不能代领密钥或直接设为通过。

## 5 发布所需配置及迁移

| 配置 | 要求 |
|---|---|
| AUTH_JWT_SECRET | 必须显式设置独立≥32字节密钥，不使用旧开发默认值 |
| FILE_SERVICE_TOKEN | web/file一致、≥32字节；取消dev-only-token默认值 |
| APP_DELIVERY_KEY | Base64编码32字节独立AES密钥，不能复用企微Secret或JWT密钥 |
| APP_DELIVERY_KEY_ID | 默认v1，部署轮换后旧版本交付需受控补发 |
| BOOTSTRAP_ADMIN_ENABLED | 默认false；只有显式启用首次引导才创建缺失管理员账号 |
| BOOTSTRAP_ADMIN_LOGIN/PASSWORD | 引导账号及独立密码；密码≥12字符且UTF-8≤72字节，无固定默认密码；已有账号不自动改密 |
| APP_ONBOARDING_WECOM_NOTIFICATIONS | 默认false；站内流程不依赖外部渠道 |
| APP_ONBOARDING_PORTAL_URL | 启用企微通知时提供真实HTTP(S)平台地址，无账号、查询串或片段 |

服务与数据库发布时应统一时区配置（当前平台以Asia/Shanghai为业务时间），并核对文件服务独立的元数据迁移。

V25新增申请、修订、任务、历史、流程、绑定、编码所有权、参与关系、交付、消息、Outbox和幂等表，以及注册来源/首次交付门禁；V26增加employees:accounts；V27增加账号凭证版本和会话绑定版本，并撤销旧web-console会话以要求重新登录。**均未执行。** 停止旧版本写入后按既有Flyway顺序发布，核对已执行历史脚本，不能忽略校验值；前后端同批发布。

没有预置可自动通过的流程，也不假设现有审核组。上线需授权申请人四项自助权限、审批人view/act、办理人view/act、流程配置/发布、异常运维与通知；配置候选/兜底/办理/运维并发布环境绑定后才能提交。紧急override不随V25默认授予平台管理员。

当前内部JSON仍使用Jackson2，已按Boot4官方兼容模块显式声明并设置MVC偏好、JavaTime与ISO日期。JSON整数字段拒绝浮点数静默截断。长期迁移Jackson3另行处理；未进行Maven依赖解析或编译验证。依据：[Spring Boot 4 JSON说明](https://docs.spring.io/spring-boot/4.0/reference/features/json.html)。Arco参数按项目声明2.66.18的官方npm类型文件（含继承的SelectViewCommonProps）只读核对。

## 6 静态证据及尚未实施的扩展

源码入口：后端`web/onboarding`、注册/账号服务、file附件引用；前端`features/onboarding`、DraftGuard及路由；V25–V27。新增回归源码覆盖会签/或签、本人禁止自批、他人交付拒绝、AAD绑定、缺少密钥、域名/端/HTTPS限制、交付失败触发代理事务回滚、旧凭证版本拒绝签发、首次收件前普通修改/轮换拒绝。

本轮只使用源码AST语法解析、record构造参数数量、Arco参数名与声明、导入/路径/XML/SQL字段/接口/状态检查及敏感模式扫描；这不是Java编译、TypeScript类型检查、测试通过、MySQL锁或浏览器交互证明。

尚无Flowable/BPMN、任意图分支/循环/并行网关、通用表单设计器、自动超时升级/转办、跨租户代办、实例热迁移、通用多渠道模板管理UI。提醒不自动审批；外部开关通过环境配置。站内链路已有源码闭环，企微实际到达与组件视觉效果仍未验证。
