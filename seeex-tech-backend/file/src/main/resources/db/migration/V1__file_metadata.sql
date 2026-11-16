create table if not exists file_metadata (
    id bigint not null auto_increment,
    idempotency_key varchar(128) not null,
    tenant_id varchar(64) not null,
    owner_id varchar(64) not null,
    biz_type varchar(64),
    biz_id varchar(128),
    bucket_name varchar(128) not null,
    object_key varchar(512) not null,
    file_name varchar(255) not null,
    content_type varchar(128) not null,
    file_size bigint not null,
    etag varchar(128),
    sha256 char(64),
    visibility varchar(32) not null default 'PRIVATE',
    status varchar(32) not null default 'PENDING',
    storage_class varchar(32) not null default 'STANDARD',
    created_at timestamp not null default current_timestamp,
    confirmed_at timestamp null,
    deleted_at timestamp null,
    updated_at timestamp not null default current_timestamp,
    primary key (id),
    unique key uk_file_metadata_idempotency (idempotency_key),
    unique key uk_file_metadata_object (object_key)
);
create index idx_file_metadata_owner on file_metadata(tenant_id, owner_id, status);
create index idx_file_metadata_biz on file_metadata(tenant_id, biz_type, biz_id);
create index idx_file_metadata_status_created on file_metadata(status, created_at);
