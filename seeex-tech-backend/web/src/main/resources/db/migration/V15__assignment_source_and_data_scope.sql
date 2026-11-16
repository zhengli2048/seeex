-- 授权来源追踪
alter table role_resource add column assignment_source varchar(32) null comment 'MANUAL/JOB_GRADE/DEPARTMENT/WE_COM';
alter table role_resource add column source_key varchar(128) null comment 'source business key';
alter table role_resource add column sync_run_id varchar(64) null comment 'sync run id';
alter table subject_role_assignment add column assignment_source varchar(32) null comment 'MANUAL/JOB_GRADE/DEPARTMENT/WE_COM';
alter table subject_role_assignment add column source_key varchar(128) null comment 'source business key';
alter table subject_role_assignment add column sync_run_id varchar(64) null comment 'sync run id';

-- 角色数据范围
create table if not exists role_data_scope (
  id bigint not null auto_increment comment 'primary key',
  role_id bigint not null comment 'role id',
  resource_code varchar(160) not null comment 'resource code',
  scope_type varchar(32) not null comment 'SELF/DEPARTMENT/DEPARTMENT_AND_DESCENDANTS/ALL/CUSTOM',
  scope_config json null comment 'CUSTOM scope config',
  created_at datetime(3) not null default current_timestamp(3) comment 'created time',
  updated_at datetime(3) not null default current_timestamp(3) on update current_timestamp(3) comment 'updated time',
  deleted_at datetime(3) null comment 'deleted time',
  primary key (id),
  key ix_role_data_scope_role (role_id, resource_code)
) engine=InnoDB default charset=utf8mb4 comment='role data scope';
