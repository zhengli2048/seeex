# Seeex RBAC 与企业微信数据库设计

## 设计约束

- PostgreSQL + Flyway，沿用现有 `bigserial`/`timestamptz` 约定。
- 单公司、单租户上下文；不引入通用 `tenant`、`person` 和 `version`。
- 员工与登录账号分离；手机号是可变登录标识。
- 企业微信身份独立保存，外部 ID 由业务层检测冲突，不依赖数据库唯一约束。
- 主数据统一使用 `created_at/created_by/updated_at/updated_by/deleted_at/deleted_by`。
- 逻辑删除由 `deleted_at` 表示，业务状态与删除状态分开；审计和事件只追加不删除。

## 实体关系

```text
company_profile
employee ── employee_account
employee ── employee_department ── department
employee ── employee_external_identity
employee ── subject_role_assignment ── role ── role_resource ── resource
department ── role_assignment
job_grade ── job_grade_role ── role
sync_run ── sync_event
employee ── audit_log
```

## 核心表

### company_profile

单行公司配置：`id bigint`、`company_code varchar(64)`、`company_name varchar(128)`、`timezone varchar(64)`、`logo_url varchar(512)`、`wecom_enabled boolean`、`wecom_corp_id varchar(128)`、`wecom_agent_id varchar(64)`、`wecom_secret_ref varchar(256)`、`wecom_token_ref varchar(256)`、`wecom_aes_key_ref varchar(256)`，以及通用审计字段。Secret、Token、AESKey 只保存密钥引用，不保存明文。

### employee

`id bigint`、`employee_no varchar(32)`、`display_name varchar(128)`、`internal_title varchar(128)`、`internal_mobile varchar(32)`、`internal_email varchar(256)`、`avatar_url varchar(512)`、`status varchar(32)`（ACTIVE/DISABLED/LEFT/LOCKED）、`source varchar(32)`（LOCAL/WE_COM/IMPORT）、`primary_department_id bigint`、`job_grade_id bigint`，以及通用审计字段。`employee_no` 创建后不可直接修改。

### employee_account

`id bigint`、`employee_id bigint`、`login_phone varchar(32)`、`phone_verified boolean`、`password_hash varchar(255)`、`login_enabled boolean`、`last_login_at timestamptz`、`failed_login_count integer`、`locked_until timestamptz`、`is_primary boolean`，以及通用审计字段。当前有效账号的手机号必须业务唯一；手机号变化不改变员工和账号主键。

### employee_phone_history

`id bigint`、`employee_account_id bigint`、`phone varchar(32)`、`valid_from timestamptz`、`valid_to timestamptz`、`change_reason varchar(128)`、`created_at timestamptz`。

### department

`id bigint`、`external_id varchar(128)`、`external_parent_id varchar(128)`、`parent_id bigint`、`department_code varchar(64)`、`name varchar(128)`、`sort_order integer`、`status varchar(32)`、`source varchar(32)`，以及通用审计字段。员工可多部门归属。

### employee_department

`id bigint`、`employee_id bigint`、`department_id bigint`、`is_primary boolean`、`is_leader boolean`、`source varchar(32)`、`effective_at timestamptz`、`expires_at timestamptz`、`revoked_at timestamptz`、`revoked_by bigint`，以及通用审计字段。服务层保证每个员工最多一个主部门。

### job_grade

`id bigint`、`name varchar(128)`、`level_value integer`、`sort_order integer`、`external_provider varchar(32)`、`external_id varchar(128)`、`external_name varchar(128)`、`status varchar(32)`，以及通用审计字段。企业微信原始职级和系统职级分开保存。

### employee_external_identity

`id bigint`、`employee_id bigint`、`provider varchar(32)`、`external_id varchar(256)`、`external_union_id varchar(256)`、`external_name varchar(128)`、`external_mobile varchar(32)`、`external_email varchar(256)`、`external_position varchar(128)`、`external_avatar varchar(512)`、`external_status varchar(32)`、`status varchar(32)`、`first_synced_at timestamptz`、`last_synced_at timestamptz`、`last_sync_run_id bigint`。

业务匹配键为 `provider + external_id`；多条匹配进入冲突处理，不按姓名合并。

### wecom_grade_mapping

