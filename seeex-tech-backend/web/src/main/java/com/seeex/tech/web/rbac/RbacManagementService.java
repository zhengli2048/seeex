package com.seeex.tech.web.rbac;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.seeex.tech.common.api.ErrorCode;
import com.seeex.tech.common.exception.BizException;
import com.seeex.tech.web.organization.SequenceGenerator;
import com.seeex.tech.web.rbac.entity.ResourceDO;
import com.seeex.tech.web.rbac.entity.RoleDO;
import com.seeex.tech.web.rbac.entity.RoleResourceDO;
import com.seeex.tech.web.rbac.entity.SubjectResourceGrantDO;
import com.seeex.tech.web.rbac.entity.SubjectRoleAssignmentDO;
import com.seeex.tech.web.rbac.mapper.ResourceMapper;
import com.seeex.tech.web.rbac.mapper.RoleMapper;
import com.seeex.tech.web.rbac.mapper.RoleResourceMapper;
import com.seeex.tech.web.rbac.mapper.SubjectResourceGrantMapper;
import com.seeex.tech.web.rbac.mapper.SubjectRoleAssignmentMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** 角色、资源和授权关系管理服务。 */
@Service
@RequiredArgsConstructor
public class RbacManagementService {

    @org.springframework.beans.factory.annotation.Autowired private com.seeex.tech.web.auth.ManagementAuthorizationService authorization;
    private static final String ACTIVE = "ACTIVE";
    private static final String MANUAL = "MANUAL";

    /** 角色业务编号前缀。 */
    private static final String PREFIX_ROLE = "ROLE";

    /** 资源业务编号前缀。 */
    private static final String PREFIX_RESOURCE = "RES";

    @org.springframework.beans.factory.annotation.Autowired private com.seeex.tech.web.organization.mapper.EmployeeDepartmentMapper memberships;
    private final RoleMapper roleMapper;
    private final com.seeex.tech.web.organization.mapper.EmployeeMapper employeeMapper;
    private final com.seeex.tech.web.organization.mapper.DepartmentMapper departmentMapper;
    private final com.seeex.tech.web.organization.mapper.JobGradeMapper jobGradeMapper;
    private final ResourceMapper resourceMapper;
    private final RoleResourceMapper roleResourceMapper;
    private final SubjectResourceGrantMapper subjectGrantMapper;
    private final SubjectRoleAssignmentMapper subjectRoleAssignmentMapper;
    private final SequenceGenerator sequenceGenerator;
    private final com.seeex.tech.web.registry.mapper.ApplicationMapper applicationMapper;

    /** 查询角色分页。 */
    public PageResult<RoleItem> roles(int page, int pageSize, String keyword, String status) {
        LambdaQueryWrapper<RoleDO> query = new LambdaQueryWrapper<RoleDO>().isNull(RoleDO::getDeletedAt)
                .eq(status != null && !status.isBlank(), RoleDO::getStatus, status)
                .and(keyword != null && !keyword.isBlank(), wrapper -> wrapper.like(RoleDO::getCode, keyword)
                        .or().like(RoleDO::getName, keyword)).orderByDesc(RoleDO::getId);
        Page<RoleDO> result = roleMapper.selectPage(new Page<>(page, pageSize), query);
        return new PageResult<>(roleItems(result.getRecords()), result.getTotal());
    }

    /** 查询角色详情。 */
    public RoleItem role(long id) { return roleItem(require(roleMapper.selectById(id), "role")); }

    /** 查询资源分页。 */
    public PageResult<ResourceItem> resources(int page, int pageSize, String keyword, String status, String applicationCode) {
        LambdaQueryWrapper<ResourceDO> query = new LambdaQueryWrapper<ResourceDO>().isNull(ResourceDO::getDeletedAt)
                .eq(status != null && !status.isBlank(), ResourceDO::getStatus, status)
                .eq(applicationCode != null && !applicationCode.isBlank(), ResourceDO::getApplicationCode, applicationCode)
                .and(keyword != null && !keyword.isBlank(), wrapper -> wrapper.like(ResourceDO::getCode, keyword)
                        .or().like(ResourceDO::getName, keyword).or().like(ResourceDO::getPermissionCode, keyword))
                .orderByDesc(ResourceDO::getId);
        Page<ResourceDO> result = resourceMapper.selectPage(new Page<>(page, pageSize), query);
        return new PageResult<>(result.getRecords().stream().map(RbacManagementService::resourceItem).toList(), result.getTotal());
    }

    /** 查询资源详情。 */
    public ResourceItem resource(long id) { return resourceItem(require(resourceMapper.selectById(id), "resource")); }

