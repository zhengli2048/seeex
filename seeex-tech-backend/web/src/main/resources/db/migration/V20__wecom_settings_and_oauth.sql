create table wecom_connection_settings (
 id bigint not null comment '单公司配置主键',
 encrypted_config mediumtext not null comment 'AES GCM 加密配置',
 primary key(id)
) engine=InnoDB default charset=utf8mb4 comment='企业微信加密连接配置';
create table wecom_oauth_state (
 id bigint not null auto_increment comment '主键',
 state_hash varchar(64) not null comment '状态摘要',
 expires_at datetime(3) not null comment '失效时间',
 used_at datetime(3) null comment '消费时间',
 primary key(id), unique key uk_wecom_state(state_hash)
) engine=InnoDB default charset=utf8mb4 comment='企业微信一次性登录状态';

alter table employee add column last_synced_at datetime(3) null comment '最近企业微信同步时间';

alter table sync_run add column failed_count int not null default 0 comment '失败数量';
