# Seeex 设计文档索引

这些文档共同构成 Seeex 权限与企业微信集成的设计基线。需求文档决定目标和规则，设计文档决定数据、接口、状态和实现边界。

## 文档顺序

1. [01-database-schema.md](./01-database-schema.md) — 数据模型和字段语义
2. [01-database-schema-sql.md](./01-database-schema-sql.md) — PostgreSQL 字段、约束和索引设计
3. [seeex-rbac-schema.sql](./seeex-rbac-schema.sql) — 独立 SQL 草案
4. [02-rbac-authorization.md](./02-rbac-authorization.md) — 权限计算、数据范围和菜单过滤
5. [03-wecom-adapter.md](./03-wecom-adapter.md) — 企业微信适配器协议边界
6. [04-wecom-sync-state-machine.md](./04-wecom-sync-state-machine.md) — 同步、事件、重试和死信
7. [05-auth-and-callback-contract.md](./05-auth-and-callback-contract.md) — 认证和回调接口契约
8. [06-wecom-java-adapter-dtos.md](./06-wecom-java-adapter-dtos.md) — Java DTO 与 Adapter 接口
9. [07-end-to-end-data-flows.md](./07-end-to-end-data-flows.md) — 端到端数据流和验收场景

## 外部事实来源

[企业微信官方 API 核对记录](../research/wecom-official-api.md)

企业微信接口、参数、返回字段和事件格式以该研究记录引用的官方链接为准。实现前必须再次确认官方当前版本，尤其是部门列表分页字段、通讯录权限和职级/自定义字段能力。

## 实现依赖关系

```text
需求规格
  ↓
数据库模型 / SQL
  ↓
RBAC 权限服务
  ↓
企业微信 Adapter + DTO
  ↓
同步状态机
  ↓
认证与回调接口
  ↓
端到端联调与验收
```

## 当前尚未实现的代码

- 数据库 migration；
- Java Adapter 和 DTO；
- RBAC PermissionService；
- 同步任务和事件消费者；
- 认证 Controller；
- RBAC 管理页面和业务页面。

这些代码必须以本文档和 [需求规格说明书](../requirements/rbac-wecom-requirements.md) 为输入实现。

## 新应用申请审批设计

- [新应用接入申请与审批交付](./12-application-approval-workflow.md)：已确认需求、状态、页面按钮、数据/接口、安全交付与验收。
- [审批组件调研](../research/approval-workflow-components.md)：Flowable、Warm-Flow、Camunda、LiteFlow及设计器的对比。
- [实施与验收计划](../plans/seeex-application-approval-implementation.md)：后续步骤和权限资源。

以上属于新增设计，尚未实现；早期“当前尚未实现的代码”列表是历史规划记录，现有平台完成度以 [静态审计及后续更新](./11-auth-platform-completion-audit.md) 为准。
