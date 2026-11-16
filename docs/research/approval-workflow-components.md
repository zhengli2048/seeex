# 审批流组件调研与选型

调研日期：2026-11-16。场景已确认：员工申请一个新应用接入平台；流程审批通过后，平台办理人填写接入信息，完成注册并定向交付申请人。本轮只调研和设计，不添加依赖、不开发业务代码、不运行项目。

## 结论

推荐 **Flowable 8.0.0 开源流程引擎 + bpmn-js 受限设计器 + 现有 React / Arco 业务页面**。这是基于当前代码栈的方案选择，尚未完成本仓库依赖兼容验证。

后端负责持久化人工任务、条件分支、版本、时限、审批历史；前端画布只负责编辑和展示。业务申请、权限、办理和凭证交付由 Seeex 自己管理，不能用一张流程图代替后端审批服务。

## 当前工程与缺口

- 后端 POM 为 Java 21、Spring Boot 4.0.9、MyBatis-Plus Boot 4 starter、MySQL、Flyway；显式使用 Jackson 2 ObjectMapper。
- 前端为 React 19、TypeScript、Arco、TanStack Query；没有流程设计器依赖。
- 已有应用注册、双端地址、路由发布、应用管理员、资源策略、权限计算、审计和文件网关，可作为办理输出和授权基础。
- 未找到审批引擎、流程定义、申请单、审批任务或站内消息服务；现有“应用路由版本审核”只针对路由元数据，不能当成新应用接入审批。
- `ApplicationRegistryService.register` 生成 CI Token 与应用服务凭证，仅保存摘要并在创建响应返回明文。审批办理需要新增加密、定向、短期交付层，不能事后从摘要恢复明文。

代码入口：`seeex-tech-backend/pom.xml`、`web/pom.xml`、`web/.../registry/ApplicationRegistryService.java`、`web/.../governance/ApplicationGovernanceService.java`、`seeex-tech-ui/package.json`。

## 后端组件对比

| 组件 | 可核对事实 | 本项目取舍 |
|---|---|---|
| Flowable 开源引擎 | 开源仓库为 Apache 2.0；8.0.0 发布说明明确升级到 Spring Framework 7 / Boot 4，Jackson 3 默认并提供 Jackson 2 兼容层；提供 Java 引擎服务API | 推荐：与现有 Boot 4 方向一致，采用 BPMN 标准；需要自行实现 Arco 业务页面和受限属性面板 |
| Warm-Flow | 官方仓库为 Apache 2.0，声明支持 Java 21、MyBatis-Plus、审批操作和仿钉钉设计器；仓库有 Vue 设计器模块 | 保留备选：轻量审批有吸引力；本次证据没有确认 Boot 4 精确依赖及原生 React/Arco 设计器适配，不直接假定能无缝复用 |
| Camunda 8 | 官方许可文档明确，8.6及以上对应核心自部署组件生产使用需要企业许可；其流程编排与建模是独立产品体系 | 当前不首选：会引入生产许可与额外平台运维决策。bpmn-js 可单独使用，不要求购买或部署 Camunda 引擎 |
| LiteFlow | 官方定位是组件式规则引擎与编排 DSL | 可用于技术规则编排；据此判断它不适合作为本需求的人工审批主引擎，长驻任务和审批治理还要另建 |
| 自写状态机 | 可以覆盖固定几步，但人工待办、并发会签、重试、版本和可配置节点均要自行维护 | 不作为主方案；业务申请和交付仍保留明确状态机，与成熟引擎分工 |

