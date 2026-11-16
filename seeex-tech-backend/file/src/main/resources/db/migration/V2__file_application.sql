alter table file_metadata add column application_code varchar(128) not null default 'platform' comment '所属应用';
create index ix_file_application_owner on file_metadata(application_code,tenant_id,owner_id,status);
