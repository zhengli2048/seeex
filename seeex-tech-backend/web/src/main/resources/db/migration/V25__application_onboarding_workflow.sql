-- 受限应用接入流程；仅编写，未执行。所有表同库使用InnoDB，不启用外部引擎自动建表。

create table onboarding_request (
    id bigint not null auto_increment primary key,
    request_no varchar(64) not null,
    applicant_id bigint not null,
    department_id bigint null,
    state varchar(32) not null,
    decision varchar(32) null,
    row_version bigint not null default 0,
    attempt_no int not null,
    revision int not null,
    definition_id bigint null,
    node_index int not null,
    app_code varchar(64) not null,
    data_json longtext not null,
    fulfillment_json longtext null,
    fulfiller_id bigint null,
    fulfillment_candidates longtext null,
    exception_message varchar(512) null,
    submitted_at datetime(3) null,
    created_at datetime(3) not null default current_timestamp(3),
    updated_at datetime(3) not null default current_timestamp(3) on update current_timestamp(3),
    deleted_at datetime(3) null,
    unique key uk_onboard_no(request_no),
    key idx_onboard_applicant(applicant_id,state),
    key idx_onboard_state(state,created_at)
) engine=InnoDB default charset=utf8mb4;

create table onboarding_revision (
    id bigint not null auto_increment primary key,
    request_id bigint not null,
    revision int not null,
    definition_id bigint null,
    data_json longtext not null,
    submitted_at datetime(3) not null,
    created_at datetime(3) not null default current_timestamp(3),
    updated_at datetime(3) not null default current_timestamp(3),
    unique key uk_onboard_revision(request_id,revision)
) engine=InnoDB default charset=utf8mb4;

create table onboarding_task (
    id bigint not null auto_increment primary key,
    request_id bigint not null,
    attempt_no int not null,
    node_index int not null,
    node_name varchar(128) not null,
    mode varchar(32) not null,
    candidate_id bigint not null,
    assignee_id bigint null,
    state varchar(32) not null,
    row_version bigint not null default 0,
    due_at datetime(3) null,
    created_at datetime(3) not null default current_timestamp(3),
    completed_at datetime(3) null,
    unique key uk_onboard_task(request_id,attempt_no,node_index,candidate_id),
    key idx_onboard_candidate(candidate_id,state),
    updated_at datetime(3) not null default current_timestamp(3) on update current_timestamp(3)
) engine=InnoDB default charset=utf8mb4;

create table onboarding_log (
    id bigint not null auto_increment primary key,
    request_id bigint not null,
    attempt_no int not null,
    actor_id bigint not null,
    action varchar(32) null,
    comment varchar(1400) null,
    key idx_onboard_log_request(request_id,id),
    created_at datetime(3) not null default current_timestamp(3),
    updated_at datetime(3) not null default current_timestamp(3) on update current_timestamp(3)
) engine=InnoDB default charset=utf8mb4;

create table onboarding_definition (
    id bigint not null auto_increment primary key,
    definition_key varchar(64) not null,
    creation_key varchar(128) not null,
    creation_hash varchar(64) not null,
    name varchar(128) not null,
    version int not null,
    status varchar(32) null,
    model_json longtext not null,
    row_version bigint not null default 0,
    created_by bigint not null,
    published_by bigint null,
    published_at datetime(3) null,
    created_at datetime(3) not null default current_timestamp(3),
    updated_at datetime(3) not null default current_timestamp(3) on update current_timestamp(3),
    unique key uk_onboard_definition(definition_key,version),
    unique key uk_onboard_definition_creation(created_by,creation_key)
) engine=InnoDB default charset=utf8mb4;

create table onboarding_delivery (
    id bigint not null auto_increment primary key,
    request_id bigint not null,
    delivery_version int not null,
    recipient_id bigint not null,
    info_json longtext not null,
    secret_cipher longtext null,
    key_id varchar(64) not null,
    expires_at datetime(3) not null,
    redeemed_at datetime(3) null,
    acknowledged_at datetime(3) null,
    row_version bigint not null default 0,
    created_at datetime(3) not null default current_timestamp(3),
    unique key uk_onboard_delivery(request_id,delivery_version),
    updated_at datetime(3) not null default current_timestamp(3) on update current_timestamp(3)
) engine=InnoDB default charset=utf8mb4;

