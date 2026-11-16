# Seeex RBAC 权限计算与服务接口设计

## 1. 设计目标

本设计定义一个深模块 `PermissionService`，把角色来源合并、有效期判断、`ALLOW/DENY` 计算、部门范围展开、数据范围和菜单过滤集中在一个接口后面。Web 控制器、前端菜单接口和业务模块不直接拼接 RBAC SQL。

## 2. 权限模型

主体角色来源：

```text
subject_role_assignment(subject_type=EMPLOYEE, subject_id=employee_id)
subject_role_assignment(subject_type=DEPARTMENT, subject_id=employee_department.department_id)
subject_role_assignment(subject_type=JOB_GRADE, subject_id=employee.job_grade_id)
```

只使用有效关系：

```sql
deleted_at is null
and revoked_at is null
and (effective_at is null or effective_at <= current_timestamp)
and (expires_at is null or expires_at > current_timestamp)
```

角色资源的最终规则：

```text
ALLOW = 有效角色关联到的 ALLOW 资源并集
DENY  = 有效角色关联到的 DENY 资源并集
FINAL = ALLOW - DENY
```

如果同一资源同时存在 `ALLOW` 与 `DENY`，结果为拒绝。没有资源登记时，API 默认拒绝。

## 3. SQL 查询模型

### 3.1 员工有效角色

```sql
with subject_roles as (
    select s.role_id
    from subject_role_assignment s
    where s.subject_type = 'EMPLOYEE'
      and s.subject_id = :employee_id
      and s.deleted_at is null
      and s.revoked_at is null
      and (s.effective_at is null or s.effective_at <= current_timestamp)
      and (s.expires_at is null or s.expires_at > current_timestamp)

    union

    select s.role_id
    from employee_department ed
    join subject_role_assignment s
      on s.subject_type = 'DEPARTMENT'
     and s.subject_id = ed.department_id
    where ed.employee_id = :employee_id
      and ed.deleted_at is null
      and ed.revoked_at is null
      and s.deleted_at is null
      and s.revoked_at is null
      and (s.effective_at is null or s.effective_at <= current_timestamp)
      and (s.expires_at is null or s.expires_at > current_timestamp)

    union

    select s.role_id
    from employee e
    join subject_role_assignment s
      on s.subject_type = 'JOB_GRADE'
     and s.subject_id = e.job_grade_id
    where e.id = :employee_id
      and e.deleted_at is null
      and e.job_grade_id is not null
      and s.deleted_at is null
      and s.revoked_at is null
      and (s.effective_at is null or s.effective_at <= current_timestamp)
      and (s.expires_at is null or s.expires_at > current_timestamp)
)
select distinct role_id from subject_roles;
```

部门角色的 `department_scope` 若需要影响下属部门，服务层应先展开员工实际部门及其祖先链，再进入同一个角色查询；不能在调用方分别实现。

### 3.2 有效权限

```sql
with effective_roles as (...),
allows as (
    select distinct rr.resource_id
    from effective_roles er
    join role_resource rr on rr.role_id = er.role_id
    where rr.effect = 'ALLOW'
      and rr.deleted_at is null
      and (rr.effective_at is null or rr.effective_at <= current_timestamp)
      and (rr.expires_at is null or rr.expires_at > current_timestamp)
),
denies as (
    select distinct rr.resource_id
    from effective_roles er
    join role_resource rr on rr.role_id = er.role_id
    where rr.effect = 'DENY'
      and rr.deleted_at is null
      and (rr.effective_at is null or rr.effective_at <= current_timestamp)
      and (rr.expires_at is null or rr.expires_at > current_timestamp)
)
select r.id, r.resource_type, r.code, r.permission_code, r.path
from resource r
join allows a on a.resource_id = r.id
left join denies d on d.resource_id = r.id
where d.resource_id is null
  and r.status = 'ACTIVE'
  and r.deleted_at is null;
```

### 3.3 单权限判断

```sql
select exists (
    select 1
    from effective_permissions
    where permission_code = :permission_code
      and resource_type = :resource_type
);
```

实际实现可将 `effective_permissions` 替换为 CTE 或 Repository 查询，接口调用方不感知 SQL 细节。

## 4. Java 模块接口

权限模块的外部接口保持小而稳定：

```java
public interface PermissionService {
    PermissionSnapshot snapshot(long employeeId);

    boolean hasPermission(long employeeId, String permissionCode);

    MenuTree visibleMenus(long employeeId);

    DataScope resolveDataScope(long employeeId, String resourceCode);
}
```

返回对象：

```java
public record PermissionSnapshot(
        long employeeId,
        Set<String> allowCodes,
        Set<String> denyCodes,
        Set<String> effectiveCodes,
        Instant calculatedAt) {
    public boolean allows(String code) {
        return effectiveCodes.contains(code);
    }
}
```

