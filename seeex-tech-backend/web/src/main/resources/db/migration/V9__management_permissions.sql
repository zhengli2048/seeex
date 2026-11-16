alter table resource add column status varchar(32) not null default 'ACTIVE' comment 'status';

insert into role (code, name, status)
values ('platform_admin', 'Platform Administrator', 'ACTIVE')
on duplicate key update name = values(name), status = values(status);

insert into resource (code, permission_code, name, resource_type, status)
values
  ('platform.dashboard.view', 'platform:dashboard:view', 'Platform dashboard', 'PAGE', 'ACTIVE'),
  ('platform.apps.view', 'platform:apps:view', 'Application registry', 'PAGE', 'ACTIVE'),
  ('employees.view', 'employees:view', 'Employee view', 'API', 'ACTIVE'),
  ('employees.create', 'employees:create', 'Employee create', 'API', 'ACTIVE'),
  ('employees.update', 'employees:update', 'Employee update', 'API', 'ACTIVE'),
  ('employees.delete', 'employees:delete', 'Employee delete', 'API', 'ACTIVE'),
  ('departments.view', 'departments:view', 'Department view', 'API', 'ACTIVE'),
  ('departments.create', 'departments:create', 'Department create', 'API', 'ACTIVE'),
  ('departments.update', 'departments:update', 'Department update', 'API', 'ACTIVE'),
  ('departments.delete', 'departments:delete', 'Department delete', 'API', 'ACTIVE'),
  ('job-grades.view', 'job-grades:view', 'Job grade view', 'API', 'ACTIVE'),
  ('job-grades.create', 'job-grades:create', 'Job grade create', 'API', 'ACTIVE'),
  ('job-grades.update', 'job-grades:update', 'Job grade update', 'API', 'ACTIVE'),
  ('job-grades.delete', 'job-grades:delete', 'Job grade delete', 'API', 'ACTIVE'),
  ('rbac.roles.view', 'rbac:roles:view', 'Role view', 'API', 'ACTIVE'),
  ('rbac.roles.create', 'rbac:roles:create', 'Role create', 'API', 'ACTIVE'),
  ('rbac.roles.update', 'rbac:roles:update', 'Role update', 'API', 'ACTIVE'),
  ('rbac.roles.delete', 'rbac:roles:delete', 'Role delete', 'API', 'ACTIVE'),
  ('rbac.resources.view', 'rbac:resources:view', 'Resource view', 'API', 'ACTIVE'),
  ('rbac.resources.create', 'rbac:resources:create', 'Resource create', 'API', 'ACTIVE'),
  ('rbac.resources.update', 'rbac:resources:update', 'Resource update', 'API', 'ACTIVE'),
  ('rbac.resources.delete', 'rbac:resources:delete', 'Resource delete', 'API', 'ACTIVE'),
  ('files.view', 'files:view', 'File view', 'API', 'ACTIVE'),
  ('files.create', 'files:create', 'File create', 'API', 'ACTIVE'),
  ('files.delete', 'files:delete', 'File delete', 'API', 'ACTIVE')
on duplicate key update name = values(name), status = values(status);

insert into role_resource (role_id, resource_id, effect)
select r.id, x.id, 'ALLOW'
from role r
join resource x
where r.code = 'platform_admin'
on duplicate key update effect = values(effect);

insert into subject_role_assignment (subject_type, subject_id, role_id)
select 'EMPLOYEE', e.id, r.id
from employee e
join role r on r.code = 'platform_admin'
where e.employee_no = 'E0001'
  and not exists (
    select 1 from subject_role_assignment s
    where s.subject_type = 'EMPLOYEE' and s.subject_id = e.id and s.role_id = r.id
  );