    /** 创建角色。 */
    @Transactional
    public RoleItem createRole(RoleCommand command) {
        String code = command.code();
        if (code == null || code.isBlank()) {
            code = sequenceGenerator.next(PREFIX_ROLE, sequenceGenerator.maxSeq(PREFIX_ROLE,
                    roleMapper.selectList(new LambdaQueryWrapper<RoleDO>().select(RoleDO::getCode)).stream().map(RoleDO::getCode).toList()));
        }
        requireText(code, "code");
        requireText(command.name(), "name");
        ensureUnique(roleMapper.selectCount(new LambdaQueryWrapper<RoleDO>().eq(RoleDO::getCode, code)
                .isNull(RoleDO::getDeletedAt)), "code");
        RoleDO entity = new RoleDO();
        entity.setCode(code);
        entity.setName(command.name());
        entity.setStatus(normalizeStatus(command.status()));
        roleMapper.insert(entity);
        return roleItem(entity);
    }

    /** 更新角色。 */
    @Transactional
    public RoleItem updateRole(long id, RoleCommand command) {
        RoleDO entity = require(roleMapper.selectById(id), "role");
        var actor=org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if(actor!=null && command.status()!=null && !command.status().equals(entity.getStatus())) authorization.require(actor,"rbac:roles:grant");
        if (command.name() != null) {
            requireText(command.name(), "name");
            entity.setName(command.name());
        }
        if (command.status() != null) {
            entity.setStatus(normalizeStatus(command.status()));
        }
        roleMapper.updateById(entity);
        return roleItem(entity);
    }

