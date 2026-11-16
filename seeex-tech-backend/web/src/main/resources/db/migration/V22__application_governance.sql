create table application_admin (
 id bigint not null auto_increment primary key, application_code varchar(128) not null, employee_id bigint not null,
 unique key uk_application_admin(application_code,employee_id)
) engine=InnoDB default charset=utf8mb4;
create table application_policy (
 id bigint not null auto_increment primary key, application_code varchar(128) not null, name varchar(128) not null,
 subject_type varchar(16) not null, subject_id bigint not null, resource_id bigint not null,
 effect varchar(16) not null, scope_type varchar(32) not null, scope_config json not null, status varchar(16) not null,
 key ix_policy_subject(subject_type,subject_id), key ix_policy_app(application_code)
) engine=InnoDB default charset=utf8mb4;
-- 现有平台管理员保留全组织管理能力，其他角色默认仅本人。
insert into role_data_scope(role_id,resource_code,scope_type,scope_config)
select r.id,p.code,'ALL','{}' from role r join (select 'employees:view' code union all select 'departments:view') p
where r.code='platform_admin' and not exists(select 1 from role_data_scope d where d.role_id=r.id and d.resource_code=p.code and d.deleted_at is null);
