-- 本地账号密码重置与登录/刷新并发安全；仅编写未执行。
alter table employee_account add column auth_version bigint not null default 0;
alter table auth_token_session add column account_id bigint null, add column account_version bigint null;
-- 旧本地会话没有已验证的凭证版本，发布时要求重新登录；企微会话仍按员工状态管理。
update auth_token_session set status='REVOKED',revoked_at=utc_timestamp() where client_id='web-console' and status='ACTIVE';
