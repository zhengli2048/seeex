alter table sync_run add column user_count int not null default 0 comment 'synced user count';
alter table sync_run add column error_message varchar(512) comment 'error message';
alter table sync_run add column finished_at datetime(3) comment 'finish time';
