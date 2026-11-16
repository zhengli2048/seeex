-- 系统平台资源统一标记为 platform 应用
update resource set application_code = 'platform' where application_code is null and status = 'ACTIVE';