    /** 软删除角色并清理授权关系。 */
    @Transactional
    public void deleteRoles(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new BizException(ErrorCode.VALIDATION_FAILED, "ids must not be empty");
        }
        ids.forEach(id -> {
            RoleDO role = require(roleMapper.selectById(id), "role");
            if (subjectRoleAssignmentMapper.selectCount(new LambdaQueryWrapper<SubjectRoleAssignmentDO>().eq(SubjectRoleAssignmentDO::getRoleId, id).isNull(SubjectRoleAssignmentDO::getDeletedAt)) > 0
                    || jobGradeMapper.selectCount(new LambdaQueryWrapper<com.seeex.tech.web.organization.entity.JobGradeDO>().eq(com.seeex.tech.web.organization.entity.JobGradeDO::getDefaultRoleId, id).isNull(com.seeex.tech.web.organization.entity.JobGradeDO::getDeletedAt)) > 0) { throw new BizException(ErrorCode.CONFLICT, "角色仍被人员、部门或职级使用"); }
            role.setDeletedAt(LocalDateTime.now());
            roleMapper.updateById(role);
            roleResourceMapper.delete(new LambdaQueryWrapper<RoleResourceDO>().eq(RoleResourceDO::getRoleId, id));
        });
    }

    /** 创建资源。 */
    @Transactional
    public ResourceItem createResource(ResourceCommand command) {
        String code = command.code();
        if (code == null || code.isBlank()) {
            code = sequenceGenerator.next(PREFIX_RESOURCE, sequenceGenerator.maxSeq(PREFIX_RESOURCE,
                    resourceMapper.selectList(new LambdaQueryWrapper<ResourceDO>().select(ResourceDO::getCode)).stream().map(ResourceDO::getCode).toList()));
        }
        String permissionCode = command.permissionCode() == null || command.permissionCode().isBlank() ? code : command.permissionCode();
        requireText(code, "code");
        requireText(permissionCode, "permissionCode");
        requireText(command.name(), "name");
        ensureUnique(resourceMapper.selectCount(new LambdaQueryWrapper<ResourceDO>().eq(ResourceDO::getCode, code)
                .or().eq(ResourceDO::getPermissionCode, permissionCode)), "code or permissionCode");
        ResourceDO entity = new ResourceDO();
        entity.setCode(code);
        entity.setPermissionCode(permissionCode);
        entity.setName(command.name());
        if (!java.util.Set.of("APPLICATION", "MODULE", "PAGE", "MENU", "ACTION", "API", "DATA_SCOPE").contains(command.resourceType() == null ? "" : command.resourceType())) { throw new BizException(ErrorCode.VALIDATION_FAILED, "资源类型不合法"); }
        validateRoutePath(command.routePath(), command.resourceType());
        entity.setResourceType(command.resourceType());
        entity.setStatus(normalizeStatus(command.status()));
        String entry = normalizeEntry(command.entryType(), command.applicationCode());
        String category = normalizeCategory(command.menuCategory());
        validateResourceParent(null, command.applicationCode(), command.parentId(), entry, category);
        entity.setMenuCategory(category);
        entity.setEntryType(entry);
        entity.setApplicationCode(command.applicationCode());
        entity.setParentId(command.parentId());
        entity.setRoutePath(command.routePath());
        if (command.icon() != null && command.icon().length() > 128) { throw new BizException(ErrorCode.VALIDATION_FAILED, "图标标识最多128字"); }
        entity.setIcon(command.icon());
        entity.setSortOrder(command.sortOrder() == null ? 0 : command.sortOrder());
        entity.setVisible(command.visible() == null || command.visible());
        entity.setPublicResource(command.publicResource() != null && command.publicResource());
        resourceMapper.insert(entity);
        return resourceItem(entity);
    }

    /** 更新资源。 */
    @Transactional
    public ResourceItem updateResource(long id, ResourceCommand command) {
        ResourceDO entity = require(resourceMapper.selectById(id), "resource");
        if (command.name() != null) {
            requireText(command.name(), "name");
            entity.setName(command.name());
        }
        if (command.resourceType() != null) {
            if (!List.of("APPLICATION", "MODULE", "PAGE", "MENU", "ACTION", "API", "DATA_SCOPE").contains(command.resourceType())) { throw new BizException(ErrorCode.VALIDATION_FAILED, "资源类型不合法"); }
            entity.setResourceType(command.resourceType());
        }
        validateRoutePath(command.routePath(), entity.getResourceType());
        if (command.status() != null) {
            entity.setStatus(normalizeStatus(command.status()));
        }
        if (command.applicationCode() != null && !command.applicationCode().equals(entity.getApplicationCode())) { throw new BizException(ErrorCode.VALIDATION_FAILED, "资源不可跨应用移动"); }
        String entry = command.entryType() == null ? normalizeEntry(entity.getEntryType(), entity.getApplicationCode()) : normalizeEntry(command.entryType(), entity.getApplicationCode());
        if (!entry.equals(normalizeEntry(entity.getEntryType(), entity.getApplicationCode()))) { throw new BizException(ErrorCode.VALIDATION_FAILED, "已有资源不可改写所属端"); }
        entity.setEntryType(entry);
        String category = command.menuCategory() == null ? normalizeCategory(entity.getMenuCategory()) : normalizeCategory(command.menuCategory());
        if (!category.equals(normalizeCategory(entity.getMenuCategory())) && resourceMapper.selectCount(new LambdaQueryWrapper<ResourceDO>().eq(ResourceDO::getParentId, id).isNull(ResourceDO::getDeletedAt)) > 0) { throw new BizException(ErrorCode.CONFLICT, "有子资源时请先调整子资源分类"); }
        validateResourceParent(id, entity.getApplicationCode(), command.parentId(), entry, category);
        entity.setMenuCategory(category);
        entity.setParentId(command.parentId());
        entity.setRoutePath(command.routePath());
        if (command.icon() != null && command.icon().length() > 128) { throw new BizException(ErrorCode.VALIDATION_FAILED, "图标标识最多128字"); }
        entity.setIcon(command.icon());
        if (command.sortOrder() != null) {
            entity.setSortOrder(command.sortOrder());
        }
        if (command.visible() != null) {
            entity.setVisible(command.visible());
        }
        if (command.publicResource() != null) {
            entity.setPublicResource(command.publicResource());
        }
        resourceMapper.updateById(entity);
        return resourceItem(entity);
    }

    /** 软删除资源。 */
    @Transactional
    public void deleteResources(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new BizException(ErrorCode.VALIDATION_FAILED, "ids must not be empty");
        }
        ids.forEach(id -> {
            ResourceDO resource = require(resourceMapper.selectById(id), "resource");
            if (resourceMapper.selectCount(new LambdaQueryWrapper<ResourceDO>().eq(ResourceDO::getParentId, id).isNull(ResourceDO::getDeletedAt)) > 0
                    || roleResourceMapper.selectCount(new LambdaQueryWrapper<RoleResourceDO>().eq(RoleResourceDO::getResourceId, id).isNull(RoleResourceDO::getDeletedAt)) > 0
                    || subjectGrantMapper.selectCount(new LambdaQueryWrapper<SubjectResourceGrantDO>().eq(SubjectResourceGrantDO::getResourceId, id)) > 0) { throw new BizException(ErrorCode.CONFLICT, "资源仍有子节点或授权关联"); }
            resource.setDeletedAt(LocalDateTime.now());
            resourceMapper.updateById(resource);
            roleResourceMapper.delete(new LambdaQueryWrapper<RoleResourceDO>().eq(RoleResourceDO::getResourceId, id));
        });
    }

    /** 替换角色的全部资源授权（按应用范围）。 */
    @Transactional
    public List<GrantItem> replaceGrants(long roleId, List<GrantCommand> grants, String applicationCode) {
        require(roleMapper.selectById(roleId), "role");
        validateGrants(grants);
        validateGrantResources(grants, applicationCode, grants(roleId));
        if (applicationCode == null || applicationCode.isBlank()) {
            roleResourceMapper.delete(new LambdaQueryWrapper<RoleResourceDO>().eq(RoleResourceDO::getRoleId, roleId));
        } else {
            resourceMapper.selectList(new LambdaQueryWrapper<ResourceDO>().eq(ResourceDO::getApplicationCode, applicationCode)).forEach(resource ->
                    roleResourceMapper.delete(new LambdaQueryWrapper<RoleResourceDO>().eq(RoleResourceDO::getRoleId, roleId)
                            .eq(RoleResourceDO::getResourceId, resource.getId())));
        }
        if (grants == null) {
            return List.of();
        }
        return grants.stream().map(grant -> {
            ResourceDO resource = require(resourceMapper.selectById(grant.resourceId()), "resource");
            if (resource.getDeletedAt() != null || (applicationCode != null && !applicationCode.isBlank() && !applicationCode.equals(resource.getApplicationCode()))) { throw new BizException(ErrorCode.VALIDATION_FAILED, "资源不属于当前应用或已停用"); }
            RoleResourceDO entity = new RoleResourceDO();
            entity.setRoleId(roleId);
            entity.setResourceId(resource.getId());
            entity.setEffect(normalizeEffect(grant.effect()));
            entity.setAssignmentSource(MANUAL);
            roleResourceMapper.insert(entity);
            return new GrantItem(resource.getId(), resource.getPermissionCode(), entity.getEffect());
        }).toList();
    }

    /** 查询角色当前授权。 */
    public List<GrantItem> grants(long roleId) {
        require(roleMapper.selectById(roleId), "role");
        return roleResourceMapper.selectList(new LambdaQueryWrapper<RoleResourceDO>().eq(RoleResourceDO::getRoleId, roleId)
                .isNull(RoleResourceDO::getDeletedAt)).stream().map(grant -> {
                    ResourceDO resource = require(resourceMapper.selectById(grant.getResourceId()), "resource");
                    return new GrantItem(resource.getId(), resource.getPermissionCode(), grant.getEffect());
                }).toList();
    }

    /** 查询角色授权差异预览（当前 vs 提交前）。 */
    public DiffResult grantsDiff(long roleId, List<GrantCommand> incoming, String applicationCode) {
        Set<Long> current = grants(roleId).stream().filter(grant -> applicationCode == null || applicationCode.isBlank() || applicationCode.equals(require(resourceMapper.selectById(grant.resourceId()), "资源").getApplicationCode())).map(GrantItem::resourceId).collect(Collectors.toSet());
        Set<Long> incomingIds = incoming == null ? Set.of() : incoming.stream().map(GrantCommand::resourceId).collect(Collectors.toSet());
        Set<Long> added = incomingIds.stream().filter(id -> !current.contains(id)).collect(Collectors.toSet());
        Set<Long> removed = current.stream().filter(id -> !incomingIds.contains(id)).collect(Collectors.toSet());
        return new DiffResult(added, removed);
    }

    /** 替换员工直接授权。 */
    @Transactional
    public List<GrantItem> replaceEmployeeGrants(long employeeId, List<GrantCommand> grants, String applicationCode) {
        require(employeeMapper.selectById(employeeId), "employee");
        validateGrants(grants);
        validateGrantResources(grants, applicationCode, employeeGrants(employeeId));
        if (applicationCode == null || applicationCode.isBlank()) {
            subjectGrantMapper.delete(new LambdaQueryWrapper<SubjectResourceGrantDO>().eq(SubjectResourceGrantDO::getSubjectType, "EMPLOYEE")
                    .eq(SubjectResourceGrantDO::getSubjectId, employeeId));
        } else {
            resourceMapper.selectList(new LambdaQueryWrapper<ResourceDO>().eq(ResourceDO::getApplicationCode, applicationCode)).forEach(resource ->
                    subjectGrantMapper.delete(new LambdaQueryWrapper<SubjectResourceGrantDO>().eq(SubjectResourceGrantDO::getSubjectType, "EMPLOYEE")
                            .eq(SubjectResourceGrantDO::getSubjectId, employeeId).eq(SubjectResourceGrantDO::getResourceId, resource.getId())));
        }
        if (grants == null) {
            return List.of();
        }
        return grants.stream().map(grant -> {
            ResourceDO resource = require(resourceMapper.selectById(grant.resourceId()), "resource");
            if (resource.getDeletedAt() != null || (applicationCode != null && !applicationCode.isBlank() && !applicationCode.equals(resource.getApplicationCode()))) { throw new BizException(ErrorCode.VALIDATION_FAILED, "资源不属于当前应用或已停用"); }
            SubjectResourceGrantDO entity = new SubjectResourceGrantDO();
            entity.setSubjectType("EMPLOYEE");
            entity.setSubjectId(employeeId);
            entity.setResourceId(resource.getId());
            entity.setEffect(normalizeEffect(grant.effect()));
            subjectGrantMapper.insert(entity);
            return new GrantItem(resource.getId(), resource.getPermissionCode(), entity.getEffect());
        }).toList();
    }

    /** 查询员工当前直接授权。 */
    public List<GrantItem> employeeGrants(long employeeId) {
        return subjectGrantMapper.selectList(new LambdaQueryWrapper<SubjectResourceGrantDO>()
                .eq(SubjectResourceGrantDO::getSubjectType, "EMPLOYEE").eq(SubjectResourceGrantDO::getSubjectId, employeeId))
                .stream().map(grant -> {
                    ResourceDO resource = require(resourceMapper.selectById(grant.getResourceId()), "resource");
                    return new GrantItem(resource.getId(), resource.getPermissionCode(), grant.getEffect());
                }).toList();
    }

    /** 替换主体角色绑定。 */
    @Transactional
    public List<RoleAssignmentItem> replaceRoleAssignments(String subjectType, long subjectId, List<Long> roleIds) {
        String normalized = normalizeSubjectType(subjectType);
        if ("EMPLOYEE".equals(normalized)) { require(employeeMapper.selectById(subjectId), "员工"); } else { require(departmentMapper.selectById(subjectId), "部门"); }
        subjectRoleAssignmentMapper.delete(new LambdaQueryWrapper<SubjectRoleAssignmentDO>()
                .eq(SubjectRoleAssignmentDO::getSubjectType, normalized).eq(SubjectRoleAssignmentDO::getSubjectId, subjectId)
                .and(w -> w.eq(SubjectRoleAssignmentDO::getAssignmentSource, MANUAL).or().isNull(SubjectRoleAssignmentDO::getAssignmentSource)));
        if (roleIds == null) {
            return List.of();
        }
        return roleIds.stream().map(roleId -> {
            RoleDO role = require(roleMapper.selectById(roleId), "role");
            SubjectRoleAssignmentDO assignment = new SubjectRoleAssignmentDO();
            assignment.setSubjectType(normalized);
            assignment.setSubjectId(subjectId);
            assignment.setRoleId(role.getId());
            assignment.setAssignmentSource(MANUAL);
            subjectRoleAssignmentMapper.insert(assignment);
            return new RoleAssignmentItem(role.getId(), role.getCode(), role.getName(), normalized, subjectId);
        }).toList();
    }

    /** 查询主体角色绑定。 */
    public List<RoleAssignmentItem> roleAssignments(String subjectType, long subjectId) {
        String normalized = normalizeSubjectType(subjectType);
        return subjectRoleAssignmentMapper.selectList(new LambdaQueryWrapper<SubjectRoleAssignmentDO>()
                .eq(SubjectRoleAssignmentDO::getSubjectType, normalized).eq(SubjectRoleAssignmentDO::getSubjectId, subjectId)
                .isNull(SubjectRoleAssignmentDO::getDeletedAt)).stream().map(assignment -> {
                    RoleDO role = require(roleMapper.selectById(assignment.getRoleId()), "role");
                    return new RoleAssignmentItem(role.getId(), role.getCode(), role.getName(), normalized, subjectId);
                }).toList();
    }

    /** 反查角色绑定的员工、部门和默认职级。 */
    public List<UsageItem> roleUsage(long id) {
        require(roleMapper.selectById(id), "角色");
        List<UsageItem> rows = new java.util.ArrayList<>();
        subjectRoleAssignmentMapper.selectList(new LambdaQueryWrapper<SubjectRoleAssignmentDO>().eq(SubjectRoleAssignmentDO::getRoleId, id)
                .isNull(SubjectRoleAssignmentDO::getDeletedAt)).forEach(row -> {
                    String name = "已删除主体";
                    if ("EMPLOYEE".equals(row.getSubjectType())) { var employee = employeeMapper.selectById(row.getSubjectId()); if (employee != null) { name = employee.getDisplayName(); } }
                    if ("DEPARTMENT".equals(row.getSubjectType())) { var department = departmentMapper.selectById(row.getSubjectId()); if (department != null) { name = department.getName(); } }
                    rows.add(new UsageItem(row.getSubjectType(), row.getSubjectId(), name, "", row.getAssignmentSource() == null ? MANUAL : row.getAssignmentSource()));
                });
        jobGradeMapper.selectList(new LambdaQueryWrapper<com.seeex.tech.web.organization.entity.JobGradeDO>()
                .eq(com.seeex.tech.web.organization.entity.JobGradeDO::getDefaultRoleId, id).isNull(com.seeex.tech.web.organization.entity.JobGradeDO::getDeletedAt))
                .forEach(row -> rows.add(new UsageItem("JOB_GRADE", row.getId(), row.getName(), "", "DEFAULT_ROLE")));
        return rows;
    }

    /** 反查资源的角色及人员直接授权。 */
    public List<UsageItem> resourceUsage(long id) {
        require(resourceMapper.selectById(id), "资源");
        List<UsageItem> rows = new java.util.ArrayList<>();
        roleResourceMapper.selectList(new LambdaQueryWrapper<RoleResourceDO>().eq(RoleResourceDO::getResourceId, id).isNull(RoleResourceDO::getDeletedAt))
                .forEach(row -> { var role = roleMapper.selectById(row.getRoleId()); if (role != null && role.getDeletedAt() == null) { rows.add(new UsageItem("ROLE", role.getId(), role.getName(), row.getEffect(), row.getAssignmentSource())); } });
        subjectGrantMapper.selectList(new LambdaQueryWrapper<SubjectResourceGrantDO>().eq(SubjectResourceGrantDO::getResourceId, id))
                .forEach(row -> { var employee = employeeMapper.selectById(row.getSubjectId()); if (employee != null && employee.getDeletedAt() == null) { rows.add(new UsageItem("EMPLOYEE", employee.getId(), employee.getDisplayName(), row.getEffect(), MANUAL)); } });
        return rows;
    }

    /** 授权关联展示对象。 */
    public record UsageItem(String subjectType, Long subjectId, String name, String effect, String source) { }

    private void validateGrants(List<GrantCommand> grants) {
        java.util.Set<Long> ids = new java.util.HashSet<>();
        if (grants != null) { for (GrantCommand grant : grants) { if (grant.resourceId() == null || !ids.add(grant.resourceId())) { throw new BizException(ErrorCode.VALIDATION_FAILED, "同一资源只能指定一种授权效果"); } normalizeEffect(grant.effect()); } }
    }

    /** 停用资源仅允许保留原有效果，不允许新增；删除前先验证完整授权集合。 */
    private void validateGrantResources(List<GrantCommand> incoming, String applicationCode, List<GrantItem> existing) {
        if (incoming == null) { return; }
        for (GrantCommand grant : incoming) {
            ResourceDO resource = require(resourceMapper.selectById(grant.resourceId()), "资源");
            boolean preserved = existing.stream().anyMatch(old -> old.resourceId().equals(grant.resourceId()) && old.effect().equalsIgnoreCase(grant.effect()));
            if (applicationCode != null && !applicationCode.isBlank() && !applicationCode.equals(resource.getApplicationCode())
                    || !ACTIVE.equals(resource.getStatus()) && !preserved) { throw new BizException(ErrorCode.VALIDATION_FAILED, "资源不属于当前应用，或不能新增停用资源授权"); }
        }
    }

    private void validateResourceParent(Long id, String appCode, Long parent, String entry, String category) {
        requireText(appCode, "applicationCode");
        if (!"platform".equals(appCode) && applicationMapper.selectCount(new LambdaQueryWrapper<com.seeex.tech.web.registry.entity.ApplicationDO>().eq(com.seeex.tech.web.registry.entity.ApplicationDO::getAppCode, appCode)) == 0) { throw new BizException(ErrorCode.VALIDATION_FAILED, "所属应用不存在"); }
        java.util.Set<Long> visited = new java.util.HashSet<>();
        while (parent != null) {
            if (java.util.Objects.equals(id, parent) || !visited.add(parent)) { throw new BizException(ErrorCode.VALIDATION_FAILED, "父资源不能是自身或后代"); }
            ResourceDO row = require(resourceMapper.selectById(parent), "父资源");
            if (row.getDeletedAt() != null || !appCode.equals(row.getApplicationCode()) || !entry.equals(normalizeEntry(row.getEntryType(), appCode)) || !"platform".equals(appCode) && !category.equals(normalizeCategory(row.getMenuCategory()))) { throw new BizException(ErrorCode.VALIDATION_FAILED, "父资源必须为同应用、同端的有效资源"); }
            parent = row.getParentId();
        }
    }

    private void validateRoutePath(String value, String type) {
        if (value == null || value.isBlank()) {
            if ("MENU".equals(type) || "PAGE".equals(type)) { throw new BizException(ErrorCode.VALIDATION_FAILED, "菜单和页面资源必须配置页面路径"); }
            return;
        }
        if (value.length() > 512 || !value.startsWith("/") || value.startsWith("//") || value.contains("\\") || value.contains("..") || value.contains("\r") || value.contains("\n")) { throw new BizException(ErrorCode.VALIDATION_FAILED, "路由须为安全站内路径"); }
    }

    private static String normalizeCategory(String value) {
        String category = value == null || value.isBlank() ? "常用功能" : value.trim();
        if (category.length() > 64) { throw new BizException(ErrorCode.VALIDATION_FAILED, "菜单分类最多 64 字"); }
        return category;
    }

    private static String normalizeEntry(String value, String appCode) {
        String entry = value == null || value.isBlank() ? ("platform".equals(appCode) ? "ADMIN" : "WEB") : value;
        if (!List.of("WEB", "ADMIN").contains(entry) || "platform".equals(appCode) && !"ADMIN".equals(entry)) { throw new BizException(ErrorCode.VALIDATION_FAILED, "资源所属端不合法"); }
        return entry;
    }

    private static String normalizeSubjectType(String subjectType) {
        if (subjectType == null) {
            throw new BizException(ErrorCode.VALIDATION_FAILED, "subjectType is required");
        }
        String value = subjectType.toUpperCase();
        if (!List.of("EMPLOYEE", "DEPARTMENT").contains(value)) {
            throw new BizException(ErrorCode.VALIDATION_FAILED, "unsupported subjectType");
        }
        return value;
    }

    private static String normalizeEffect(String effect) {
        if (effect == null || !List.of("ALLOW", "DENY").contains(effect.toUpperCase())) {
            throw new BizException(ErrorCode.VALIDATION_FAILED, "effect must be ALLOW or DENY");
        }
        return effect.toUpperCase();
    }

    private RoleItem roleItem(RoleDO row) { return roleItems(List.of(row)).getFirst(); }
    private List<RoleItem> roleItems(List<RoleDO> rows) {
        if(rows.isEmpty())return List.of();
        var ids=rows.stream().map(RoleDO::getId).collect(Collectors.toSet());
        var grants=roleResourceMapper.selectList(new LambdaQueryWrapper<RoleResourceDO>().in(RoleResourceDO::getRoleId,ids).isNull(RoleResourceDO::getDeletedAt));
        java.util.Map<Long,Long> counts=grants.stream().collect(Collectors.groupingBy(RoleResourceDO::getRoleId,Collectors.counting()));
        var assignments=subjectRoleAssignmentMapper.selectList(new LambdaQueryWrapper<SubjectRoleAssignmentDO>().in(SubjectRoleAssignmentDO::getRoleId,ids).isNull(SubjectRoleAssignmentDO::getDeletedAt));
        java.util.Map<Long,java.util.Set<Long>> people=new java.util.HashMap<>(), departmentRoles=new java.util.HashMap<>(), gradeRoles=new java.util.HashMap<>();
        assignments.forEach(row->{ if("EMPLOYEE".equals(row.getSubjectType()))people.computeIfAbsent(row.getRoleId(),ignored->new java.util.HashSet<>()).add(row.getSubjectId()); if("DEPARTMENT".equals(row.getSubjectType()))departmentRoles.computeIfAbsent(row.getSubjectId(),ignored->new java.util.HashSet<>()).add(row.getRoleId()); });
        jobGradeMapper.selectList(new LambdaQueryWrapper<com.seeex.tech.web.organization.entity.JobGradeDO>().in(com.seeex.tech.web.organization.entity.JobGradeDO::getDefaultRoleId,ids).isNull(com.seeex.tech.web.organization.entity.JobGradeDO::getDeletedAt).eq(com.seeex.tech.web.organization.entity.JobGradeDO::getStatus,ACTIVE)).forEach(row->gradeRoles.computeIfAbsent(row.getId(),ignored->new java.util.HashSet<>()).add(row.getDefaultRoleId()));
        if(!departmentRoles.isEmpty() || !gradeRoles.isEmpty()) {
            var members=memberships.selectList(new LambdaQueryWrapper<com.seeex.tech.web.organization.entity.EmployeeDepartmentDO>().isNull(com.seeex.tech.web.organization.entity.EmployeeDepartmentDO::getDeletedAt).and(q->{if(!departmentRoles.isEmpty())q.in(com.seeex.tech.web.organization.entity.EmployeeDepartmentDO::getDepartmentId,departmentRoles.keySet());if(!gradeRoles.isEmpty()){if(!departmentRoles.isEmpty())q.or();q.in(com.seeex.tech.web.organization.entity.EmployeeDepartmentDO::getJobGradeId,gradeRoles.keySet());}}));
            var departmentIds=members.stream().map(com.seeex.tech.web.organization.entity.EmployeeDepartmentDO::getDepartmentId).collect(Collectors.toSet());
            var activeDepartments=new java.util.HashSet<Long>();
            if(!departmentIds.isEmpty())departmentMapper.selectList(new LambdaQueryWrapper<com.seeex.tech.web.organization.entity.DepartmentDO>().in(com.seeex.tech.web.organization.entity.DepartmentDO::getId,departmentIds).isNull(com.seeex.tech.web.organization.entity.DepartmentDO::getDeletedAt).eq(com.seeex.tech.web.organization.entity.DepartmentDO::getStatus,ACTIVE)).forEach(row->activeDepartments.add(row.getId()));
            members.stream().filter(row->activeDepartments.contains(row.getDepartmentId())).forEach(row->{var inherited=new java.util.HashSet<>(departmentRoles.getOrDefault(row.getDepartmentId(),java.util.Set.of()));inherited.addAll(gradeRoles.getOrDefault(row.getJobGradeId(),java.util.Set.of()));inherited.forEach(role->people.computeIfAbsent(role,ignored->new java.util.HashSet<>()).add(row.getEmployeeId()));});
        }
        var employeeIds=people.values().stream().flatMap(java.util.Set::stream).collect(Collectors.toSet());
        var activeEmployees=new java.util.HashSet<Long>();
        if(!employeeIds.isEmpty())employeeMapper.selectList(new LambdaQueryWrapper<com.seeex.tech.web.organization.entity.EmployeeDO>().select(com.seeex.tech.web.organization.entity.EmployeeDO::getId).in(com.seeex.tech.web.organization.entity.EmployeeDO::getId,employeeIds).isNull(com.seeex.tech.web.organization.entity.EmployeeDO::getDeletedAt).eq(com.seeex.tech.web.organization.entity.EmployeeDO::getStatus,ACTIVE)).forEach(row->activeEmployees.add(row.getId()));
        return rows.stream().map(row->new RoleItem(row.getId(),row.getCode(),row.getName(),row.getStatus(),counts.getOrDefault(row.getId(),0L),people.getOrDefault(row.getId(),java.util.Set.of()).stream().filter(activeEmployees::contains).count(),row.getUpdatedAt())).toList();
    }

    private static ResourceItem resourceItem(ResourceDO entity) {
        return new ResourceItem(entity.getId(), entity.getCode(), entity.getPermissionCode(), entity.getName(),
                entity.getResourceType(), entity.getStatus(), entity.getApplicationCode(), entity.getParentId(),
                entity.getRoutePath(), entity.getIcon(), entity.getSortOrder(), entity.getVisible(), entity.getPublicResource(), normalizeEntry(entity.getEntryType(), entity.getApplicationCode()), normalizeCategory(entity.getMenuCategory()));
    }

    private static <T> T require(T entity, String name) {
        boolean deleted = entity instanceof RoleDO role && role.getDeletedAt() != null
                || entity instanceof ResourceDO resource && resource.getDeletedAt() != null
                || entity instanceof com.seeex.tech.web.organization.entity.EmployeeDO employee && employee.getDeletedAt() != null
                || entity instanceof com.seeex.tech.web.organization.entity.DepartmentDO department && department.getDeletedAt() != null;
        if (entity == null || deleted) { throw new BizException(ErrorCode.RESOURCE_NOT_FOUND, name + " not found"); }
        return entity;
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new BizException(ErrorCode.VALIDATION_FAILED, field + " must not be blank");
        }
    }

    private static void ensureUnique(long count, String field) {
        if (count > 0) {
            throw new BizException(ErrorCode.CONFLICT, field + " already exists");
        }
    }

    private static String normalizeStatus(String status) {
        String value = status == null || status.isBlank() ? ACTIVE : status.toUpperCase();
        if (!List.of("ACTIVE", "INACTIVE").contains(value)) { throw new BizException(ErrorCode.VALIDATION_FAILED, "状态不合法"); }
        return value;
    }

    public record PageResult<T>(List<T> items, long total) { }
    public record RoleItem(Long id, String code, String name, String status, long resourceCount, long employeeCount, LocalDateTime updatedAt) { }
    public record ResourceItem(Long id, String code, String permissionCode, String name,
                               String resourceType, String status, String applicationCode, Long parentId,
                               String routePath, String icon, Integer sortOrder, Boolean visible, Boolean publicResource, String entryType, String menuCategory) { }
    public record RoleCommand(String code, String name, String status) { }
    public record ResourceCommand(String code, String permissionCode, String name, String resourceType, String status,
                                  String applicationCode, Long parentId, String routePath, String icon, Integer sortOrder,
                                  Boolean visible, Boolean publicResource, String entryType, String menuCategory) {
        public ResourceCommand(String code, String permissionCode, String name, String resourceType, String status, String applicationCode, Long parentId, String routePath, String icon, Integer sortOrder, Boolean visible, Boolean publicResource) {
            this(code, permissionCode, name, resourceType, status, applicationCode, parentId, routePath, icon, sortOrder, visible, publicResource, null, null);
        }
    }
    public record GrantCommand(Long resourceId, String effect) { }
    public record GrantItem(Long resourceId, String permissionCode, String effect) { }
    public record RoleAssignmentItem(Long roleId, String roleCode, String roleName, String subjectType, Long subjectId) { }
    public record DiffResult(Set<Long> added, Set<Long> removed) { }
}
