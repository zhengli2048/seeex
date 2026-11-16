-- Seeex MySQL 8.0+ schema
-- Policy: no FOREIGN KEY. Relationships are validated by the application layer.
set names utf8mb4;

create database if not exists seeex default character set utf8mb4 collate utf8mb4_0900_ai_ci;
use seeex;

create table if not exists company_profile (
  id bigint not null auto_increment comment '公司配置主键',
  company_code varchar(64) not null comment '公司业务编码',
  company_name varchar(128) not null comment '公司名称',
  timezone varchar(64) not null default 'Asia/Shanghai' comment '公司时区',
  logo_url varchar(512) comment '公司 Logo 地址',
  wecom_enabled tinyint(1) not null default 0 comment '是否启用企业微信',
  wecom_corp_id varchar(128) comment '企业微信 Corp ID',
  wecom_agent_id varchar(64) comment '企业微信 Agent ID',
  wecom_secret_ref varchar(256) comment '企业微信 Secret 引用',
  wecom_token_ref varchar(256) comment '企业微信 Token 引用',
  wecom_aes_key_ref varchar(256) comment '企业微信 AES Key 引用',
  created_at datetime(3) not null default current_timestamp(3) comment '创建时间',
  updated_at datetime(3) not null default current_timestamp(3) on update current_timestamp(3) comment '更新时间',
  deleted_at datetime(3) comment '逻辑删除时间',
  primary key (id), unique key uk_company_code (company_code)
) engine=InnoDB default charset=utf8mb4 comment='公司基础配置';

create table if not exists employee (
  id bigint not null auto_increment comment '员工主键',
  employee_no varchar(32) not null comment '员工业务编号',
  display_name varchar(128) not null comment '员工显示名称',
  internal_title varchar(128) comment '内部职位名称',
  internal_mobile varchar(32) comment '内部手机号',
  internal_email varchar(256) comment '内部邮箱',
  avatar_url varchar(512) comment '头像地址',
  status varchar(32) not null default 'ACTIVE' comment 'ACTIVE/DISABLED/LEFT/LOCKED',
  source varchar(32) not null default 'LOCAL' comment 'LOCAL/WE_COM/IMPORT',
  primary_department_id bigint comment '主部门 ID，逻辑关联 department.id',
  job_grade_id bigint comment '职级 ID，逻辑关联 job_grade.id',
  created_at datetime(3) not null default current_timestamp(3) comment '创建时间',
  created_by varchar(128) comment '创建人名称',
  updated_at datetime(3) not null default current_timestamp(3) on update current_timestamp(3) comment '更新时间',
  updated_by varchar(128) comment '更新人名称',
  deleted_at datetime(3) comment '逻辑删除时间',
  deleted_by varchar(128) comment '删除人名称',
  primary key (id), unique key uk_employee_no (employee_no), key ix_employee_status (status), key ix_employee_department (primary_department_id)
) engine=InnoDB default charset=utf8mb4 comment='员工主数据';

create table if not exists employee_account (
  id bigint not null auto_increment comment '账号主键',
  employee_id bigint not null comment '员工 ID，逻辑关联 employee.id',
  login_phone varchar(32) not null comment '登录手机号',
  phone_verified tinyint(1) not null default 0 comment '手机号是否验证',
  password_hash varchar(255) comment '密码哈希',
  login_enabled tinyint(1) not null default 1 comment '是否允许登录',
  last_login_at datetime(3) comment '最后登录时间',
  failed_login_count int not null default 0 comment '连续登录失败次数',
  locked_until datetime(3) comment '锁定截止时间',
  is_primary tinyint(1) not null default 1 comment '是否主账号',
  created_at datetime(3) not null default current_timestamp(3) comment '创建时间',
  updated_at datetime(3) not null default current_timestamp(3) on update current_timestamp(3) comment '更新时间',
  deleted_at datetime(3) comment '逻辑删除时间',
  primary key (id), unique key uk_account_phone (login_phone), key ix_account_employee (employee_id)
) engine=InnoDB default charset=utf8mb4 comment='员工登录账号';

