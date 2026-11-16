alter table resource add column application_code varchar(128) comment 'application boundary';
alter table resource add column parent_id bigint comment 'parent resource id';
alter table resource add column route_path varchar(512) comment 'front-end route';
alter table resource add column icon varchar(128) comment 'icon name';
alter table resource add column sort_order int not null default 0 comment 'sort order';
alter table resource add column visible tinyint(1) not null default 1 comment 'visible in menu';
alter table resource add column is_public tinyint(1) not null default 0 comment 'public resource';
create index ix_resource_application_parent on resource(application_code, parent_id, sort_order);