```java
public record DataScope(
        ScopeType type,
        Set<Long> departmentIds,
        Set<Long> employeeIds) {}

public enum ScopeType {
    SELF, DEPARTMENT, DEPARTMENT_AND_DESCENDANTS, ALL, CUSTOM
}
```

接口不返回角色表、关系表或 SQL 中间结果；调用方只拿到完成计算的权限快照、菜单树和数据范围。

## 5. Repository 接口

Repository 是数据库适配器，不暴露给 Web 层：

```java
public interface PermissionRepository {
    Set<Long> findEffectiveRoleIds(long employeeId, Instant now);

    PermissionRows findRoleResources(Set<Long> roleIds, Instant now);

    List<MenuResourceRow> findMenus(Set<String> effectivePermissionCodes);

    List<DataScopeRow> findDataScopes(Set<Long> roleIds, String resourceCode, Instant now);
}
```

`PermissionService` 负责合并和规则，`PermissionRepository` 负责 PostgreSQL 查询。这样可以用内存 Adapter 测试 `DENY` 优先和有效期，不需要启动数据库。

## 6. 部门范围计算

部门授权需要将员工所在部门与授权部门作用域匹配：

```text
CURRENT                  员工属于授权部门
DESCENDANTS              员工属于授权部门的下属部门
CURRENT_AND_DESCENDANTS  员工属于授权部门或其下属部门
```

部门树查询使用 PostgreSQL recursive CTE 或维护闭包表。第一版使用 recursive CTE，避免新增闭包表；当部门规模或权限查询延迟不满足 NFR 时，再引入 `department_closure` Adapter。

## 7. 数据范围计算

`data_scope` 按角色和资源匹配，多个有效范围按最宽范围合并；数据范围只负责缩小业务查询范围，不提供独立 DENY 语义：

```text
ALL > DEPARTMENT_AND_DESCENDANTS > DEPARTMENT > CUSTOM > SELF
```

`CUSTOM` 固定 JSON 结构：

```json
{
  "department_ids": [10, 20],
  "employee_ids": [1001, 1002]
}
```

业务模块不得直接读取 `scope_config`。必须调用：

```java
public interface DataScopeService {
    DataScope resolve(long employeeId, String resourceCode);

    <T> QueryCondition apply(QueryCondition query, DataScope scope, DataScopeColumnMapping mapping);
}
```

业务模块提供字段映射，数据范围模块负责组合条件；`ALL` 直接返回无过滤条件，其他范围不得返回空条件。

## 8. 动态菜单过滤

菜单生成规则：

1. 读取有效菜单项和引用资源；
2. 过滤菜单自身 `is_visible=false`、资源禁用或已删除节点；
3. 页面/Tab 入口必须具备对应有效权限；
4. 父节点没有直接权限但有可见子节点时保留父节点；
5. 菜单节点没有可见子节点且自身无权限时移除；
6. 按 `parent_id + sort_order` 构建树。

菜单只影响导航，不替代页面和 API 鉴权。

## 9. Web API 鉴权接口

后端提供一个统一授权注解/拦截器入口：

```java
@RequirePermission("users:api:list")
public ApiResponse<PageResponse<UserView>> listUsers(...) { ... }
```

拦截器职责：

1. 从本系统会话取得 `employeeId`；
2. 调用 `PermissionService.hasPermission`；
3. 无权限返回 `AUTHORIZATION`；
4. 权限模块异常时默认拒绝并记录 request_id；
5. 不使用前端传入的权限码作为鉴权依据。

公共接口必须显式标注白名单，例如登录、健康检查和企业微信回调。

## 10. 缓存和失效

第一版可以使用进程内短 TTL 缓存，键为：

```text
permission:employee:{employeeId}
menu:employee:{employeeId}
datascope:employee:{employeeId}:{resourceCode}
```

以下事件必须使对应员工权限缓存失效：

- 员工角色绑定变化；
- 部门角色绑定变化；
- 职级变化；
- 职级默认角色变化；
- 角色资源变化；
- 员工部门关系变化；
- 资源或菜单状态变化。

第一版不在数据库写权限快照，避免快照和授权关系出现双重事实来源。

## 11. 必测规则

```text
员工直接角色 ALLOW + 部门角色 ALLOW => 允许
任意有效角色 DENY => 拒绝
过期 ALLOW => 不生效
已撤销角色绑定 => 不生效
禁用角色/资源 => 不生效
未登记 API => 拒绝
无权限父菜单但有可见子菜单 => 保留父菜单
CUSTOM 数据范围 => 只能通过 DataScopeService 解释
```

## 12. 接口性能与失败策略

- `hasPermission` 目标 P95 小于 100ms；
- `visibleMenus` 目标 P95 小于 200ms；
- 权限缓存失效期间可重新计算，不允许旧权限永久保留；
- 权限查询失败默认拒绝；
- 缓存解析失败删除缓存并回源数据库；
- 数据范围解析失败不得退化为 `ALL`。