create table if not exists department (
  id bigint not null auto_increment comment '部门主键',
  external_id varchar(128) comment '外部系统部门 ID',
  external_parent_id varchar(128) comment '外部系统父部门 ID',
  parent_id bigint comment '父部门 ID，逻辑关联 department.id',
  department_code varchar(64) not null comment '部门编码',
  name varchar(128) not null comment '部门名称',
  sort_order int not null default 0 comment '排序值',
  status varchar(32) not null default 'ACTIVE' comment '部门状态',
  source varchar(32) not null default 'LOCAL' comment '数据来源',
  created_at datetime(3) not null default current_timestamp(3) comment '创建时间',
  updated_at datetime(3) not null default current_timestamp(3) on update current_timestamp(3) comment '更新时间',
  deleted_at datetime(3) comment '逻辑删除时间',
  primary key (id), unique key uk_department_code (department_code), key ix_department_parent (parent_id), unique key uk_department_external (external_id)
) engine=InnoDB default charset=utf8mb4 comment='组织部门';

create table if not exists employee_department (
  id bigint not null auto_increment comment '员工部门关系主键',
  employee_id bigint not null comment '员工 ID',
  department_id bigint not null comment '部门 ID',
  is_primary tinyint(1) not null default 0 comment '是否主部门',
  is_leader tinyint(1) not null default 0 comment '是否部门负责人',
  source varchar(32) not null default 'LOCAL' comment '关系来源',
  effective_at datetime(3) not null default current_timestamp(3) comment '生效时间',
  expires_at datetime(3) comment '失效时间',
  revoked_at datetime(3) comment '撤销时间',
  revoked_by bigint comment '撤销人 ID',
  created_at datetime(3) not null default current_timestamp(3) comment '创建时间',
  primary key (id), unique key uk_employee_department (employee_id, department_id), key ix_employee_department_active (employee_id, is_primary, revoked_at)
) engine=InnoDB default charset=utf8mb4 comment='员工部门关系';

create table if not exists job_grade (
  id bigint not null auto_increment comment '职级主键',
  name varchar(128) not null comment '职级名称',
  level_value int comment '职级数值',
  sort_order int not null default 0 comment '排序值',
  external_provider varchar(32) comment '外部职级提供方',
  external_id varchar(128) comment '外部职级 ID',
  external_name varchar(128) comment '外部职级名称',
  status varchar(32) not null default 'ACTIVE' comment '职级状态',
  created_at datetime(3) not null default current_timestamp(3) comment '创建时间',
  updated_at datetime(3) not null default current_timestamp(3) on update current_timestamp(3) comment '更新时间',
  deleted_at datetime(3) comment '逻辑删除时间',
  primary key (id), unique key uk_job_grade_external (external_provider, external_id)
) engine=InnoDB default charset=utf8mb4 comment='系统职级';

create table if not exists auth_session (
  id bigint not null auto_increment comment '会话主键',
  employee_account_id bigint not null comment '账号 ID',
  access_token_hash varchar(128) not null comment 'Access Token 哈希',
  refresh_token_hash varchar(128) not null comment 'Refresh Token 哈希',
  issued_at datetime(3) not null default current_timestamp(3) comment '签发时间',
  access_expires_at datetime(3) not null comment 'Access Token 过期时间',
  refresh_expires_at datetime(3) not null comment 'Refresh Token 过期时间',
  revoked_at datetime(3) comment '撤销时间',
  ip_address varchar(45) comment '客户端 IP',
  user_agent varchar(512) comment '客户端 User-Agent',
  last_used_at datetime(3) comment '最后使用时间',
  primary key (id), unique key uk_access_hash (access_token_hash), unique key uk_refresh_hash (refresh_token_hash), key ix_session_expiry (access_expires_at, refresh_expires_at)
) engine=InnoDB default charset=utf8mb4 comment='登录会话';

