package com.seeex.tech.web.rbac;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.seeex.tech.common.api.ErrorCode;
import com.seeex.tech.common.exception.BizException;
import com.seeex.tech.web.rbac.mapper.*;
import com.seeex.tech.web.rbac.entity.RoleDataScopeDO;
import com.seeex.tech.web.governance.*;
import com.seeex.tech.web.organization.mapper.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.*;
/** 合并数据范围为实际集合；默认仅本人，所有配置均在服务端执行。 */
@Service
@RequiredArgsConstructor
public class DataScopeService {
    private final RoleDataScopeMapper roleDataScopeMapper;
    private final ApplicationPolicyMapper policies;
    private final ResourceMapper resources;
    private final DepartmentMapper departments;
    private final EmployeeDepartmentMapper memberships;
    private final ObjectMapper json;
    /** 兼容角色范围解析；没有员工身份时不展开部门。 */
    public DataScope resolve(Set<Long> roleIds, String resourceCode) { return resolveFor(0,roleIds,resourceCode); }
    /** 计算个人、部门、后代和自定义集合的并集；ALL 显式表示不限行。 */
    public DataScope resolveFor(long employeeId, Set<Long> roleIds, String resourceCode) {
        List<Rule> rules = new ArrayList<>();
        if (!roleIds.isEmpty()) { roleDataScopeMapper.selectList(Wrappers.<RoleDataScopeDO>lambdaQuery().in(RoleDataScopeDO::getRoleId,roleIds).eq(RoleDataScopeDO::getResourceCode,resourceCode).isNull(RoleDataScopeDO::getDeletedAt)).forEach(row -> rules.add(new Rule(row.getScopeType(),row.getScopeConfig()))); }
        var resource = resources.selectOne(Wrappers.<com.seeex.tech.web.rbac.entity.ResourceDO>lambdaQuery().eq(com.seeex.tech.web.rbac.entity.ResourceDO::getPermissionCode,resourceCode).eq(com.seeex.tech.web.rbac.entity.ResourceDO::getStatus,"ACTIVE").isNull(com.seeex.tech.web.rbac.entity.ResourceDO::getDeletedAt));
        if (resource != null) { policies.selectList(Wrappers.<ApplicationPolicyDO>lambdaQuery().eq(ApplicationPolicyDO::getResourceId,resource.getId()).eq(ApplicationPolicyDO::getApplicationCode,resource.getApplicationCode()).eq(ApplicationPolicyDO::getStatus,"ACTIVE").eq(ApplicationPolicyDO::getEffect,"ALLOW")).stream().filter(row -> "EMPLOYEE".equals(row.getSubjectType()) && Long.valueOf(employeeId).equals(row.getSubjectId()) || "ROLE".equals(row.getSubjectType()) && roleIds.contains(row.getSubjectId())).forEach(row -> rules.add(new Rule(row.getScopeType(),row.getScopeConfig()))); }
        Set<Long> ds = new HashSet<>(), es = new HashSet<>();
        if (rules.isEmpty()) { if (employeeId > 0) es.add(employeeId); return new DataScope(ScopeType.SELF,Set.of(),Set.copyOf(es)); }
        var activeDepartments = departments.selectList(Wrappers.<com.seeex.tech.web.organization.entity.DepartmentDO>lambdaQuery().eq(com.seeex.tech.web.organization.entity.DepartmentDO::getStatus,"ACTIVE").isNull(com.seeex.tech.web.organization.entity.DepartmentDO::getDeletedAt));
        Set<Long> activeIds = activeDepartments.stream().map(com.seeex.tech.web.organization.entity.DepartmentDO::getId).collect(java.util.stream.Collectors.toSet());
        for (Rule rule : rules) {
            ScopeType type;
            try { type = ScopeType.valueOf(rule.type()); } catch (RuntimeException ex) { throw new BizException(ErrorCode.CONFLICT,"数据范围配置无效，请联系管理员"); }
            if (type == ScopeType.ALL) return new DataScope(ScopeType.ALL,Set.of(),Set.of());
            if (type == ScopeType.SELF) { if (employeeId > 0) es.add(employeeId); }
            else if (type == ScopeType.CUSTOM) {
                try { var node = json.readTree(rule.config() == null ? "{}" : rule.config()); node.path("departmentIds").forEach(id -> { if (id.canConvertToLong() && activeIds.contains(id.longValue())) ds.add(id.longValue()); }); node.path("employeeIds").forEach(id -> { if (id.canConvertToLong() && id.longValue() > 0) es.add(id.longValue()); }); }
                catch (com.fasterxml.jackson.core.JsonProcessingException ex) { throw new BizException(ErrorCode.CONFLICT,"自定义数据范围格式无效"); }
            } else {
                Set<Long> own = memberships.selectList(Wrappers.<com.seeex.tech.web.organization.entity.EmployeeDepartmentDO>lambdaQuery().eq(com.seeex.tech.web.organization.entity.EmployeeDepartmentDO::getEmployeeId,employeeId).isNull(com.seeex.tech.web.organization.entity.EmployeeDepartmentDO::getDeletedAt)).stream().map(com.seeex.tech.web.organization.entity.EmployeeDepartmentDO::getDepartmentId).filter(activeIds::contains).collect(java.util.stream.Collectors.toSet());
                if (type == ScopeType.DEPARTMENT_AND_DESCENDANTS) { boolean changed; do { changed = false; for (var department : activeDepartments) if (own.contains(department.getParentId()) && own.add(department.getId())) changed = true; } while (changed); }
                ds.addAll(own);
            }
        }
        return new DataScope(ScopeType.CUSTOM,Set.copyOf(ds),Set.copyOf(es));
    }
    private record Rule(String type,String config) { }
    /** ALL表示无限制，其余使用明确集合判断，空集合表示无可见行。 */
    public record DataScope(ScopeType type, Set<Long> departmentIds, Set<Long> employeeIds) { }
    /** 数据范围类型。 */
    public enum ScopeType { SELF, CUSTOM, DEPARTMENT, DEPARTMENT_AND_DESCENDANTS, ALL }
}
