-- 认证平台导航与应用双端授权。旧应用路由保持 WEB，基础平台属于 ADMIN。
alter table resource add column entry_type varchar(16) not null default 'WEB' comment '资源所属端 WEB/ADMIN',
 add column menu_category varchar(64) not null default '常用功能' comment '应用菜单分类';
update resource set entry_type='ADMIN' where application_code='platform';
alter table auth_sso_exchange_code add column entry_type varchar(16) not null default 'WEB' comment '交换码目标端';
alter table application_registry add column client_secret_hash varchar(128) null comment '应用服务凭证摘要，独立于CI';

insert into resource(code,permission_code,name,resource_type,status,application_code,entry_type,sort_order,visible)
select p.code,p.permission_code,p.name,'MODULE','ACTIVE','platform','ADMIN',p.sort_order,1 from (
 select 'nav.organization' code,'platform:navigation:organization' permission_code,'组织管理' name,10 sort_order union all
 select 'nav.authorization','platform:navigation:authorization','权限中心',20 union all
 select 'nav.integration','platform:navigation:integration','集成与运维',30 union all
 select 'nav.applications','platform:navigation:applications','应用管理',40
) p where not exists(select 1 from resource r where r.code=p.code);

update resource set resource_type='MENU',route_path='/',visible=1,sort_order=0 where permission_code='platform:dashboard:view';
update resource set resource_type='MENU',route_path=case permission_code
 when 'employees:view' then '/employees' when 'departments:view' then '/departments'
 when 'job-grades:view' then '/job-grades' when 'rbac:roles:view' then '/rbac/roles'
 when 'rbac:resources:view' then '/rbac/resources' when 'wecom:config:view' then '/wecom'
 when 'files:view' then '/files' when 'audit:view' then '/audit-logs'
 when 'platform:apps:view' then '/applications' end,visible=1
where application_code='platform' and permission_code in ('employees:view','departments:view','job-grades:view','rbac:roles:view','rbac:resources:view','wecom:config:view','files:view','audit:view','platform:apps:view');
update resource child join resource parent on parent.code='nav.organization' set child.parent_id=parent.id
 where child.application_code='platform' and child.permission_code in ('employees:view','departments:view','job-grades:view') and child.parent_id is null;
update resource child join resource parent on parent.code='nav.authorization' set child.parent_id=parent.id
 where child.application_code='platform' and child.permission_code in ('rbac:roles:view','rbac:resources:view') and child.parent_id is null;
update resource child join resource parent on parent.code='nav.integration' set child.parent_id=parent.id
 where child.application_code='platform' and child.permission_code in ('wecom:config:view','files:view','audit:view') and child.parent_id is null;
update resource child join resource parent on parent.code='nav.applications' set child.parent_id=parent.id
 where child.application_code='platform' and child.permission_code='platform:apps:view' and child.parent_id is null;
-- 权限分析复用员工查看权限，由导航聚合显式关联，避免复制 permission_code。
insert into resource(code,permission_code,name,resource_type,status,application_code,entry_type,visible)
select 'platform.apps.auth','platform:apps:auth','轮换应用服务凭证','ACTION','ACTIVE','platform','ADMIN',0
where not exists(select 1 from resource r where r.permission_code='platform:apps:auth');
insert into role_resource(role_id,resource_id,effect,assignment_source)
select ro.id,re.id,'ALLOW','MANUAL' from role ro join resource re on re.permission_code='platform:apps:auth'
where ro.code='platform_admin' and not exists(select 1 from role_resource rr where rr.role_id=ro.id and rr.resource_id=re.id);