create table if not exists auth_oauth_state (
  id bigint not null auto_increment comment 'OAuth 状态主键',
  provider varchar(32) not null comment 'OAuth 提供方，如 WE_COM',
  state_hash varchar(128) not null comment 'state 哈希',
  redirect_uri varchar(512) not null comment '回调地址',
  return_path varchar(512) comment '登录完成后的返回路径',
  expires_at datetime(3) not null comment '过期时间',
  used_at datetime(3) comment '消费时间',
  created_at datetime(3) not null default current_timestamp(3) comment '创建时间',
  primary key (id), unique key uk_oauth_state_hash (state_hash), key ix_oauth_state_lookup (provider, expires_at, used_at)
) engine=InnoDB default charset=utf8mb4 comment='OAuth 一次性状态';

create table if not exists auth_exchange_code (
  id bigint not null auto_increment comment '交换码主键',
  employee_account_id bigint not null comment '账号 ID',
  code_hash varchar(128) not null comment '交换码哈希',
  expires_at datetime(3) not null comment '过期时间',
  used_at datetime(3) comment '消费时间',
  created_at datetime(3) not null default current_timestamp(3) comment '创建时间',
  primary key (id), unique key uk_exchange_code_hash (code_hash), key ix_exchange_code_lookup (expires_at, used_at)
) engine=InnoDB default charset=utf8mb4 comment='OAuth 短期交换码';

create table if not exists role (
  id bigint not null auto_increment comment '角色主键',
  code varchar(128) not null comment '角色编码',
  name varchar(128) not null comment '角色名称',
  description text comment '角色描述',
  status varchar(32) not null default 'ACTIVE' comment '角色状态',
  created_at datetime(3) not null default current_timestamp(3) comment '创建时间',
  updated_at datetime(3) not null default current_timestamp(3) on update current_timestamp(3) comment '更新时间',
  deleted_at datetime(3) comment '逻辑删除时间',
  primary key (id), unique key uk_role_code (code)
) engine=InnoDB default charset=utf8mb4 comment='RBAC 角色';

create table if not exists resource (
  id bigint not null auto_increment comment '资源主键',
  parent_id bigint comment '父资源 ID',
  resource_type varchar(32) not null comment 'MENU/PAGE/TAB/BUTTON/API',
  code varchar(128) not null comment '资源编码',
  permission_code varchar(160) not null comment '权限编码',
  name varchar(128) not null comment '资源名称',
  path varchar(512) comment '路由或 API 路径',
  component varchar(256) comment '前端组件',
  icon varchar(128) comment '图标',
  sort_order int not null default 0 comment '排序值',
  status varchar(32) not null default 'ACTIVE' comment '资源状态',
  is_visible tinyint(1) not null default 1 comment '是否显示',
  is_public tinyint(1) not null default 0 comment '是否公开 API',
  created_at datetime(3) not null default current_timestamp(3) comment '创建时间',
  updated_at datetime(3) not null default current_timestamp(3) on update current_timestamp(3) comment '更新时间',
  deleted_at datetime(3) comment '逻辑删除时间',
  primary key (id), unique key uk_resource_code (code), unique key uk_permission_code (permission_code), key ix_resource_parent (parent_id, sort_order)
) engine=InnoDB default charset=utf8mb4 comment='RBAC 资源';

create table if not exists role_resource (
  id bigint not null auto_increment comment '角色资源关系主键',
  role_id bigint not null comment '角色 ID',
  resource_id bigint not null comment '资源 ID',
  effect varchar(16) not null comment 'ALLOW/DENY，DENY 优先',
  effective_at datetime(3) not null default current_timestamp(3) comment '生效时间',
  expires_at datetime(3) comment '失效时间',
  created_at datetime(3) not null default current_timestamp(3) comment '创建时间',
  primary key (id), unique key uk_role_resource_effect (role_id, resource_id, effect), key ix_role_resource_active (role_id, resource_id, effective_at, expires_at)
) engine=InnoDB default charset=utf8mb4 comment='角色资源授权';

