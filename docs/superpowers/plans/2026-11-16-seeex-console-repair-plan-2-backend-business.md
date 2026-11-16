# Plan 2：组织 / RBAC / 应用 / 审计后端闭环 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让组织/RBAC 的写路径真正可用：软删除切换、编码自动生成、多部门、职级默认角色、授权来源追踪、DENY、应用注册持久化、AOP 审计。

**Architecture:** 在 Plan 1 的数据模型与认证权限核心之上，逐项落地 spec §7 与 ADR-6（软删除）。数据访问 MyBatis-Plus，审计用 Spring AOP 注解 + 切面，应用注册中心从进程内 Map 切换到数据库。

**Tech Stack:** Java 21、Spring Boot 4、MyBatis-Plus、Spring AOP、JUnit 5 + Mockito。

**Spec:** `docs/superpowers/specs/2026-11-16-seeex-console-repair-design.md`

## Global Constraints

- 依赖 Plan 1 的迁移列与 `SequenceGenerator`/`AccountService`/`PermissionService`。
- 软删除一律写 `deleted_at`，查询过滤 `deleted_at is null`；员工 `status` 用 `ACTIVE/DISABLED/LEFT/LOCKED`。
- 业务编号用 `SequenceGenerator`；公开方法中文 Javadoc；禁魔法数字；`BizException`。
- 提交前 `mvn verify`（含 Checkstyle）。

---

## File Structure

**Modify:**
- `.../organization/OrganizationManagementService.java`
- `.../rbac/RbacManagementService.java`
- `.../registry/ApplicationRegistryService.java`

**Create:**
- `.../rbac/audit/AuditLog.java`（注解）
- `.../rbac/audit/AuditAspect.java`（切面）
- `.../rbac/audit/AuditService.java`
- `.../rbac/audit/AuditLogDO.java` + `.../rbac/audit/mapper/AuditLogMapper.java`
- `.../api/AuditController.java`

---

### Task 1: 软删除切换 — status=DELETED → deleted_at

**Files:**
- Modify: `.../organization/OrganizationManagementService.java`
- Modify: `.../rbac/RbacManagementService.java`

**Interfaces:**
- Consumes: Plan 1 Task 1 的 `deleted_at` 列。
- Produces: 删除方法写 `setDeletedAt(now)`，查询 wrapper 用 `.isNull(x::getDeletedAt)` 替代 `.ne(status, DELETED)`。

- [ ] **Step 1: 写失败测试（删除后查不到但记录保留）**

