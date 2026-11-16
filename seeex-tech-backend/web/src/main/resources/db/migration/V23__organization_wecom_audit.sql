alter table job_grade add column source varchar(32) not null default 'LOCAL', add column updated_at datetime(3) not null default current_timestamp(3) on update current_timestamp(3);
alter table role add column updated_at datetime(3) not null default current_timestamp(3) on update current_timestamp(3);
alter table audit_log add column application_code varchar(128) null, add column resource_key varchar(512) null;
create index ix_audit_app_time on audit_log(application_code,created_at);
create table wecom_grade_mapping (
 id bigint not null auto_increment primary key, external_position varchar(128) character set utf8mb4 collate utf8mb4_bin not null, job_grade_id bigint not null,
 status varchar(16) not null default 'ACTIVE', unique key uk_wecom_position(external_position)
) engine=InnoDB default charset=utf8mb4;
insert into resource(code,permission_code,name,resource_type,status,application_code,entry_type,visible)
select 'audit.export','audit:export','导出审计','ACTION','ACTIVE','platform','ADMIN',0 where not exists(select 1 from resource where permission_code='audit:export');
insert into role_resource(role_id,resource_id,effect,assignment_source)
select r.id,p.id,'ALLOW','MANUAL' from role r join resource p on p.permission_code='audit:export'
where r.code='platform_admin' and not exists(select 1 from role_resource rr where rr.role_id=r.id and rr.resource_id=p.id);