事实来源：[Flowable 8 发布说明](https://github.com/flowable/flowable-engine/releases/tag/flowable-8.0.0)、[Flowable 许可](https://github.com/flowable/flowable-engine/blob/main/LICENSE)、[Flowable API](https://www.flowable.com/open-source/docs/bpmn/ch04-API)、[Warm-Flow 官方说明](https://raw.githubusercontent.com/dromara/warm-flow/master/README.md)、[Warm-Flow 许可](https://raw.githubusercontent.com/dromara/warm-flow/master/LICENSE)、[Camunda 8 许可](https://docs.camunda.io/docs/reference/licenses/)、[LiteFlow 官方仓库](https://github.com/dromara/liteflow)。表中项目适配结论是本方案的判断，不是厂商兼容承诺。

## 前端组件选择

**bpmn-js** 提供浏览器内 BPMN 编辑/查看能力，适合封装为独立 React 组件。官方许可要求保留可见的 bpmn.io 水印，不能通过主题CSS遮盖；它不是后端执行引擎，也不能直接当成完整审批系统。[组件介绍](https://bpmn.io/toolkit/bpmn-js/)、[许可证原文](https://raw.githubusercontent.com/bpmn-io/bpmn-js/develop/LICENSE)。

首期使用受限 BPMN 画布：开始、审批用户任务、条件分支、会签/或签、多出口结果、结束；办理和交付使用平台固定服务/任务，不允许随意删除。属性区使用 Arco Form、Select、TreeSelect、InputNumber、Switch；只读实例图叠加任务状态，详情历史使用 Timeline。

不同时建设“完整 BPMN 编辑器”和一套新的仿钉钉 JSON DSL。后续确需简化视图时，采用同一受限模型的展示层，先定义可无损转换的子集，不能宣称所有 BPMN 可双向转换。

## 建议依赖与兼容核对

- 后端候选：`org.flowable:flowable-spring-boot-starter-process:8.0.0`。官方此版本的 [starter POM](https://raw.githubusercontent.com/flowable/flowable-engine/flowable-8.0.0/modules/flowable-spring-boot/flowable-spring-boot-starters/flowable-spring-boot-starter-process/pom.xml) 确认该坐标存在。不直接引入 process-rest，不开放引擎原始 REST 管理接口。
- 官方 [Spring Boot 集成说明](https://www.flowable.com/open-source/docs/bpmn/ch05a-Spring-Boot) 明确支持 Boot 4；这不代表本工程的 4.0.9、MyBatis-Plus、Jackson Bean 自动装配已经验证。
- 前端候选：固定经核对版本的 `bpmn-js`、Flowable 扩展属性描述与受限校验适配层；不直接套用 Camunda/Zeebe 扩展属性当作 Flowable 元数据。
- 实施前静态核对：固定版本POM、Jackson 2/3包名、MyBatis事务工厂/DataSource、异步执行器、数据库脚本、React生命周期/CSS与组件参数。
- 引擎内部持久层与业务表分开：业务表继续 MyBatis-Plus；流程表通过引擎API维护。此第三方引擎持久层例外应在实施时同步记录 AGENTS/CLAUDE，不能让业务代码直接CRUD引擎内部表。
- 不为“最新版”使用 SNAPSHOT；精确版本在开发前锁定。本轮不安装依赖、不执行依赖树、构建或迁移。

## 通知组件

首期必须交付持久化站内消息和申请人专属交付页；企业微信通知为可配置适配器，失败不回滚审批或注册结果。当前只有企微认证/通讯录适配，尚无消息发送服务，不能视为已具备。

企微消息只包含申请编号、状态和需要登录的站内链接，不包含 CI Token 或服务凭证。使用已验证的员工外部身份绑定映射收件人，不接受浏览器传入任意企业微信 userId。消息应用凭证及发送权限单独核对，不能假定通讯录 Secret 可直接复用。

企业微信应用消息文档入口为 [发送应用消息](https://developer.work.weixin.qq.com/document/path/90236)，本次未成功获取该页面正文，因此不据此锁定具体SDK、请求参数和权限范围；实施阶段需要完成该接口官方契约核对。站内交付不依赖此渠道先成功。

## 调研边界

本次完成的是官方文档/许可证/版本POM和本地源码调查，没有运行候选组件、验证流程执行、联调通知或出具许可法律意见。完整业务状态、页面按钮、接口和验收要求见 [应用申请与审批设计](../design/12-application-approval-workflow.md)。
