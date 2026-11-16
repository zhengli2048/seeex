# Plan 1：后端数据模型 + 认证 + 权限核心 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 建立真实认证与权限核心：员工账号密码登录、subject=真实 employeeId、多部门+职级默认角色+数据范围的权限计算、未登记 API 默认拒绝。

**Architecture:** 分两步——先通过 Flyway 增量迁移补齐数据模型（软删除、多部门、职级默认角色、授权来源、数据范围、审计/同步字段），再实现认证服务与权限服务。数据访问统一 MyBatis-Plus，权限计算沿用 `PermissionService` 深模块接口并扩展。

**Tech Stack:** Java 21、Spring Boot 4、MyBatis-Plus、Flyway（MySQL）、JUnit 5 + Mockito、BCrypt。

**Spec:** `docs/superpowers/specs/2026-11-16-seeex-console-repair-design.md`

## Global Constraints

- Java 21，Spring Boot 4.0.9，Maven 多模块（本计划只改 `seeex-tech-backend/web` 与 `common`）。
- 数据访问统一 MyBatis-Plus；禁止业务层手写 SQL（`PermissionRepository` 已用 `@Select` 注解属既有例外，扩展沿用该模式）。
- 公开类/方法必须写中文 Javadoc；禁止魔法数字；业务异常抛 `BizException(ErrorCode)`。
- 错误响应用 `message` 字段；分页响应 `{ items, total }`。
- 软删除用 `deleted_at`；员工状态 `ACTIVE/DISABLED/LEFT/LOCKED`；资源类型 7 类。
- 业务编号格式 `YYYYMMDDNNNNNNNN`（日期+序列）。
- 提交前 `mvn verify`（含 Checkstyle）必须通过。

---

## File Structure

**迁移脚本（Create）：**
- `seeex-tech-backend/web/src/main/resources/db/migration/V13__soft_delete_and_status.sql`
- `V14__employee_department_and_grade_role.sql`
- `V15__assignment_source_and_data_scope.sql`
- `V16__audit_and_sync_state_machine.sql`

**实体与 Mapper（Create/Modify）：**
- Modify `.../organization/entity/EmployeeDO.java`、`DepartmentDO.java`、`JobGradeDO.java`
- Modify `.../rbac/entity/RoleDO.java`、`ResourceDO.java`、`RoleResourceDO.java`、`SubjectRoleAssignmentDO.java`
- Create `.../organization/entity/EmployeeDepartmentDO.java` + `.../organization/mapper/EmployeeDepartmentMapper.java`
- Create `.../rbac/entity/RoleDataScopeDO.java` + `.../rbac/mapper/RoleDataScopeMapper.java`
- Create `.../auth/entity/EmployeeAccountDO.java` + `.../auth/mapper/EmployeeAccountMapper.java`

**服务（Create/Modify）：**
- Create `.../common/` 或 `.../organization/SequenceGenerator.java`（业务编号生成器）
- Create `.../auth/AccountService.java`
- Modify `.../api/AuthController.java`、`.../api/PlatformController.java`
- Modify `.../auth/ManagementAuthorizationService.java`
- Modify `.../rbac/PermissionService.java`、`.../rbac/PermissionRepository.java`
- Create `.../rbac/DataScopeService.java`
- Modify `.../config/SecurityConfig.java`

---

### Task 1: Flyway 迁移 V13 — 软删除字段 + 状态扩展 + 员工账号唯一约束

**Files:**
- Create: `seeex-tech-backend/web/src/main/resources/db/migration/V13__soft_delete_and_status.sql`

**Interfaces:**
- Produces: `employee.deleted_at`、`department.deleted_at`、`job_grade.deleted_at`、`role.deleted_at`、`resource.deleted_at`、`employee_account.login_phone` 唯一索引。后续 Task 5 实体依赖这些列。

- [ ] **Step 1: 编写迁移脚本**

```sql
-- 软删除字段（与现有 status=DELETED 共存，Phase 2 再切换写入逻辑）
alter table employee add column deleted_at datetime(3) null comment 'deleted time';
alter table department add column deleted_at datetime(3) null comment 'deleted time';
alter table job_grade add column deleted_at datetime(3) null comment 'deleted time';
alter table role add column deleted_at datetime(3) null comment 'deleted time';
alter table resource add column deleted_at datetime(3) null comment 'deleted time';
alter table role_resource add column deleted_at datetime(3) null comment 'deleted time';
alter table subject_role_assignment add column deleted_at datetime(3) null comment 'deleted time';

-- 员工状态扩展（新增 DISABLED/LEFT/LOCKED 语义，varchar 无需改列，仅注释）
alter table employee modify column status varchar(32) not null default 'ACTIVE' comment 'ACTIVE/DISABLED/LEFT/LOCKED';

-- 员工账号手机号业务唯一
alter table employee_account add unique key uk_account_login_phone (login_phone);
```

