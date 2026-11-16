-- 账号凭证维护与普通员工资料修改分离；仅编写未执行。
insert into resource(code,permission_code,name,resource_type,status,application_code,entry_type,visible)
select 'employees.accounts','employees:accounts','维护员工登录账号','ACTION','ACTIVE','platform','ADMIN',0
where not exists(select 1 from resource where permission_code='employees:accounts');
insert into role_resource(role_id,resource_id,effect,assignment_source)
select ro.id,re.id,'ALLOW','MANUAL' from role ro join resource re on re.permission_code='employees:accounts'
where ro.code='platform_admin' and not exists(select 1 from role_resource rr where rr.role_id=ro.id and rr.resource_id=re.id);
