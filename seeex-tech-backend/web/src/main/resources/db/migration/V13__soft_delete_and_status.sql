-- 软删除字段（与现有 status=DELETED 共存，后续阶段再切换写入逻辑）
alter table employee add column deleted_at datetime(3) null comment 'deleted time';
alter table department add column deleted_at datetime(3) null comment 'deleted time';
alter table job_grade add column deleted_at datetime(3) null comment 'deleted time';
alter table role add column deleted_at datetime(3) null comment 'deleted time';
alter table resource add column deleted_at datetime(3) null comment 'deleted time';
alter table role_resource add column deleted_at datetime(3) null comment 'deleted time';
alter table subject_role_assignment add column deleted_at datetime(3) null comment 'deleted time';

-- 员工状态扩展（新增 DISABLED/LEFT/LOCKED 语义，varchar 无需改列，仅更新注释）
alter table employee modify column status varchar(32) not null default 'ACTIVE' comment 'ACTIVE/DISABLED/LEFT/LOCKED';

-- 员工账号手机号业务唯一
alter table employee_account add unique key uk_account_login_phone (login_phone);