- [ ] **Step 2: 校验脚本可执行**

Run: `cd seeex-tech-backend && mvn -pl web -am flyway:migrate -Dflyway.url=jdbc:mysql://localhost:3306/seeex -Dflyway.user=root -Dflyway.password=root -q`
Expected: 迁移成功，`show columns from employee like 'deleted_at';` 返回该列。

- [ ] **Step 3: Commit**

```bash
git add seeex-tech-backend/web/src/main/resources/db/migration/V13__soft_delete_and_status.sql
git commit -m "feat: add soft-delete columns and employee status taxonomy"
```

---

### Task 2: Flyway 迁移 V14 — 员工多部门 + 部门负责人 + 职级默认角色

**Files:**
- Create: `seeex-tech-backend/web/src/main/resources/db/migration/V14__employee_department_and_grade_role.sql`

**Interfaces:**
- Produces: `employee_department` 表、`department.leader_employee_id`、`job_grade.default_role_id`。Task 5/9 依赖。

- [ ] **Step 1: 编写迁移脚本**

```sql
-- 员工-部门关系（多部门，最多一个主部门）
create table if not exists employee_department (
  id bigint not null auto_increment comment 'primary key',
  employee_id bigint not null comment 'employee id',
  department_id bigint not null comment 'department id',
  job_grade_id bigint comment 'job grade in this department',
  is_primary tinyint(1) not null default 0 comment 'primary department',
  created_at datetime(3) not null default current_timestamp(3),
  updated_at datetime(3) not null default current_timestamp(3) on update current_timestamp(3),
  deleted_at datetime(3) null,
  primary key (id),
  unique key uk_employee_department (employee_id, department_id),
  key ix_employee_department_emp (employee_id)
) engine=InnoDB default charset=utf8mb4 comment='employee department membership';

-- 部门负责人（仅组织属性，不自动赋权）
alter table department add column leader_employee_id bigint null comment 'department leader employee id';

-- 职级默认角色
alter table job_grade add column default_role_id bigint null comment 'default role id';
```

- [ ] **Step 2: 校验脚本可执行**

Run: 同 Task 1 Step 2 的 flyway 命令。
Expected: `show tables like 'employee_department';` 返回该表。

- [ ] **Step 3: Commit**

```bash
git add seeex-tech-backend/web/src/main/resources/db/migration/V14__employee_department_and_grade_role.sql
git commit -m "feat: add multi-department membership and job grade default role"
```

---

### Task 3: Flyway 迁移 V15 — 授权来源追踪 + 数据范围表

**Files:**
- Create: `seeex-tech-backend/web/src/main/resources/db/migration/V15__assignment_source_and_data_scope.sql`

**Interfaces:**
- Produces: `role_resource.assignment_source/source_key/sync_run_id`、`subject_role_assignment.assignment_source/source_key/sync_run_id`、`role_data_scope` 表。Task 5/9/10 依赖。

- [ ] **Step 1: 编写迁移脚本**

```sql
-- 授权来源追踪
alter table role_resource add column assignment_source varchar(32) null comment 'MANUAL/JOB_GRADE/DEPARTMENT/WE_COM';
alter table role_resource add column source_key varchar(128) null comment 'source business key';
alter table role_resource add column sync_run_id varchar(64) null comment 'sync run id';
alter table subject_role_assignment add column assignment_source varchar(32) null comment 'MANUAL/JOB_GRADE/DEPARTMENT/WE_COM';
alter table subject_role_assignment add column source_key varchar(128) null comment 'source business key';
alter table subject_role_assignment add column sync_run_id varchar(64) null comment 'sync run id';

-- 角色数据范围
create table if not exists role_data_scope (
  id bigint not null auto_increment comment 'primary key',
  role_id bigint not null comment 'role id',
  resource_code varchar(160) not null comment 'resource code',
  scope_type varchar(32) not null comment 'SELF/DEPARTMENT/DEPARTMENT_AND_DESCENDANTS/ALL/CUSTOM',
  scope_config json null comment 'CUSTOM scope config',
  created_at datetime(3) not null default current_timestamp(3),
  updated_at datetime(3) not null default current_timestamp(3) on update current_timestamp(3),
  deleted_at datetime(3) null,
  primary key (id),
  key ix_role_data_scope_role (role_id, resource_code)
) engine=InnoDB default charset=utf8mb4 comment='role data scope';
```

