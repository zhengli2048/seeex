-- 员工-部门关系（多部门，最多一个主部门）
create table if not exists employee_department (
  id bigint not null auto_increment comment 'primary key',
  employee_id bigint not null comment 'employee id',
  department_id bigint not null comment 'department id',
  job_grade_id bigint comment 'job grade in this department',
  is_primary tinyint(1) not null default 0 comment 'primary department',
  created_at datetime(3) not null default current_timestamp(3) comment 'created time',
  updated_at datetime(3) not null default current_timestamp(3) on update current_timestamp(3) comment 'updated time',
  deleted_at datetime(3) null comment 'deleted time',
  primary key (id),
  unique key uk_employee_department (employee_id, department_id),
  key ix_employee_department_emp (employee_id)
) engine=InnoDB default charset=utf8mb4 comment='employee department membership';

-- 部门负责人（仅组织属性，不自动赋权）
alter table department add column leader_employee_id bigint null comment 'department leader employee id';

-- 职级默认角色
alter table job_grade add column default_role_id bigint null comment 'default role id';
