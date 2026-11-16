-- 审计扩展
alter table audit_log add column source_ip varchar(64) null comment 'source ip';
alter table audit_log add column request_summary varchar(1024) null comment 'request summary';
alter table audit_log add column before_json json null comment 'before change snapshot';
alter table audit_log add column after_json json null comment 'after change snapshot';
alter table audit_log add column result varchar(32) null comment 'SUCCESS/FAILED';

-- 同步批次状态（PENDING/PARTIAL 语义，varchar 无需改列，仅更新注释）
alter table sync_run modify column status varchar(32) not null default 'PENDING' comment 'PENDING/RUNNING/SUCCESS/PARTIAL/FAILED';

-- 事件状态机字段
alter table sync_event add column retry_count int not null default 0 comment 'retry count';
alter table sync_event add column next_retry_at datetime(3) null comment 'next retry time';
alter table sync_event add column processed_at datetime(3) null comment 'processed time';
alter table sync_event add column error_message varchar(1024) null comment 'last error';
alter table sync_event add column payload mediumtext null comment 'original event payload';
alter table sync_event modify column status varchar(32) not null default 'RECEIVED' comment 'RECEIVED/PROCESSING/SUCCESS/RETRY_WAIT/DEAD_LETTER';