- [ ] **Step 2: 校验脚本可执行**

Run: 同 Task 1 Step 2 的 flyway 命令。
Expected: `show tables like 'role_data_scope';` 返回该表。

- [ ] **Step 3: Commit**

```bash
git add seeex-tech-backend/web/src/main/resources/db/migration/V15__assignment_source_and_data_scope.sql
git commit -m "feat: add assignment source tracking and role data scope table"
```

---

### Task 4: Flyway 迁移 V16 — 审计扩展 + 同步状态机字段

**Files:**
- Create: `seeex-tech-backend/web/src/main/resources/db/migration/V16__audit_and_sync_state_machine.sql`

**Interfaces:**
- Produces: `audit_log` 扩展字段、`sync_run` 状态、`sync_event` 状态机字段。Plan 2/3 依赖。

- [ ] **Step 1: 编写迁移脚本**

```sql
-- 审计扩展
alter table audit_log add column source_ip varchar(64) null comment 'source ip';
alter table audit_log add column request_summary varchar(1024) null comment 'request summary';
alter table audit_log add column before_json json null comment 'before change snapshot';
alter table audit_log add column after_json json null comment 'after change snapshot';
alter table audit_log add column result varchar(32) null comment 'SUCCESS/FAILED';

-- 同步批次状态（PENDING/PARTIAL 语义，varchar 无需改列，仅注释）
alter table sync_run modify column status varchar(32) not null default 'PENDING' comment 'PENDING/RUNNING/SUCCESS/PARTIAL/FAILED';

-- 事件状态机字段
alter table sync_event add column retry_count int not null default 0 comment 'retry count';
alter table sync_event add column next_retry_at datetime(3) null comment 'next retry time';
alter table sync_event add column processed_at datetime(3) null comment 'processed time';
alter table sync_event add column error_message varchar(1024) null comment 'last error';
alter table sync_event add column payload mediumtext null comment 'original event payload';
alter table sync_event modify column status varchar(32) not null default 'RECEIVED' comment 'RECEIVED/PROCESSING/SUCCESS/RETRY_WAIT/DEAD_LETTER';
```

- [ ] **Step 2: 校验脚本可执行**

Run: 同 Task 1 Step 2 的 flyway 命令。
Expected: `show columns from sync_event like 'retry_count';` 返回该列。

- [ ] **Step 3: Commit**

```bash
git add seeex-tech-backend/web/src/main/resources/db/migration/V16__audit_and_sync_state_machine.sql
git commit -m "feat: extend audit and sync event state machine fields"
```

---

### Task 5: 实体 DO 与 Mapper 更新/新增

**Files:**
- Modify: `.../organization/entity/EmployeeDO.java`、`DepartmentDO.java`、`JobGradeDO.java`
- Modify: `.../rbac/entity/RoleDO.java`、`ResourceDO.java`、`RoleResourceDO.java`、`SubjectRoleAssignmentDO.java`
- Create: `.../organization/entity/EmployeeDepartmentDO.java`、`.../organization/mapper/EmployeeDepartmentMapper.java`
- Create: `.../rbac/entity/RoleDataScopeDO.java`、`.../rbac/mapper/RoleDataScopeMapper.java`
- Create: `.../auth/entity/EmployeeAccountDO.java`、`.../auth/mapper/EmployeeAccountMapper.java`

**Interfaces:**
- Consumes: Task 1-4 迁移产生的列。
- Produces: `EmployeeDepartmentDO`（`Long id, Long employeeId, Long departmentId, Long jobGradeId, Boolean isPrimary, LocalDateTime deletedAt`）；`RoleDataScopeDO`（`Long id, Long roleId, String resourceCode, String scopeType, String scopeConfig, LocalDateTime deletedAt`）；`EmployeeAccountDO`（`Long id, Long employeeId, String loginPhone, String passwordHash, String status`）。Mapper 均 `extends BaseMapper<T>`。

- [ ] **Step 1: 为既有 DO 增加新字段**

对每个既有 DO，按迁移列补齐字段 + getter/setter + 中文 Javadoc。示例（EmployeeDO）：

