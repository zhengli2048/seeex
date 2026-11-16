-- 补充企业微信与审计权限码（前端菜单依赖）
insert into resource (code, permission_code, name, resource_type, status)
values
  ('wecom.config.view', 'wecom:config:view', 'WeCom config', 'PAGE', 'ACTIVE'),
  ('wecom.sync.run', 'wecom:sync:run', 'WeCom sync run', 'ACTION', 'ACTIVE'),
  ('wecom.events.view', 'wecom:events:view', 'WeCom events view', 'API', 'ACTIVE'),
  ('audit.view', 'audit:view', 'Audit log view', 'PAGE', 'ACTIVE')
on duplicate key update name = values(name), status = values(status);

-- 给角色授权关系补唯一约束，避免重复授权
alter table role_resource add unique key uk_role_resource (role_id, resource_id);

-- 绑定到 platform_admin 角色
insert ignore into role_resource (role_id, resource_id, effect)
select r.id, x.id, 'ALLOW'
from role r
join resource x on x.permission_code in ('wecom:config:view', 'wecom:sync:run', 'wecom:events:view', 'audit:view')
where r.code = 'platform_admin';
