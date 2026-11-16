create table if not exists application_registry (
  id bigint not null auto_increment comment 'application primary key',
  app_code varchar(128) not null comment 'application code',
  app_name varchar(128) not null comment 'application name',
  base_url varchar(512) not null comment 'application deployment URL',
  repository_url varchar(512) comment 'git repository URL',
  ci_token_hash varchar(128) not null comment 'hashed CI token',
  status varchar(32) not null default 'ACTIVE' comment 'application status',
  created_at datetime(3) not null default current_timestamp(3) comment 'created time',
  updated_at datetime(3) not null default current_timestamp(3) on update current_timestamp(3) comment 'updated time',
  primary key (id), unique key uk_application_code (app_code)
) engine=InnoDB default charset=utf8mb4 comment='registered applications';

create table if not exists application_route_release (
  id bigint not null auto_increment comment 'route release primary key',
  app_code varchar(128) not null comment 'application code',
  version varchar(128) not null comment 'metadata version',
  git_commit varchar(128) comment 'source commit',
  environment varchar(32) not null comment 'deployment environment',
  base_url varchar(512) not null comment 'deployment URL for this release',
  metadata_json json not null comment 'route metadata JSON',
  status varchar(32) not null default 'REPORTED' comment 'REPORTED or PUBLISHED',
  reported_at datetime(3) not null default current_timestamp(3) comment 'report time',
  primary key (id), unique key uk_route_release (app_code, version)
) engine=InnoDB default charset=utf8mb4 comment='application route metadata releases';

create table if not exists auth_sso_exchange_code (
  id bigint not null auto_increment comment 'exchange code primary key',
  code_hash varchar(128) not null comment 'one-time code hash',
  app_code varchar(128) not null comment 'target application code',
  subject_id varchar(128) not null comment 'authenticated subject',
  redirect_path varchar(512) comment 'target application path',
  expires_at datetime(3) not null comment 'expiration time',
  used_at datetime(3) comment 'consumed time',
  primary key (id), unique key uk_sso_code_hash (code_hash), key ix_sso_code_expiry (app_code, expires_at, used_at)
) engine=InnoDB default charset=utf8mb4 comment='one-time cross application SSO exchange codes';
