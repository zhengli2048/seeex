# 数据模型变更约定

本文件是当前数据模型的补充约定，优先级高于早期 RBAC/企业微信设计稿。

## 审计人字段

所有审计人字段使用字符串保存显示名称：

```sql
created_by varchar(128) comment '创建人名称'
updated_by varchar(128) comment '更新人名称'
deleted_by varchar(128) comment '删除人名称'
```

需要稳定身份追踪时，在审计上下文中另行保存 `operator_id`。

## 删除的表

以下表不再建立，也不再作为当前模型的一部分：

- `employee_phone_history`
- `employee_external_identity`
- `external_identity_conflict`

系统不建立独立的外部人员实体。企业微信只作为认证和组织同步来源，企业微信事件、同步批次和同步状态保存在 `sync_run`、`sync_event` 中。

## 当前权威建表脚本

MySQL 8.0 建表脚本为 `seeex-complete-schema-mysql.sql`。

脚本不使用外键，表之间的关联由应用层维护。早期 PostgreSQL 脚本和包含已删除表的设计稿仅作为历史记录，不用于初始化当前数据库。