```java
/** 员工来源：LOCAL/WECOM。 */
private String source;
/** 删除时间，软删除标记。 */
private java.time.LocalDateTime deletedAt;
```

- [ ] **Step 2: 新增 DO + Mapper**

```java
package com.seeex.tech.web.organization.entity;

/** 员工-部门关系。 */
public class EmployeeDepartmentDO {
    private Long id;
    private Long employeeId;
    private Long departmentId;
    private Long jobGradeId;
    private Boolean isPrimary;
    private java.time.LocalDateTime createdAt;
    private java.time.LocalDateTime updatedAt;
    private java.time.LocalDateTime deletedAt;
    // getter/setter 省略，字段命名与列名 snake_case 由 MyBatis-Plus 映射
}
```

```java
package com.seeex.tech.web.organization.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.seeex.tech.web.organization.entity.EmployeeDepartmentDO;

/** 员工部门关系 Mapper。 */
public interface EmployeeDepartmentMapper extends BaseMapper<EmployeeDepartmentDO> { }
```

（`RoleDataScopeDO`/`RoleDataScopeMapper`、`EmployeeAccountDO`/`EmployeeAccountMapper` 同理，字段见 Interfaces。）

- [ ] **Step 3: 编译验证**

Run: `cd seeex-tech-backend && mvn -pl web -am compile -q`
Expected: 编译通过，无 Checkstyle 错误。

- [ ] **Step 4: Commit**

```bash
git add seeex-tech-backend/web/src/main/java/com/seeex/tech/web
git commit -m "feat: add entity fields and mappers for new data model"
```

---

### Task 6: 业务编号生成器

**Files:**
- Create: `.../organization/SequenceGenerator.java`
- Test: `.../organization/SequenceGeneratorTest.java`

**Interfaces:**
- Produces: `public String next(String prefix)` 返回 `YYYYMMDD` + 前缀 + 8 位当日自增序列，如 `next("EMP")` → `"EMP2026111600000001"`。基于数据库当日 max 派生 + 唯一约束兜底。

- [ ] **Step 1: 写失败测试**