`id bigint`、`external_field varchar(128)`、`external_value varchar(256)`、`job_grade_id bigint`、`source varchar(32)`、通用审计字段。用于把企业微信 `position` 或 `extattr` 中配置的值映射到系统职级；不把外部文本直接当作稳定职级 ID。

### external_identity_conflict

`id bigint`、`provider varchar(32)`、`external_id varchar(256)`、`candidate_employee_ids jsonb`、`reason varchar(128)`、`status varchar(32)`、`resolved_employee_id bigint`、`resolved_by bigint`、`resolved_at timestamptz`、`created_at timestamptz`。

## RBAC 表

### role

`id bigint`、`code varchar(128)`、`name varchar(128)`、`description text`、`status varchar(32)`，以及通用审计字段。

### resource

`id bigint`、`parent_id bigint`、`resource_type varchar(32)`（MENU/PAGE/TAB/BUTTON/API）、`code varchar(128)`、`permission_code varchar(160)`、`name varchar(128)`、`path varchar(512)`、`component varchar(256)`、`icon varchar(128)`、`sort_order integer`、`status varchar(32)`、`is_visible boolean`，以及通用审计字段。

API 资源增加 `is_public boolean not null default false`；只有登录、健康检查、企业微信回调等明确配置的公共 API 可以设为 `true`。

### menu_item

`id bigint`、`parent_id bigint`、`resource_id bigint`、`name varchar(128)`、`path varchar(512)`、`icon varchar(128)`、`sort_order integer`、`is_visible boolean`、`open_type varchar(32)`（CURRENT_TAB/NEW_TAB/EXTERNAL），以及通用审计字段。菜单是导航配置，不等于权限；一个页面可被多个菜单引用。

### role_resource

`id bigint`、`role_id bigint`、`resource_id bigint`、`effect varchar(16)`（ALLOW/DENY）、`effective_at timestamptz`、`expires_at timestamptz`，以及通用审计字段。

### subject_role_assignment

统一表达员工直授、部门授权和职级默认角色：`id`、`subject_type`（EMPLOYEE/DEPARTMENT/JOB_GRADE）、`subject_id`、`role_id`、`assignment_source`（MANUAL/JOB_GRADE/DEPARTMENT/WE_COM/SYSTEM）、`source_key`、`sync_run_id`、`department_scope`（CURRENT/DESCENDANTS/CURRENT_AND_DESCENDANTS）、`effective_at`、`expires_at`、`revoked_at`、`revoked_by`，以及通用审计字段。角色绑定只表达“主体获得角色”；允许/拒绝统一由 `role_resource.effect` 表达，避免两层 effect 语义冲突。同步只能撤销自己产生的自动授权。

删除独立的 `job_grade_role_assignment` 表，职级默认角色统一写入 `subject_role_assignment(subject_type=JOB_GRADE)`。职级通过默认角色参与授权，不直接绑定按钮或 API。

### data_scope

`id bigint`、`role_id bigint`、`resource_id bigint`、`scope_type varchar(32)`（SELF/DEPARTMENT/DEPARTMENT_AND_DESCENDANTS/ALL/CUSTOM）、`scope_config jsonb`、`effective_at timestamptz`、`expires_at timestamptz`，以及通用审计字段。

`scope_config` 仅用于 `CUSTOM`，保存部门 ID、员工 ID 或业务维度配置；业务服务必须通过统一数据范围服务解释，不能让前端直接拼接过滤条件。

### auth_session

`id bigint`、`employee_account_id bigint`、`access_token_hash varchar(128)`、`refresh_token_hash varchar(128)`、`issued_at timestamptz`、`access_expires_at timestamptz`、`refresh_expires_at timestamptz`、`revoked_at timestamptz`、`ip_address inet`、`user_agent varchar(512)`、`last_used_at timestamptz`。

只保存 Token 哈希，不保存明文 Token；Refresh Token 轮换和撤销必须可审计。

### auth_oauth_state

`id bigint`、`provider varchar(32)`、`state_hash varchar(128)`、`redirect_uri varchar(512)`、`return_path varchar(512)`、`expires_at timestamptz`、`used_at timestamptz`、`created_at timestamptz`。state 只能使用一次，过期或已使用不得继续登录。

### auth_exchange_code