create table onboarding_message (
    id bigint not null auto_increment primary key,
    request_id bigint not null,
    recipient_id bigint not null,
    event_key varchar(160) not null,
    title varchar(128) not null,
    content varchar(1000) not null,
    read_at datetime(3) null,
    row_version bigint not null default 0,
    created_at datetime(3) not null default current_timestamp(3),
    unique key uk_onboard_message(recipient_id,event_key),
    key idx_onboard_inbox(recipient_id,created_at),
    updated_at datetime(3) not null default current_timestamp(3) on update current_timestamp(3)
) engine=InnoDB default charset=utf8mb4;

create table onboarding_notice (
    id bigint not null auto_increment primary key,
    message_id bigint not null,
    recipient_id bigint not null,
    event_key varchar(160) not null,
    state varchar(32) not null,
    attempts int not null,
    next_attempt_at datetime(3) not null,
    lease_until datetime(3) null,
    owner_token varchar(64) null,
    last_error varchar(512) null,
    row_version bigint not null default 0,
    created_at datetime(3) not null default current_timestamp(3),
    unique key uk_onboard_notice(event_key,recipient_id),
    key idx_onboard_notice_due(state,next_attempt_at),
    updated_at datetime(3) not null default current_timestamp(3) on update current_timestamp(3)
) engine=InnoDB default charset=utf8mb4;

create table onboarding_action (
    id bigint not null auto_increment primary key,
    request_id bigint not null,
    actor_id bigint not null,
    operation varchar(32) not null,
    idempotency_key varchar(128) not null,
    payload_hash varchar(64) not null,
    created_at datetime(3) not null default current_timestamp(3),
    unique key uk_onboard_action(actor_id,operation,idempotency_key),
    updated_at datetime(3) not null default current_timestamp(3) on update current_timestamp(3)
) engine=InnoDB default charset=utf8mb4;

create table onboarding_reservation (
    app_code varchar(64) not null primary key,request_id bigint not null,unique key uk_onboard_reserved_request(request_id),
    created_at datetime(3) not null default current_timestamp(3),
    updated_at datetime(3) not null default current_timestamp(3) on update current_timestamp(3)
) engine=InnoDB default charset=utf8mb4;

create table onboarding_binding (
    environment varchar(16) not null primary key,definition_id bigint not null,
    row_version bigint not null default 0,
    created_at datetime(3) not null default current_timestamp(3),
    updated_at datetime(3) not null default current_timestamp(3) on update current_timestamp(3)
) engine=InnoDB default charset=utf8mb4;

insert into configuration_revision(config_key,revision) values('onboarding-binding',0);

alter table application_registry add column originating_request_id bigint null, add column initial_delivery_pending tinyint not null default 0, add unique key uk_app_originating_request(originating_request_id);

insert into resource(code,permission_code,name,resource_type,status,application_code,entry_type,route_path,visible,sort_order,menu_category) select 'onboarding.app-requests:own:view','app-requests:own:view','我的应用申请','MENU','ACTIVE','platform','ADMIN','/application-requests',1,60,'应用审批' where not exists(select 1 from resource where permission_code='app-requests:own:view');

insert into resource(code,permission_code,name,resource_type,status,application_code,entry_type,route_path,visible,sort_order,menu_category) select 'onboarding.workflow:tasks:view','workflow:tasks:view','我的审批任务','MENU','ACTIVE','platform','ADMIN','/approval-tasks',1,61,'应用审批' where not exists(select 1 from resource where permission_code='workflow:tasks:view');

insert into resource(code,permission_code,name,resource_type,status,application_code,entry_type,route_path,visible,sort_order,menu_category) select 'onboarding.app-fulfillments:view','app-fulfillments:view','平台办理','MENU','ACTIVE','platform','ADMIN','/application-fulfillments',1,62,'应用审批' where not exists(select 1 from resource where permission_code='app-fulfillments:view');

insert into resource(code,permission_code,name,resource_type,status,application_code,entry_type,route_path,visible,sort_order,menu_category) select 'onboarding.workflow:definitions:view','workflow:definitions:view','审批流配置','MENU','ACTIVE','platform','ADMIN','/workflow-definitions',1,63,'应用审批' where not exists(select 1 from resource where permission_code='workflow:definitions:view');

insert into resource(code,permission_code,name,resource_type,status,application_code,entry_type,route_path,visible,sort_order,menu_category) select 'onboarding.notifications:own:view','notifications:own:view','通知中心','MENU','ACTIVE','platform','ADMIN','/inbox',1,64,'应用审批' where not exists(select 1 from resource where permission_code='notifications:own:view');