```java
@Test
void 生成日期加序列格式的业务编号() {
    SequenceGenerator generator = new SequenceGenerator(mapper);
    String no = generator.next("EMP");
    assertThat(no).matches("EMP\\d{16}");
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `mvn -pl web -am test -Dtest=SequenceGeneratorTest -q`
Expected: FAIL（类不存在）。

- [ ] **Step 3: 实现**

```java
/** 日期+序列业务编号生成器。 */
@Service
public class SequenceGenerator {
    private static final String DATE_PATTERN = "yyyyMMdd";
    private final EmployeeMapper employeeMapper;
    // 前缀到各实体的当日 max 派生（employee_no/code），唯一约束兜底并发冲突
    public String next(String prefix) {
        String date = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern(DATE_PATTERN));
        // 查询当日该前缀已用序列，+1 后左补 8 位
        return prefix + date + String.format("%08d", nextSeq(prefix, date));
    }
}
```

- [ ] **Step 4: 运行测试通过**

Run: 同 Step 2。
Expected: PASS。

- [ ] **Step 5: Commit**

```bash
git add seeex-tech-backend/web/src/main/java/com/seeex/tech/web/organization/SequenceGenerator.java seeex-tech-backend/web/src/test/java/com/seeex/tech/web/organization/SequenceGeneratorTest.java
git commit -m "feat: add date-sequence business number generator"
```

---

### Task 7: AccountService — BCrypt 密码校验与账号查询

**Files:**
- Create: `.../auth/AccountService.java`
- Test: `.../auth/AccountServiceTest.java`

**Interfaces:**
- Consumes: `EmployeeAccountMapper`（Task 5）。
- Produces: `public boolean verify(String phone, String rawPassword)`；`public EmployeeAccountDO findByPhone(String phone)`。Task 8 依赖。

- [ ] **Step 1: 写失败测试**

```java
@Test
void 正确密码校验通过() {
    AccountService service = new AccountService(mapper, encoder);
    when(mapper.selectOne(any())).thenReturn(account("admin", encoder.encode("Seeex@2026")));
    assertThat(service.verify("admin", "Seeex@2026")).isTrue();
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `mvn -pl web -am test -Dtest=AccountServiceTest -q`
Expected: FAIL。

- [ ] **Step 3: 实现**

```java
/** 员工账号服务：登录凭证校验。 */
@Service
public class AccountService {
    private final EmployeeAccountMapper accountMapper;
    private final PasswordEncoder encoder; // BCryptPasswordEncoder

    public boolean verify(String phone, String rawPassword) {
        EmployeeAccountDO account = findByPhone(phone);
        if (account == null || !"ACTIVE".equals(account.getStatus())) return false;
        return encoder.matches(rawPassword, account.getPasswordHash());
    }
    public EmployeeAccountDO findByPhone(String phone) {
        return accountMapper.selectOne(new LambdaQueryWrapper<EmployeeAccountDO>()
                .eq(EmployeeAccountDO::getLoginPhone, phone));
    }
}
```

- [ ] **Step 4: 运行测试通过 + 编译**

Run: `mvn -pl web -am test -Dtest=AccountServiceTest -q`
Expected: PASS。

- [ ] **Step 5: Commit**

```bash
git add seeex-tech-backend/web/src/main/java/com/seeex/tech/web/auth/AccountService.java seeex-tech-backend/web/src/test/java/com/seeex/tech/web/auth/AccountServiceTest.java
git commit -m "feat: add account service with bcrypt verification"
```

---

### Task 8: AuthController 真实登录 + 移除 demo 绕过 + /me 真实化

**Files:**
- Modify: `.../api/AuthController.java`
- Modify: `.../auth/ManagementAuthorizationService.java`
- Modify: `.../api/PlatformController.java`
- Test: `.../api/AuthControllerTest.java`

**Interfaces:**
- Consumes: `AccountService.verify`（Task 7）、`TokenSessionService.issue`。
- Produces: `POST /api/auth/login` 入参 `{phone, password}`，成功返回 `{ access_token, refresh_token, expires_in, refresh_expires_in, employee_id, display_name, has_permission }`；`GET /api/me` 返回真实员工。

- [ ] **Step 1: 写失败测试（login 校验失败返回 UNAUTHORIZED）**

```java
@Test
void 密码错误登录失败() {
    when(accountService.verify("admin", "bad")).thenReturn(false);
    ApiResponse<?> resp = controller.login(new LoginRequest("admin", "bad"));
    assertThat(resp.getCode()).isEqualTo("UNAUTHORIZED");
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `mvn -pl web -am test -Dtest=AuthControllerTest -q`
Expected: FAIL。

- [ ] **Step 3: 实现真实登录**

`AuthController.login`：调用 `accountService.verify`，失败抛 `BizException(ErrorCode.UNAUTHORIZED, "账号或密码错误")`；成功用 `sessions.issue(String.valueOf(account.getEmployeeId()), "web-console")`，并回填员工摘要。

`ManagementAuthorizationService.require`：删除 `if ("demo".equals(subject)) return;` 分支。

`PlatformController.me`：从 Authentication 取 employeeId，查员工返回真实 `employeeId/displayName/roles/permissions`，不再硬编码 demo。

- [ ] **Step 4: 运行测试通过 + 全量编译**

Run: `mvn -pl web -am test -Dtest=AuthControllerTest -q`
Expected: PASS。

- [ ] **Step 5: Commit**

```bash
git add seeex-tech-backend/web/src/main/java/com/seeex/tech/web/api/AuthController.java seeex-tech-backend/web/src/main/java/com/seeex/tech/web/auth/ManagementAuthorizationService.java seeex-tech-backend/web/src/main/java/com/seeex/tech/web/api/PlatformController.java
git commit -m "feat: real authentication with bcrypt, remove demo bypass"
```

---

### Task 9: PermissionService 扩展 — 多部门职级默认角色 + 数据范围 + 可见菜单

**Files:**
- Modify: `.../rbac/PermissionService.java`
- Modify: `.../rbac/PermissionRepository.java`
- Create: `.../rbac/DataScopeService.java`
- Test: `.../rbac/PermissionServiceTest.java`

**Interfaces:**
- Consumes: `EmployeeDepartmentMapper`、`RoleDataScopeMapper`（Task 5）。
- Produces: `PermissionSnapshot snapshot(long employeeId)`（allowCodes/denyCodes/effectiveCodes/sources，sources 现在含具体角色名）；`DataScope resolveDataScope(long employeeId, String resourceCode)`；`MenuTree visibleMenus(long employeeId)`。Plan 2/4 依赖。

- [ ] **Step 1: 写失败测试（职级默认角色参与权限）**

```java
@Test
void 职级默认角色参与最终权限() {
    // employee 在部门1有职级P7，P7.default_role_id=role3，role3 授 employees:view
    when(repo.findEffectiveRoleIds(anyLong(), any())).thenReturn(Set.of(3L));
    when(repo.findRoleResources(eq(Set.of(3L)), any())).thenReturn(List.of(new PermissionRow("employees:view", "ALLOW")));
    var snapshot = service.snapshot(1L);
    assertThat(snapshot.effectiveCodes()).contains("employees:view");
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `mvn -pl web -am test -Dtest=PermissionServiceTest -q`
Expected: FAIL。

- [ ] **Step 3: 实现**

- `PermissionRepository.findEffectiveRoleIds` 改为三条来源：员工直绑 + 各 `employee_department.department_id` 的部门角色 + 各 `employee_department.job_grade_id` 对应职级 `default_role_id` 的角色。
- `sources` 按 `permissionCode → 来源名称集合`（含具体角色名、部门名、职级名）。
- `DataScopeService.resolve`：合并 `role_data_scope` 按 `ALL > DEPARTMENT_AND_DESCENDANTS > DEPARTMENT > CUSTOM > SELF` 取最宽。
- `visibleMenus`：从 resource 树过滤 `visible && ACTIVE`，保留有有效权限或有可见子节点的父节点，按 `parent_id + sort_order` 组树。

- [ ] **Step 4: 运行测试通过**

Run: 同 Step 2。
Expected: PASS。

- [ ] **Step 5: Commit**

```bash
git add seeex-tech-backend/web/src/main/java/com/seeex/tech/web/rbac
git commit -m "feat: extend permission service with default role, data scope, visible menus"
```

---

### Task 10: SecurityConfig 未登记 API 默认拒绝

**Files:**
- Modify: `.../config/SecurityConfig.java`
- Modify: `.../auth/ManagementAuthorizationService.java`

**Interfaces:**
- Consumes: `PermissionService.hasPermission`（Task 9）。
- Produces: 未登记资源码的受保护 API 默认拒绝；公共接口白名单（`/api/auth/login`、`/actuator/health/**`、`/api/integrations/wecom/events`）显式放行。

- [ ] **Step 1: 写失败测试（未登记 API 拒绝）**

```java
@Test
void 未登记权限码默认拒绝() {
    when(permissionService.hasPermission(1L, "employees:view")).thenReturn(false);
    assertThatThrownBy(() -> authorization.require(auth("1"), "employees:view"))
        .isInstanceOf(BizException.class);
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `mvn -pl web -am test -Dtest=ManagementAuthorizationServiceTest -q`
Expected: FAIL。

- [ ] **Step 3: 实现**

`ManagementAuthorizationService.require`：认证失败抛 `UNAUTHORIZED`；无有效权限抛 `FORBIDDEN`（已存在，保留）；新增「资源码未登记」判定——权限快照为空且无该码时同样拒绝（不因快照为空而放行）。

`SecurityConfig`：将公共接口白名单收敛为 `login/health/wecom events`，其余 `/api/**` 走 Bearer 鉴权 + 服务端 `require` 校验。

- [ ] **Step 4: 运行测试通过 + 全量回归**

Run: `mvn -pl web -am test -q`
Expected: 全部 PASS。

- [ ] **Step 5: Commit**

```bash
git add seeex-tech-backend/web/src/main/java/com/seeex/tech/web/config/SecurityConfig.java seeex-tech-backend/web/src/main/java/com/seeex/tech/web/auth/ManagementAuthorizationService.java
git commit -m "feat: deny unregistered APIs by default with explicit whitelist"
```

---

## Self-Review

- **Spec 覆盖**：§4（数据模型）→ Task 1-5；§5.1-5.4（账号/登录/demo/me 系列）→ Task 7-8；§5.5-5.6 + §6.2-6.3（权限计算/数据范围/可见菜单）→ Task 9；§5.7（未登记 API 默认拒绝）→ Task 10；§3 ADR-7（业务编号）→ Task 6。§7-§9 归 Plan 2/3/4。
- **占位符扫描**：无 TBD/TODO；测试与实现步骤均含实际代码骨架。
- **类型一致性**：`EmployeeDepartmentDO`/`RoleDataScopeDO`/`EmployeeAccountDO` 字段名在 Task 5 Interfaces 与 Task 7/9 使用处一致；`verify(phone, rawPassword)` 签名 Task 7→8 一致；`hasPermission(long, String)` 沿用既有签名。