```java
@Test
void 软删除写deletedAt且列表不返回() {
    EmployeeDO e = new EmployeeDO(); e.setId(1L); e.setStatus("ACTIVE");
    when(employeeMapper.selectById(1L)).thenReturn(e);
    service.deleteEmployees(List.of(1L));
    assertThat(e.getDeletedAt()).isNotNull();
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `mvn -pl web -am test -Dtest=OrganizationManagementServiceTest -q`
Expected: FAIL（当前实现 setStatus(DELETED)）。

- [ ] **Step 3: 实现切换**

`deleteEmployees/deleteDepartments/deleteJobGrades/deleteRoles/deleteResources`：`consumer` 改为 `entity -> entity.setDeletedAt(LocalDateTime.now())`。查询 wrapper 中 `.ne(Xxx::getStatus, DELETED)` 改为 `.isNull(Xxx::getDeletedAt)`（删除 `DELETED` 常量，改 `private static final LocalDateTime` 不需要，直接用 now）。

- [ ] **Step 4: 运行测试通过 + 全量回归**

Run: `mvn -pl web -am test -q`
Expected: PASS（含 RbacManagementServiceTest）。

- [ ] **Step 5: Commit**

```bash
git add seeex-tech-backend/web/src/main/java/com/seeex/tech/web/organization/OrganizationManagementService.java seeex-tech-backend/web/src/main/java/com/seeex/tech/web/rbac/RbacManagementService.java
git commit -m "refactor: switch soft delete from status=DELETED to deleted_at"
```

---

### Task 2: 编码自动生成

**Files:**
- Modify: `.../organization/OrganizationManagementService.java`
- Modify: `.../rbac/RbacManagementService.java`

**Interfaces:**
- Consumes: `SequenceGenerator.next(prefix)`（Plan 1 Task 6）。
- Produces: `createXxx` 在 `code`/`employeeNo`/`permissionCode` 为空时自动生成；`permissionCode` 空时默认取 `code`。

- [ ] **Step 1: 写失败测试（code 为空自动生成）**

```java
@Test
void 部门编码为空时自动生成() {
    when(sequenceGenerator.next("DEPT")).thenReturn("DEPT2026111600000001");
    var cmd = new DepartmentCommand(null, "研发部", null, null);
    when(departmentMapper.insert(any())).thenAnswer(inv -> { ((DepartmentDO) inv.getArgument(0)).setId(1L); return 1; });
    var item = service.createDepartment(cmd);
    assertThat(item.code()).isEqualTo("DEPT2026111600000001");
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `mvn -pl web -am test -Dtest=OrganizationManagementServiceTest -q`
Expected: FAIL（当前 requireText(code) 抛异常）。

- [ ] **Step 3: 实现**

`createDepartment/createJobGrade/createRole`：`String code = command.code(); if (code == null || code.isBlank()) code = sequenceGenerator.next("DEPT"/"JG"/"ROLE");`（前缀按实体）。`createEmployee`：`employeeNo` 空时 `next("EMP")`。`createResource`：`code` 空时 `next("RES")`，`permissionCode` 空时 `permissionCode = code`。保留唯一约束兜底。

- [ ] **Step 4: 运行测试通过**

Run: 同 Step 2。
Expected: PASS。

- [ ] **Step 5: Commit**

```bash
git add seeex-tech-backend/web/src/main/java/com/seeex/tech/web/organization/OrganizationManagementService.java seeex-tech-backend/web/src/main/java/com/seeex/tech/web/rbac/RbacManagementService.java
git commit -m "feat: auto-generate codes when blank on create"
```

---

### Task 3: 员工多部门

**Files:**
- Modify: `.../organization/OrganizationManagementService.java`
- Test: `.../organization/EmployeeDepartmentTest.java`

**Interfaces:**
- Consumes: `EmployeeDepartmentMapper`（Plan 1 Task 5）。
- Produces: `EmployeeCommand` 增加 `List<DepartmentMembership> memberships`（`DepartmentMembership(Long departmentId, Long jobGradeId, Boolean primary)`）；create/update 时维护 `employee_department` 关系，最多一个 primary。

- [ ] **Step 1: 写失败测试（创建员工写入多部门关系）**

```java
@Test
void 创建员工写入部门关系() {
    var memberships = List.of(new DepartmentMembership(10L, 7L, true), new DepartmentMembership(11L, null, false));
    var cmd = new EmployeeCommand("E1", "张三", "ACTIVE", 10L, 7L, memberships);
    service.createEmployee(cmd);
    verify(employeeDepartmentMapper, times(2)).insert(any(EmployeeDepartmentDO.class));
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `mvn -pl web -am test -Dtest=EmployeeDepartmentTest -q`
Expected: FAIL（EmployeeCommand 无 memberships 字段）。

- [ ] **Step 3: 实现**

`EmployeeCommand` record 增加 `memberships` 字段（默认空）。`createEmployee/updateEmployee` 在写员工后，删除旧关系（`updateEmployee` 时软删）并批量 `insert` 新关系；校验 primary 至多一条。`primaryDepartmentId` 字段保留为便捷冗余，与 memberships 中 `primary=true` 一致。

- [ ] **Step 4: 运行测试通过**

Run: 同 Step 2。
Expected: PASS。

- [ ] **Step 5: Commit**

```bash
git add seeex-tech-backend/web/src/main/java/com/seeex/tech/web/organization/OrganizationManagementService.java
git commit -m "feat: support multiple department membership for employees"
```

---

### Task 4: 职级默认角色管理

**Files:**
- Modify: `.../organization/OrganizationManagementService.java`（或新增 `JobGradeRoleService`）
- Test: `.../organization/JobGradeRoleTest.java`

**Interfaces:**
- Produces: 职级 CRUD 支持 `defaultRoleId`；`JobGradeCommand` 增加 `defaultRoleId`。权限计算已在 Plan 1 Task 9 读取 `default_role_id`。

- [ ] **Step 1: 写失败测试（职级可绑定默认角色）**

```java
@Test
void 职级绑定默认角色() {
    var cmd = new JobGradeCommand("JG1", "P7", 7, "ACTIVE", 3L);
    service.createJobGrade(cmd);
    verify(jobGradeMapper).insert(argThat(g -> g.getDefaultRoleId() == 3L));
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `mvn -pl web -am test -Dtest=JobGradeRoleTest -q`
Expected: FAIL。

- [ ] **Step 3: 实现**

`JobGradeDO` 已有 `defaultRoleId` 字段（Plan 1 Task 5），`JobGradeCommand` 增加 `defaultRoleId`，`createJobGrade/updateJobGrade` 写入。更新默认角色时，仅影响此后继承，不改已有手动绑定。

- [ ] **Step 4: 运行测试通过**

Run: 同 Step 2。
Expected: PASS。

- [ ] **Step 5: Commit**

```bash
git add seeex-tech-backend/web/src/main/java/com/seeex/tech/web/organization/OrganizationManagementService.java
git commit -m "feat: bind default role to job grade"
```

---

### Task 5: 授权来源追踪

**Files:**
- Modify: `.../rbac/RbacManagementService.java`

**Interfaces:**
- Produces: `replaceRoleAssignments`/`replaceGrants`/`replaceEmployeeGrants` 写入 `assignment_source`（手动调用一律 `MANUAL`），企微/职级自动来源在 Plan 3 写入。

- [ ] **Step 1: 写失败测试（手动绑定写 MANUAL 来源）**

```java
@Test
void 手动角色绑定来源为MANUAL() {
    service.replaceRoleAssignments("EMPLOYEE", 1L, List.of(2L));
    verify(subjectRoleAssignmentMapper).insert(argThat(a -> "MANUAL".equals(a.getAssignmentSource())));
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `mvn -pl web -am test -Dtest=RbacManagementServiceTest -q`
Expected: FAIL。

- [ ] **Step 3: 实现**

`SubjectRoleAssignmentDO` 已含 `assignmentSource`（Plan 1 Task 5）。`replaceRoleAssignments` 插入时 `assignment.setAssignmentSource("MANUAL")`。同理 `RoleResourceDO`/`SubjectResourceGrantDO` 写入 `MANUAL`。

- [ ] **Step 4: 运行测试通过**

Run: 同 Step 2。
Expected: PASS。

- [ ] **Step 5: Commit**

```bash
git add seeex-tech-backend/web/src/main/java/com/seeex/tech/web/rbac/RbacManagementService.java
git commit -m "feat: track assignment source on role bindings and grants"
```

---

### Task 6: DENY 授权 + 变更差异预览

**Files:**
- Modify: `.../rbac/RbacController.java`、`.../rbac/RbacManagementService.java`

**Interfaces:**
- Produces: `GrantCommand.effect` 支持 `ALLOW`/`DENY`（已支持，补校验）；新增 `GET /api/rbac/roles/{id}/grants/diff?applicationCode=` 返回 `{ added, removed }` 变更前后差异（前端 Plan 4 差异预览依赖）。

- [ ] **Step 1: 写失败测试（非法 effect 拒绝）**

```java
@Test
void 非法effect被拒绝() {
    var cmd = List.of(new GrantCommand(1L, "MAYBE"));
    assertThatThrownBy(() -> service.replaceGrants(1L, cmd, null))
        .isInstanceOf(BizException.class);
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `mvn -pl web -am test -Dtest=RbacManagementServiceTest -q`
Expected: FAIL（当前非法 effect 静默转 ALLOW）。

- [ ] **Step 3: 实现**

`replaceGrants/replaceEmployeeGrants`：effect 非 `ALLOW`/`DENY` 时抛 `BizException(VALIDATION_FAILED, "effect must be ALLOW or DENY")`。新增 `grantsDiff(long roleId, String applicationCode)`：对比当前 grants 与提交前快照，返回 added/removed 权限码。

- [ ] **Step 4: 运行测试通过**

Run: 同 Step 2。
Expected: PASS。

- [ ] **Step 5: Commit**

```bash
git add seeex-tech-backend/web/src/main/java/com/seeex/tech/web/rbac
git commit -m "feat: validate grant effect and add diff preview"
```

---

### Task 7: 应用注册中心 DB 持久化

**Files:**
- Modify: `.../registry/ApplicationRegistryService.java`

**Interfaces:**
- Produces: `listApps()` 从 `application_registry` 读（不再只读内存）；`register` 写 DB；`createExchangeCode/exchange` 落 `auth_sso_exchange_code`（60s 一次性）；CI token 只存 `ci_token_hash`。

- [ ] **Step 1: 写失败测试（listApps 从 DB 读）**

```java
@Test
void 列出应用从数据库读取() {
    when(applicationMapper.selectList(any())).thenReturn(List.of(app("ai-recruitment")));
    var apps = service.listApps();
    assertThat(apps).hasSize(1);
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `mvn -pl web -am test -Dtest=ApplicationRegistryServiceTest -q`
Expected: FAIL（当前只读内存 Map）。

- [ ] **Step 3: 实现**

`listApps` 改为 `applicationMapper.selectList(status=ACTIVE)` 组装 `AppView`；`register` 仍写内存缓存 + DB（启动时从 DB 回读填充内存）；`createExchangeCode` 写 `auth_sso_exchange_code`（存 code_hash），`exchange` 校验 + 置 used。保留 raw CI token 仅创建/轮换时返回一次。

- [ ] **Step 4: 运行测试通过**

Run: 同 Step 2。
Expected: PASS。

- [ ] **Step 5: Commit**

```bash
git add seeex-tech-backend/web/src/main/java/com/seeex/tech/web/registry/ApplicationRegistryService.java
git commit -m "feat: persist application registry and SSO exchange codes to database"
```

---

### Task 8: AOP 审计 + 查询接口

**Files:**
- Create: `.../rbac/audit/AuditLog.java`、`AuditAspect.java`、`AuditService.java`、`AuditLogDO.java`、`AuditLogMapper.java`
- Create: `.../api/AuditController.java`

**Interfaces:**
- Produces: `@AuditLog(action="CREATE_EMPLOYEE", resourceType="EMPLOYEE")` 注解；切面记录操作者/动作/资源/结果/来源 IP/请求摘要 + before/after JSON；`GET /api/audit-logs`（筛选 + 分页）、`GET /api/audit-logs/{id}`（详情）。

- [ ] **Step 1: 写失败测试（注解方法调用后落审计）**

```java
@Test
void 注解方法调用后写入审计() {
    auditService.record(new AuditContext(1L, "CREATE_EMPLOYEE", "EMPLOYEE", 5L, "127.0.0.1", "{}", "{\"id\":5}"));
    verify(auditLogMapper).insert(any(AuditLogDO.class));
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `mvn -pl web -am test -Dtest=AuditServiceTest -q`
Expected: FAIL（类不存在）。

- [ ] **Step 3: 实现**

`@AuditLog` 注解 + `@Around` 切面：从 `SecurityContextHolder` 取 employeeId、`HttpServletRequest` 取 source IP；`before_json` 在目标方法执行前捕获（需要 service 提供 before 快照时用 `@AuditLog` 上的 `beforeProvider` 或直接记录入参）；`after_json` 用返回值。`AuditController` 提供筛选查询与详情。审计覆盖：登录、锁定、授权变更、组织变更、资源菜单变更（后续 Plan 3 补同步/解密审计）。

- [ ] **Step 4: 运行测试通过 + 全量回归**

Run: `mvn -pl web -am test -q`
Expected: PASS。

- [ ] **Step 5: Commit**

```bash
git add seeex-tech-backend/web/src/main/java/com/seeex/tech/web/rbac/audit seeex-tech-backend/web/src/main/java/com/seeex/tech/web/api/AuditController.java
git commit -m "feat: add AOP audit logging and query endpoint"
```

---

## Self-Review

- **Spec 覆盖**：§7.1→Task 2；§7.2→Task 3；§7.3→Task 4；§7.4→Task 5；§7.5→Task 6；§7.6→Task 7；§7.7→Task 8；ADR-6（软删除）→Task 1。
- **占位符扫描**：无 TBD；每 task 含测试与实现骨架。
- **类型一致性**：`SequenceGenerator.next` 签名 Plan 1→Task 2 一致；`DepartmentMembership(Long,Long,Boolean)` 在 Task 3 定义并使用；`AuditContext` 在 Task 8 定义并使用。