insert into resource(code,permission_code,name,resource_type,status,application_code,entry_type,route_path,visible,sort_order,menu_category) select 'onboarding.app-requests:manage','app-requests:manage','接入申请管理','MENU','ACTIVE','platform','ADMIN','/application-requests/manage',1,65,'应用审批' where not exists(select 1 from resource where permission_code='app-requests:manage');

insert into resource(code,permission_code,name,resource_type,status,application_code,entry_type,route_path,visible,sort_order,menu_category) select 'onboarding.notifications:operate','notifications:operate','通知异常','MENU','ACTIVE','platform','ADMIN','/notification-jobs',1,66,'应用审批' where not exists(select 1 from resource where permission_code='notifications:operate');

insert into resource(code,permission_code,name,resource_type,status,application_code,entry_type,route_path,visible,sort_order,menu_category) select 'onboarding.app-requests:create','app-requests:create','申请应用接入','ACTION','ACTIVE','platform','ADMIN',null,0,67,'应用审批' where not exists(select 1 from resource where permission_code='app-requests:create');

insert into resource(code,permission_code,name,resource_type,status,application_code,entry_type,route_path,visible,sort_order,menu_category) select 'onboarding.workflow:tasks:act','workflow:tasks:act','处理审批任务','ACTION','ACTIVE','platform','ADMIN',null,0,68,'应用审批' where not exists(select 1 from resource where permission_code='workflow:tasks:act');

insert into resource(code,permission_code,name,resource_type,status,application_code,entry_type,route_path,visible,sort_order,menu_category) select 'onboarding.workflow:definitions:write','workflow:definitions:write','编辑审批流程','ACTION','ACTIVE','platform','ADMIN',null,0,69,'应用审批' where not exists(select 1 from resource where permission_code='workflow:definitions:write');

insert into resource(code,permission_code,name,resource_type,status,application_code,entry_type,route_path,visible,sort_order,menu_category) select 'onboarding.workflow:definitions:publish','workflow:definitions:publish','发布审批流程','ACTION','ACTIVE','platform','ADMIN',null,0,70,'应用审批' where not exists(select 1 from resource where permission_code='workflow:definitions:publish');

insert into resource(code,permission_code,name,resource_type,status,application_code,entry_type,route_path,visible,sort_order,menu_category) select 'onboarding.app-fulfillments:act','app-fulfillments:act','办理应用接入','ACTION','ACTIVE','platform','ADMIN',null,0,71,'应用审批' where not exists(select 1 from resource where permission_code='app-fulfillments:act');

insert into resource(code,permission_code,name,resource_type,status,application_code,entry_type,route_path,visible,sort_order,menu_category) select 'onboarding.app-deliveries:reissue','app-deliveries:reissue','补发交付凭证','ACTION','ACTIVE','platform','ADMIN',null,0,72,'应用审批' where not exists(select 1 from resource where permission_code='app-deliveries:reissue');

insert into resource(code,permission_code,name,resource_type,status,application_code,entry_type,route_path,visible,sort_order,menu_category) select 'onboarding.app-deliveries:own:view','app-deliveries:own:view','领取本人交付','ACTION','ACTIVE','platform','ADMIN',null,0,73,'应用审批' where not exists(select 1 from resource where permission_code='app-deliveries:own:view');

insert into resource(code,permission_code,name,resource_type,status,application_code,entry_type,route_path,visible,sort_order,menu_category) select 'onboarding.platform:apps:override','platform:apps:override','紧急直接注册应用','ACTION','ACTIVE','platform','ADMIN',null,0,74,'应用审批' where not exists(select 1 from resource where permission_code='platform:apps:override');

insert into role_resource(role_id,resource_id,effect,assignment_source) select ro.id,re.id,'ALLOW','MANUAL' from role ro join resource re on re.code like 'onboarding.%' and re.permission_code<>'platform:apps:override' where ro.code='platform_admin' and not exists(select 1 from role_resource rr where rr.role_id=ro.id and rr.resource_id=re.id);

create table onboarding_participant (
    id bigint not null auto_increment primary key,request_id bigint not null,actor_id bigint not null,attempt_no int not null,unique key uk_onboard_participant(request_id,actor_id,attempt_no),key idx_onboard_participant_actor(actor_id,request_id),
    created_at datetime(3) not null default current_timestamp(3),
    updated_at datetime(3) not null default current_timestamp(3) on update current_timestamp(3)
) engine=InnoDB default charset=utf8mb4;