create table if not exists subject_role_assignment (
  id bigint not null auto_increment comment '主体角色授权主键',
  subject_type varchar(32) not null comment 'EMPLOYEE/DEPARTMENT/JOB_GRADE',
  subject_id bigint not null comment '主体 ID，根据 subject_type 解释',
  role_id bigint not null comment '角色 ID',
  assignment_source varchar(32) not null comment 'MANUAL/JOB_GRADE/DEPARTMENT/WE_COM/SYSTEM',
  source_key varchar(128) comment '来源幂等键',
  sync_run_id bigint comment '同步批次 ID',
  department_scope varchar(32) comment 'CURRENT/DESCENDANTS/CURRENT_AND_DESCENDANTS',
  effective_at datetime(3) not null default current_timestamp(3) comment '生效时间',
  expires_at datetime(3) comment '失效时间',
  revoked_at datetime(3) comment '撤销时间',
  revoked_by bigint comment '撤销人 ID',
  created_at datetime(3) not null default current_timestamp(3) comment '创建时间',
  primary key (id), key ix_subject_role_active (subject_type, subject_id, revoked_at, expires_at), key ix_subject_role_role (role_id, revoked_at)
) engine=InnoDB default charset=utf8mb4 comment='主体角色授权';

create table if not exists data_scope (
  id bigint not null auto_increment comment '数据范围主键',
  role_id bigint not null comment '角色 ID',
  resource_id bigint comment '资源 ID，为空表示角色级范围',
  scope_type varchar(32) not null comment 'SELF/DEPARTMENT/DEPARTMENT_AND_DESCENDANTS/ALL/CUSTOM',
  scope_config json comment 'CUSTOM 范围配置',
  effective_at datetime(3) not null default current_timestamp(3) comment '生效时间',
  expires_at datetime(3) comment '失效时间',
  created_at datetime(3) not null default current_timestamp(3) comment '创建时间',
  primary key (id), key ix_data_scope_lookup (role_id, resource_id, effective_at, expires_at)
) engine=InnoDB default charset=utf8mb4 comment='角色数据范围';

create table if not exists wecom_grade_mapping (
  id bigint not null auto_increment comment '职级映射主键',
  external_field varchar(128) not null comment '外部字段名',
  external_value varchar(256) not null comment '外部字段值',
  job_grade_id bigint not null comment '系统职级 ID',
  source varchar(32) not null default 'MANUAL' comment '映射来源',
  created_at datetime(3) not null default current_timestamp(3) comment '创建时间',
  updated_at datetime(3) not null default current_timestamp(3) on update current_timestamp(3) comment '更新时间',
  primary key (id), unique key uk_wecom_grade_mapping (external_field, external_value)
) engine=InnoDB default charset=utf8mb4 comment='企业微信职级映射';

create table if not exists c (
  id bigint not null auto_increment comment '身份冲突主键',
  provider varchar(32) not null comment '外部身份提供方',
  external_id varchar(256) not null comment '外部身份 ID',
  candidate_employee_ids json not null comment '候选员工 ID JSON 数组',
  reason varchar(128) not null comment '冲突原因',
  status varchar(32) not null default 'OPEN' comment 'OPEN/RESOLVED/IGNORED',
  resolved_employee_id bigint comment '最终确认员工 ID',
  resolved_by bigint comment '处理人 ID',
  resolved_at datetime(3) comment '处理时间',
  created_at datetime(3) not null default current_timestamp(3) comment '创建时间',
  primary key (id), key ix_identity_conflict_status (status, created_at)
) engine=InnoDB default charset=utf8mb4 comment='外部身份匹配冲突';


create table if not exists sync_run (
  id bigint not null auto_increment comment '同步批次主键',
  run_no varchar(32) not null comment '同步批次编号',
  sync_type varchar(32) not null comment 'FULL/INCREMENTAL/REPAIR',
  status varchar(32) not null comment 'PENDING/RUNNING/SUCCESS/PARTIAL/FAILED',
  started_at datetime(3) comment '开始时间',
  finished_at datetime(3) comment '完成时间',
  total_count int not null default 0 comment '总记录数',
  success_count int not null default 0 comment '成功记录数',
  failed_count int not null default 0 comment '失败记录数',
  error_message text comment '失败信息',
  created_at datetime(3) not null default current_timestamp(3) comment '创建时间',
  primary key (id), unique key uk_sync_run_no (run_no), key ix_sync_run_status (status, created_at)
) engine=InnoDB default charset=utf8mb4 comment='企业微信同步批次';

