alter table employee add column sync_version bigint not null default 0, add column last_event_at datetime(3) null;
alter table department add column sync_version bigint not null default 0, add column last_synced_at datetime(3) null, add column last_event_at datetime(3) null;
alter table sync_run add column owner_token varchar(64) null, add column lease_until datetime(3) null, add column attempt_count int not null default 0;
create table sync_run_item (
 id bigint not null auto_increment primary key, run_no varchar(64) not null, attempt_no int not null,
 object_type varchar(32) not null, external_key varchar(128) not null, status varchar(16) not null, error_message varchar(512) null,
 unique key uk_sync_attempt_item(run_no,attempt_no,object_type,external_key)
) engine=InnoDB default charset=utf8mb4;
alter table application_policy add column row_version bigint not null default 0;
create table configuration_revision (
 config_key varchar(160) not null primary key, revision bigint not null default 0
) engine=InnoDB default charset=utf8mb4;
insert into configuration_revision(config_key,revision) select concat('app-admin:',app_code),0 from application_registry;
insert into configuration_revision(config_key,revision) select concat('role-scope:',id),0 from role;
-- 老版本RUNNING没有执行令牌，迁移后安全重新排队，不恢复旧进程的写入资格。
update sync_run set status='PENDING',owner_token=null,lease_until=null where status='RUNNING';
