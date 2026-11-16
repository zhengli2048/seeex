package com.seeex.tech.web.rbac;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.seeex.tech.web.organization.entity.EmployeeDO;
import com.seeex.tech.web.organization.entity.EmployeeDepartmentDO;
import com.seeex.tech.web.organization.entity.JobGradeDO;
import com.seeex.tech.web.organization.mapper.EmployeeDepartmentMapper;
import com.seeex.tech.web.organization.mapper.EmployeeMapper;
import com.seeex.tech.web.organization.mapper.JobGradeMapper;
import com.seeex.tech.web.rbac.entity.ResourceDO;
import com.seeex.tech.web.rbac.entity.RoleResourceDO;
import com.seeex.tech.web.rbac.entity.SubjectResourceGrantDO;
import com.seeex.tech.web.rbac.entity.SubjectRoleAssignmentDO;
import com.seeex.tech.web.rbac.mapper.ResourceMapper;
import com.seeex.tech.web.rbac.mapper.RoleResourceMapper;
import com.seeex.tech.web.rbac.mapper.SubjectResourceGrantMapper;
import com.seeex.tech.web.rbac.mapper.SubjectRoleAssignmentMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** 从 RBAC 关系表计算员工最终权限，DENY 始终覆盖 ALLOW。 */
@Service
@RequiredArgsConstructor
public class PermissionService {

    private final com.seeex.tech.web.rbac.mapper.RoleMapper roleMapper;
    private final com.seeex.tech.web.organization.mapper.DepartmentMapper departmentMapper;
    private final EmployeeMapper employeeMapper;
    private final EmployeeDepartmentMapper employeeDepartmentMapper;
    private final JobGradeMapper jobGradeMapper;
    private final SubjectRoleAssignmentMapper assignmentMapper;
    private final RoleResourceMapper roleResourceMapper;
    private final SubjectResourceGrantMapper subjectGrantMapper;
    private final ResourceMapper resourceMapper;
    private final DataScopeService dataScopeService;
    private final com.seeex.tech.web.governance.ApplicationPolicyMapper policyMapper;
    private final com.seeex.tech.web.registry.mapper.ApplicationMapper applicationMapper;

