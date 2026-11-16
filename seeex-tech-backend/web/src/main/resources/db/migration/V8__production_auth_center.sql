alter table application_registry add column owner_employee_id bigint comment 'application owner employee id';
alter table application_registry add column rollout_strategy varchar(32) not null default 'ALL' comment 'ALL/CANARY';

create table if not exists application_ci_credential (
  id bigint not null auto_increment comment 'CI credential primary key',
  app_code varchar(128) not null comment 'application code',
  credential_name varchar(128) not null comment 'credential name',
  token_hash varchar(128) not null comment 'hashed token',
  status varchar(32) not null default 'ACTIVE' comment 'ACTIVE/REVOKED',
  expires_at datetime(3) comment 'credential expiry',
  last_used_at datetime(3) comment 'last use time',
  created_at datetime(3) not null default current_timestamp(3) comment 'created time',
  revoked_at datetime(3) comment 'revoked time',
  primary key (id), unique key uk_ci_credential_hash (token_hash), key ix_ci_credential_app (app_code, status)
) engine=InnoDB default charset=utf8mb4 comment='application CI credentials';

create table if not exists application_route_approval (
  id bigint not null auto_increment comment 'approval primary key',
  app_code varchar(128) not null comment 'application code',
  release_id bigint not null comment 'route release id',
  status varchar(32) not null default 'PENDING' comment 'PENDING/APPROVED/REJECTED',
  reviewer_id bigint comment 'reviewer employee id',
  review_message varchar(512) comment 'review message',
  reviewed_at datetime(3) comment 'review time',
  primary key (id), unique key uk_route_approval_release (release_id)
) engine=InnoDB default charset=utf8mb4 comment='route metadata approval';

create table if not exists application_release_rollout (
  id bigint not null auto_increment comment 'rollout primary key',
  app_code varchar(128) not null comment 'application code',
  release_id bigint not null comment 'release id',
  rollout_percent int not null default 100 comment 'rollout percentage',
  audience_json json comment 'canary audience rules',
  status varchar(32) not null default 'ACTIVE' comment 'ACTIVE/ROLLED_BACK',
  created_at datetime(3) not null default current_timestamp(3) comment 'created time',
  ended_at datetime(3) comment 'ended time',
  primary key (id), key ix_rollout_app_status (app_code, status)
) engine=InnoDB default charset=utf8mb4 comment='application release rollout';

create table if not exists auth_token_session (
  id bigint not null auto_increment comment 'token session primary key',
  subject_id varchar(128) not null comment 'subject id',
  client_id varchar(128) not null comment 'client id',
  access_token_hash varchar(128) not null comment 'access token hash',
  refresh_token_hash varchar(128) comment 'refresh token hash',
  access_expires_at datetime(3) not null comment 'access expiry',
  refresh_expires_at datetime(3) comment 'refresh expiry',
  status varchar(32) not null default 'ACTIVE' comment 'ACTIVE/REVOKED',
  rotated_from_id bigint comment 'previous refresh session',
  created_at datetime(3) not null default current_timestamp(3) comment 'created time',
  revoked_at datetime(3) comment 'revoked time',
  primary key (id), unique key uk_access_token_hash (access_token_hash), unique key uk_refresh_token_hash (refresh_token_hash)
) engine=InnoDB default charset=utf8mb4 comment='authentication token sessions';

create table if not exists wecom_sync_checkpoint (
  id bigint not null auto_increment comment 'checkpoint primary key',
  provider varchar(32) not null comment 'provider',
  cursor_value varchar(512) comment 'provider cursor',
  last_success_at datetime(3) comment 'last successful sync',
  status varchar(32) not null default 'READY' comment 'READY/RUNNING/FAILED',
  error_message varchar(512) comment 'last error',
  primary key (id), unique key uk_sync_checkpoint_provider (provider)
) engine=InnoDB default charset=utf8mb4 comment='WeCom full sync checkpoint';