create table if not exists sync_event (
  id bigint not null auto_increment comment '同步事件主键',
  event_no varchar(32) not null comment '事件编号',
  provider varchar(32) not null comment '事件提供方',
  event_type varchar(128) not null comment '事件类型',
  event_id varchar(256) comment '外部事件 ID',
  payload_hash varchar(128) not null comment '事件内容哈希',
  payload_encrypted longtext comment '加密后的原始事件内容',
  status varchar(32) not null comment 'RECEIVED/PROCESSING/SUCCESS/RETRY_WAIT/DEAD_LETTER',
  retry_count int not null default 0 comment '重试次数',
  next_retry_at datetime(3) comment '下一次重试时间',
  received_at datetime(3) not null default current_timestamp(3) comment '接收时间',
  processed_at datetime(3) comment '处理时间',
  error_message text comment '处理错误信息',
  sync_run_id bigint comment '同步批次 ID',
  primary key (id), unique key uk_sync_event_no (event_no), unique key uk_sync_event_external (provider, event_type, event_id), key ix_sync_event_retry (status, next_retry_at)
) engine=InnoDB default charset=utf8mb4 comment='企业微信同步事件';

create table if not exists file_metadata (
  id bigint not null auto_increment comment '文件主键',
  idempotency_key varchar(128) not null comment '上传申请幂等键',
  tenant_id varchar(64) not null comment '租户 ID',
  owner_id varchar(64) not null comment '文件所有者 ID',
  biz_type varchar(64) comment '关联业务类型',
  biz_id varchar(128) comment '关联业务 ID',
  bucket_name varchar(128) not null comment 'OSS Bucket，由文件服务选择',
  object_key varchar(512) not null comment 'OSS 对象 Key',
  file_name varchar(255) not null comment '原始文件名，用于下载命名',
  content_type varchar(128) not null comment 'MIME 类型',
  file_size bigint not null comment '文件大小，单位字节',
  etag varchar(128) comment 'OSS ETag',
  sha256 char(64) comment '文件 SHA-256 摘要',
  visibility varchar(32) not null default 'PRIVATE' comment 'PRIVATE/PUBLIC/TEMP/QUARANTINE',
  status varchar(32) not null default 'PENDING' comment 'PENDING/CONFIRMED/DELETED',
  storage_class varchar(32) not null default 'STANDARD' comment 'OSS 存储类型',
  created_at datetime(3) not null default current_timestamp(3) comment '创建时间',
  confirmed_at datetime(3) comment '确认上传时间',
  deleted_at datetime(3) comment '删除时间',
  updated_at datetime(3) not null default current_timestamp(3) on update current_timestamp(3) comment '更新时间',
  primary key (id), unique key uk_file_idempotency (idempotency_key), unique key uk_file_object_key (object_key), key ix_file_owner_status (tenant_id, owner_id, status), key ix_file_business (tenant_id, biz_type, biz_id), key ix_file_cleanup (status, created_at)
) engine=InnoDB default charset=utf8mb4 comment='文件服务元数据';

create table if not exists audit_log (
  id bigint not null auto_increment comment '审计日志主键',
  log_no varchar(32) not null comment '日志编号',
  operator_employee_id varchar(128) comment '操作人名称',
  action varchar(128) not null comment '操作动作',
  resource_type varchar(32) comment '资源类型',
  resource_id bigint comment '资源 ID',
  before_data json comment '变更前数据，敏感字段需脱敏',
  after_data json comment '变更后数据，敏感字段需脱敏',
  request_id varchar(128) comment '请求 ID，可关联 traceId',
  ip_address varchar(45) comment '客户端 IP',
  user_agent varchar(512) comment '客户端 User-Agent',
  created_at datetime(3) not null default current_timestamp(3) comment '创建时间',
  primary key (id), unique key uk_audit_log_no (log_no), key ix_audit_operator_time (operator_employee_id, created_at), key ix_audit_resource (resource_type, resource_id, created_at)
) engine=InnoDB default charset=utf8mb4 comment='系统审计日志';