`id bigint`、`employee_account_id bigint`、`code_hash varchar(128)`、`expires_at timestamptz`、`used_at timestamptz`、`created_at timestamptz`。OAuth 回调跳转前端时只传短时一次性 code，不把 Access/Refresh Token 放入 URL。

## 同步、事件和审计表

### sync_run

`id bigint`、`run_no varchar(32)`、`sync_type varchar(32)`（FULL/INCREMENTAL/REPAIR）、`status varchar(32)`（PENDING/RUNNING/SUCCESS/PARTIAL/FAILED）、`started_at timestamptz`、`finished_at timestamptz`、`total_count integer`、`success_count integer`、`failed_count integer`、`error_message text`。

### sync_event

`id bigint`、`event_no varchar(32)`、`provider varchar(32)`、`event_type varchar(128)`、`event_id varchar(256)`、`payload_hash varchar(128)`、`payload_encrypted text`、`status varchar(32)`（RECEIVED/PROCESSING/SUCCESS/RETRY_WAIT/DEAD_LETTER）、`retry_count integer`、`next_retry_at timestamptz`、`received_at timestamptz`、`processed_at timestamptz`、`error_message text`、`sync_run_id bigint`。原文永久加密保存，不提供普通前端查看。

### audit_log

`id bigint`、`log_no varchar(32)`、`operator_employee_id bigint`、`action varchar(128)`、`resource_type varchar(32)`、`resource_id bigint`、`before_data jsonb`、`after_data jsonb`、`request_id varchar(128)`、`ip_address inet`、`user_agent varchar(512)`、`created_at timestamptz`。审计追加写，敏感原文不得放入 before/after JSON。

## 日期业务编号

数据库内部主键使用 identity/bigserial，业务编号独立生成。`business_sequence(sequence_key varchar(64) primary key, sequence_date date, next_value bigint, updated_at timestamptz)` 按业务类型分序列：`employee_no`、`audit_log_no`、`sync_run_no`、`sync_event_no`。事务内加锁递增，格式为 `yyyyMMdd + 8 位序列`，数据库唯一索引兜底，不使用内存计数器。

## 索引与业务约束

建议索引：员工状态、账号手机号、员工/部门关系、外部身份 `(provider, external_id)`、资源权限码、菜单父节点排序、角色资源关系、待重试事件、审计操作人和时间。

数据库唯一约束建议保留在业务编号、角色 code、资源 permission_code、有效账号手机号等稳定内部标识上；外部 ID 的重复由同步服务检测并落冲突表。有效手机号建议使用 PostgreSQL 部分唯一索引：`where deleted_at is null and login_enabled = true`。

主部门建议使用 PostgreSQL 部分唯一索引保证每个员工最多一个有效主部门；`employee.primary_department_id` 是查询冗余字段，必须与 `employee_department.is_primary` 在同一事务内维护，不能作为第二套事实来源。

## 已定稿约束

- 员工编号由系统生成且创建后不可直接修改；如需别名，使用独立展示字段。
- 当前有效账号手机号业务唯一，由数据库和服务层共同校验，历史手机号保留。
- 企业微信职级适配器兼容独立职级接口和企业自定义字段，统一映射到 `job_grade`。
- 员工与部门授权统一使用 `subject_role_assignment` 多态关系表。
- 第一版实现数据范围表，至少支持 `SELF`、`DEPARTMENT`、`DEPARTMENT_AND_DESCENDANTS`、`ALL`、`CUSTOM`，并由业务服务统一调用过滤入口。

## 字段审查结论

本版可以作为详细建表设计的基础，但必须遵守以下一致性规则：

1. `subject_role_assignment` 是员工、部门和职级到角色的唯一绑定表；
2. `role_resource.effect` 是唯一的 ALLOW/DENY 来源，角色绑定表不再设置 `effect`；
3. `data_scope` 是第一版正式表，不只是预留字段；
4. `auth_session` 必须存在，Refresh Token 不得只依赖无状态字符串；
5. 当前有效账号手机号、业务编号、角色 code、资源 permission_code 由数据库约束兜底；
6. 主部门关系由部分唯一索引兜底，`employee.primary_department_id` 只作为冗余查询字段；
7. 外部身份重复仍由业务冲突流程处理，不用数据库唯一约束强行合并。