    /** 计算员工最终权限快照。 */
    public PermissionSnapshot snapshot(long employeeId) {
        EmployeeDO employee = employeeMapper.selectById(employeeId);
        if (employee == null || employee.getDeletedAt() != null || !"ACTIVE".equals(employee.getStatus())) {
            return new PermissionSnapshot(employeeId, Set.of(), Set.of(), Set.of(), Map.of());
        }
        Map<Long, Set<String>> roleSources = roleSources(employeeId);
        Set<Long> roleIds = roleSources.keySet();
        Map<Long, String> resourceCodeById = loadResourceCodes(roleIds, employeeId);
        Set<String> allow = new HashSet<>();
        Set<String> deny = new HashSet<>();
        Map<String, Set<String>> sources = new HashMap<>();

        if (!roleIds.isEmpty()) {
            roleResourceMapper.selectList(new LambdaQueryWrapper<RoleResourceDO>()
                    .in(RoleResourceDO::getRoleId, roleIds).isNull(RoleResourceDO::getDeletedAt)).forEach(rr -> {
                String code = resourceCodeById.get(rr.getResourceId());
                if (code == null) {
                    return;
                }
                if ("DENY".equalsIgnoreCase(rr.getEffect())) {
                    deny.add(code);
                } else {
                    allow.add(code);
                }
                sources.computeIfAbsent(code, ignored -> new HashSet<>()).addAll(roleSources.getOrDefault(rr.getRoleId(), Set.of()));
            });
        }
        subjectGrantMapper.selectList(new LambdaQueryWrapper<SubjectResourceGrantDO>()
                .eq(SubjectResourceGrantDO::getSubjectType, "EMPLOYEE")
                .eq(SubjectResourceGrantDO::getSubjectId, employeeId)).forEach(grant -> {
            String code = resourceCodeById.get(grant.getResourceId());
            if (code == null) {
                return;
            }
            if ("DENY".equalsIgnoreCase(grant.getEffect())) {
                deny.add(code);
            } else {
                allow.add(code);
            }
            sources.computeIfAbsent(code, ignored -> new HashSet<>()).add("人员直接授权：" + employee.getDisplayName());
        });

        Set<String> activeApps = applicationMapper.selectList(com.baomidou.mybatisplus.core.toolkit.Wrappers.<com.seeex.tech.web.registry.entity.ApplicationDO>lambdaQuery().eq(com.seeex.tech.web.registry.entity.ApplicationDO::getStatus,"ACTIVE")).stream().map(com.seeex.tech.web.registry.entity.ApplicationDO::getAppCode).collect(Collectors.toSet());
        var matchedPolicies=policyMapper.selectList(com.baomidou.mybatisplus.core.toolkit.Wrappers.<com.seeex.tech.web.governance.ApplicationPolicyDO>lambdaQuery().eq(com.seeex.tech.web.governance.ApplicationPolicyDO::getStatus,"ACTIVE").and(query->{query.and(person->person.eq(com.seeex.tech.web.governance.ApplicationPolicyDO::getSubjectType,"EMPLOYEE").eq(com.seeex.tech.web.governance.ApplicationPolicyDO::getSubjectId,employeeId));if(!roleIds.isEmpty())query.or(role->role.eq(com.seeex.tech.web.governance.ApplicationPolicyDO::getSubjectType,"ROLE").in(com.seeex.tech.web.governance.ApplicationPolicyDO::getSubjectId,roleIds));}));
        var policyResources=new HashMap<Long,ResourceDO>(); var policyResourceIds=matchedPolicies.stream().map(com.seeex.tech.web.governance.ApplicationPolicyDO::getResourceId).collect(Collectors.toSet());
        if(!policyResourceIds.isEmpty())resourceMapper.selectList(new LambdaQueryWrapper<ResourceDO>().in(ResourceDO::getId,policyResourceIds).eq(ResourceDO::getStatus,"ACTIVE").isNull(ResourceDO::getDeletedAt)).forEach(resource->policyResources.put(resource.getId(),resource));
        for(var policy:matchedPolicies) {
            if(!activeApps.contains(policy.getApplicationCode()))continue;
            var resource=policyResources.get(policy.getResourceId()); if(resource==null || !policy.getApplicationCode().equals(resource.getApplicationCode()))continue;
            String code=resource.getPermissionCode(); if("DENY".equals(policy.getEffect()))deny.add(code);else if("ALLOW".equals(policy.getEffect()))allow.add(code);
            sources.computeIfAbsent(code,ignored->new HashSet<>()).add("应用策略："+policy.getName());
        }

        Set<String> effective = new HashSet<>(allow);
        effective.removeAll(deny);
        return new PermissionSnapshot(employeeId, allow, deny, effective,
                sources.entrySet().stream().collect(Collectors.toUnmodifiableMap(Map.Entry::getKey,
                        entry -> Set.copyOf(entry.getValue()))));
    }

    /** 判断员工是否拥有指定权限。 */
    public boolean hasPermission(long employeeId, String permissionCode) {
        return snapshot(employeeId).effectiveCodes().contains(permissionCode);
    }

    /** 解析员工对指定资源的数据范围。 */
    public DataScopeService.DataScope resolveDataScope(long employeeId, String resourceCode) {
        return dataScopeService.resolveFor(employeeId, collectRoleIds(employeeId), resourceCode);
    }

    /** 收集有效角色，来源包含人员、部门、职级名称。 */
    private Set<Long> collectRoleIds(long employeeId) { return roleSources(employeeId).keySet(); }

