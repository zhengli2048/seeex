-- 应用入口及版本控制；增量迁移，不改写历史迁移。
alter table application_registry
  add column description varchar(500) null comment '应用描述',
  add column app_type varchar(32) not null default 'WEB' comment '应用类型',
  add column logo varchar(512) null comment 'Logo 地址',
  add column admin_url varchar(512) null comment '后台管理入口',
  add column api_url varchar(512) null comment '后端服务地址',
  add column sso_callback_path varchar(256) not null default '/sso/callback' comment '站内 SSO 回调路径',
  add column active_version varchar(128) null comment '当前发布版本';
alter table application_route_release
  add column approval_status varchar(32) not null default 'PENDING' comment '审核状态',
  add column rollout_percent int not null default 100 comment '发布用户比例';
alter table employee
  add column source varchar(32) not null default 'LOCAL' comment '员工来源';
insert into resource(code,permission_code,name,resource_type,status,application_code,visible)
select concat('console.',p.code),p.code,p.name,'ACTION','ACTIVE','platform',0
from (
 select 'platform:apps:create' code,'注册应用' name union all
 select 'platform:apps:update','编辑应用' union all
 select 'platform:apps:publish','发布应用版本' union all
 select 'platform:apps:review','审核应用版本' union all
 select 'platform:apps:ci','管理应用凭证' union all
 select 'rbac:roles:assign','绑定角色' union all
 select 'rbac:roles:grant','配置角色资源' union all
 select 'employees:grant','人员直接授权'
) p where not exists(select 1 from resource r where r.permission_code=p.code);
insert into role_resource(role_id,resource_id,effect,assignment_source)
select ro.id,re.id,'ALLOW','MANUAL' from role ro join resource re on re.code like 'console.%'
where ro.code='platform_admin' and not exists(select 1 from role_resource rr where rr.role_id=ro.id and rr.resource_id=re.id);

-- 将历史单部门员工回填到权限计算的关系表。
insert into employee_department(employee_id,department_id,job_grade_id,is_primary)
select e.id,e.primary_department_id,e.job_grade_id,1 from employee e
where e.primary_department_id is not null and e.deleted_at is null
and not exists(select 1 from employee_department ed where ed.employee_id=e.id and ed.deleted_at is null);

-- 已发布历史版本保留当前指向，避免升级后应用菜单全部消失。
update application_route_release set approval_status='APPROVED' where status='PUBLISHED';
update application_registry a set active_version=(select r.version from application_route_release r where r.app_code=a.app_code and r.status='PUBLISHED' order by r.reported_at desc,r.id desc limit 1);
update employee set source='WECOM' where employee_no like 'wc_%';
