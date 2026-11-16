# 当前数据模型基线

本文档是当前后端数据模型的入口，优先级高于目录中的历史 PostgreSQL 草稿。

## 权威文件

- MySQL 建表脚本：`seeex-complete-schema-mysql.sql`
- 模型变更约定：`10-model-decisions.md`
- ER 图：`seeex-security-file-er.drawio`

## 当前数据库约定

- 数据库：MySQL 8.0+
- 字符集：`utf8mb4`
- 存储引擎：`InnoDB`
- 时间类型：`datetime(3)`
- JSON 类型：`json`
- 不使用数据库外键
- 关联完整性由应用层维护
- 所有字段直接在建表语句中使用 MySQL `comment` 注解

## 审计字段

所有表的审计人字段使用显示名称字符串：

```sql
created_by varchar(128) comment '创建人名称'
updated_by varchar(128) comment '更新人名称'
deleted_by varchar(128) comment '删除人名称'
```

## 已删除的表

以下表不再属于当前模型：

- `employee_phone_history`
- `employee_external_identity`
- `external_identity_conflict`

系统不建立独立的外部人员表。企业微信只保留认证和同步所需的配置、批次、事件及职级映射数据。

## 当前表清单

```text
company_profile
employee
employee_account
department
employee_department
job_grade
auth_session
auth_oauth_state
auth_exchange_code
role
resource
role_resource
subject_role_assignment
data_scope
wecom_grade_mapping
sync_run
sync_event
file_metadata
audit_log
```

## 历史文档说明

以下早期文件包含已废弃的 PostgreSQL 语法或已删除表，只用于追溯设计过程：

- `01-database-schema.md`
- `01-database-schema-sql.md`
- `seeex-rbac-schema.sql`

实现和数据库初始化只能依据 MySQL 权威脚本，不得直接执行历史脚本。