    private Map<Long, Set<String>> roleSources(long employeeId) {
        Map<Long,Set<String>> raw=new HashMap<>();
        assignmentMapper.selectList(new LambdaQueryWrapper<SubjectRoleAssignmentDO>().eq(SubjectRoleAssignmentDO::getSubjectType,"EMPLOYEE").eq(SubjectRoleAssignmentDO::getSubjectId,employeeId).isNull(SubjectRoleAssignmentDO::getDeletedAt)).forEach(row->raw.computeIfAbsent(row.getRoleId(),ignored->new HashSet<>()).add("人员角色"));
        var members=employeeDepartmentMapper.selectList(new LambdaQueryWrapper<EmployeeDepartmentDO>().eq(EmployeeDepartmentDO::getEmployeeId,employeeId).isNull(EmployeeDepartmentDO::getDeletedAt));
        var departmentIds=members.stream().map(EmployeeDepartmentDO::getDepartmentId).collect(Collectors.toSet());
        Map<Long,String> activeDepartments=new HashMap<>();
        if(!departmentIds.isEmpty()) departmentMapper.selectList(new LambdaQueryWrapper<com.seeex.tech.web.organization.entity.DepartmentDO>().in(com.seeex.tech.web.organization.entity.DepartmentDO::getId,departmentIds).eq(com.seeex.tech.web.organization.entity.DepartmentDO::getStatus,"ACTIVE").isNull(com.seeex.tech.web.organization.entity.DepartmentDO::getDeletedAt)).forEach(row->activeDepartments.put(row.getId(),row.getName()));
        if(!activeDepartments.isEmpty()) assignmentMapper.selectList(new LambdaQueryWrapper<SubjectRoleAssignmentDO>().eq(SubjectRoleAssignmentDO::getSubjectType,"DEPARTMENT").in(SubjectRoleAssignmentDO::getSubjectId,activeDepartments.keySet()).isNull(SubjectRoleAssignmentDO::getDeletedAt)).forEach(row->raw.computeIfAbsent(row.getRoleId(),ignored->new HashSet<>()).add("部门："+activeDepartments.get(row.getSubjectId())));
        var gradeIds=members.stream().filter(row->activeDepartments.containsKey(row.getDepartmentId())).map(EmployeeDepartmentDO::getJobGradeId).filter(java.util.Objects::nonNull).collect(Collectors.toSet());
        if(!gradeIds.isEmpty()) jobGradeMapper.selectList(new LambdaQueryWrapper<JobGradeDO>().in(JobGradeDO::getId,gradeIds).eq(JobGradeDO::getStatus,"ACTIVE").isNull(JobGradeDO::getDeletedAt)).forEach(row->{if(row.getDefaultRoleId()!=null) raw.computeIfAbsent(row.getDefaultRoleId(),ignored->new HashSet<>()).add("职级："+row.getName());});
        Map<Long,Set<String>> result=new HashMap<>(); raw.remove(null);
        if(!raw.isEmpty()) roleMapper.selectList(new LambdaQueryWrapper<com.seeex.tech.web.rbac.entity.RoleDO>().in(com.seeex.tech.web.rbac.entity.RoleDO::getId,raw.keySet()).eq(com.seeex.tech.web.rbac.entity.RoleDO::getStatus,"ACTIVE").isNull(com.seeex.tech.web.rbac.entity.RoleDO::getDeletedAt)).forEach(role->{var names=new HashSet<String>(); raw.get(role.getId()).forEach(source->names.add(source+" / "+role.getName())); result.put(role.getId(),names);});
        return result;
    }

    /** 加载涉及资源的 id → permission_code 映射。 */
    private Map<Long, String> loadResourceCodes(Set<Long> roleIds, long employeeId) {
        Set<Long> resourceIds = new HashSet<>();
        if (!roleIds.isEmpty()) {
            roleResourceMapper.selectList(new LambdaQueryWrapper<RoleResourceDO>()
                    .in(RoleResourceDO::getRoleId, roleIds).isNull(RoleResourceDO::getDeletedAt))
                    .forEach(rr -> resourceIds.add(rr.getResourceId()));
        }
        subjectGrantMapper.selectList(new LambdaQueryWrapper<SubjectResourceGrantDO>()
                .eq(SubjectResourceGrantDO::getSubjectType, "EMPLOYEE")
                .eq(SubjectResourceGrantDO::getSubjectId, employeeId))
                .forEach(g -> resourceIds.add(g.getResourceId()));
        if (resourceIds.isEmpty()) {
            return Map.of();
        }
        Set<String> activeApplications = applicationMapper.selectList(com.baomidou.mybatisplus.core.toolkit.Wrappers.<com.seeex.tech.web.registry.entity.ApplicationDO>lambdaQuery().eq(com.seeex.tech.web.registry.entity.ApplicationDO::getStatus,"ACTIVE")).stream().map(com.seeex.tech.web.registry.entity.ApplicationDO::getAppCode).collect(Collectors.toSet());
        return resourceMapper.selectList(new LambdaQueryWrapper<ResourceDO>()
                .in(ResourceDO::getId, resourceIds).eq(ResourceDO::getStatus, "ACTIVE").isNull(ResourceDO::getDeletedAt))
                .stream().filter(row -> row.getApplicationCode() == null || "platform".equals(row.getApplicationCode()) || activeApplications.contains(row.getApplicationCode())).collect(Collectors.toMap(ResourceDO::getId, ResourceDO::getPermissionCode, (a, b) -> a));
    }

    public record PermissionSnapshot(long employeeId, Set<String> allowCodes, Set<String> denyCodes,
                                     Set<String> effectiveCodes, Map<String, Set<String>> sources) {
        public PermissionSnapshot(long employeeId, Set<String> allowCodes, Set<String> denyCodes, Set<String> effectiveCodes) {
            this(employeeId, allowCodes, denyCodes, effectiveCodes, Map.of());
        }
    }
}
