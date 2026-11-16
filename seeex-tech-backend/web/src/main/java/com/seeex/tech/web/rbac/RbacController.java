package com.seeex.tech.web.rbac;

import com.seeex.tech.common.api.ApiResponse;
import com.seeex.tech.web.auth.ManagementAuthorizationService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 角色、资源和授权关系管理接口。 */
@RestController
@RequestMapping("/api/rbac")
public class RbacController {
    private final RbacManagementService service;
    private final ManagementAuthorizationService authorization;
    private final PermissionService permissions;
    private final com.seeex.tech.web.registry.ApplicationRegistryService registry;

    public RbacController(RbacManagementService service, ManagementAuthorizationService authorization,
                          PermissionService permissions, com.seeex.tech.web.registry.ApplicationRegistryService registry) {
        this.service = service;
        this.authorization = authorization;
        this.permissions = permissions; this.registry = registry;
    }

    /** 查询角色。 */
    @GetMapping("/roles")
    public ApiResponse<RbacManagementService.PageResult<RbacManagementService.RoleItem>> roles(
            @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String keyword, @RequestParam(required = false) String status,
            Authentication authentication) {
        authorization.require(authentication, "rbac:roles:view");
        return ApiResponse.ok(service.roles(page, Math.min(Math.max(pageSize, 1), 100), keyword, status));
    }

    /** 创建角色。 */
    @PostMapping("/roles")
    @com.seeex.tech.web.audit.AuditLog(action = "CREATE_ROLE", resourceType = "RBAC")
    public ApiResponse<RbacManagementService.RoleItem> createRole(
            @RequestBody RbacManagementService.RoleCommand command, Authentication authentication) {
        authorization.require(authentication, "rbac:roles:create");
        return ApiResponse.ok(service.createRole(command));
    }

    /** 查询角色详情。 */
    @GetMapping("/roles/{id}")
    public ApiResponse<RbacManagementService.RoleItem> role(@PathVariable long id, Authentication authentication) {
        authorization.require(authentication, "rbac:roles:view");
        return ApiResponse.ok(service.role(id));
    }

    /** 更新角色。 */
    @PutMapping("/roles/{id}")
    @com.seeex.tech.web.audit.AuditLog(action = "UPDATE_ROLE", resourceType = "RBAC")
    public ApiResponse<RbacManagementService.RoleItem> updateRole(
            @PathVariable long id, @RequestBody RbacManagementService.RoleCommand command,
            Authentication authentication) {
        authorization.require(authentication, "rbac:roles:update");
        return ApiResponse.ok(service.updateRole(id, command));
    }

    /** 删除角色。 */
    @DeleteMapping("/roles")
    @com.seeex.tech.web.audit.AuditLog(action = "DELETE_ROLES", resourceType = "RBAC")
    public ApiResponse<Void> deleteRoles(@RequestBody List<Long> ids, Authentication authentication) {
        authorization.require(authentication, "rbac:roles:delete");
        service.deleteRoles(ids);
        return ApiResponse.ok(null);
    }

    /** 替换角色授权。 */
    @PutMapping("/roles/{id}/grants")
    @com.seeex.tech.web.audit.AuditLog(action = "REPLACE_GRANTS", resourceType = "RBAC")
    public ApiResponse<List<RbacManagementService.GrantItem>> replaceGrants(
            @PathVariable long id, @RequestBody List<RbacManagementService.GrantCommand> grants,
            @RequestParam(required = false) String applicationCode,
            Authentication authentication) {
        authorization.require(authentication, "rbac:roles:grant");
        return ApiResponse.ok(service.replaceGrants(id, grants, applicationCode));
    }

    /** 查询角色授权。 */
    @GetMapping("/roles/{id}/grants")
    public ApiResponse<List<RbacManagementService.GrantItem>> grants(@PathVariable long id,
                                                                       Authentication authentication) {
        authorization.require(authentication, "rbac:roles:view");
        return ApiResponse.ok(service.grants(id));
    }

    @PutMapping("/employees/{employeeId}/grants")
    @com.seeex.tech.web.audit.AuditLog(action = "REPLACE_EMPLOYEE_GRANTS", resourceType = "RBAC")
    public ApiResponse<List<RbacManagementService.GrantItem>> replaceEmployeeGrants(
            @PathVariable long employeeId, @RequestBody List<RbacManagementService.GrantCommand> grants,
            @RequestParam(required = false) String applicationCode,
            Authentication authentication) {
        authorization.require(authentication, "employees:grant");
        authorization.requireEmployeeScope(authentication,employeeId); return ApiResponse.ok(service.replaceEmployeeGrants(employeeId, grants, applicationCode));
    }

    @GetMapping("/employees/{employeeId}/grants")
    public ApiResponse<List<RbacManagementService.GrantItem>> employeeGrants(@PathVariable long employeeId,
                                                                                Authentication authentication) {
        authorization.require(authentication, "employees:view");
        authorization.requireEmployeeScope(authentication,employeeId); return ApiResponse.ok(service.employeeGrants(employeeId));
    }

    @PutMapping("/assignments/{subjectType}/{subjectId}")
    @com.seeex.tech.web.audit.AuditLog(action = "REPLACE_ASSIGNMENTS", resourceType = "RBAC")
    public ApiResponse<List<RbacManagementService.RoleAssignmentItem>> replaceAssignments(
            @PathVariable String subjectType, @PathVariable long subjectId, @RequestBody List<Long> roleIds,
            Authentication authentication) {
        authorization.require(authentication, "rbac:roles:assign");
        if ("EMPLOYEE".equalsIgnoreCase(subjectType)) authorization.requireEmployeeScope(authentication,subjectId);
        if ("DEPARTMENT".equalsIgnoreCase(subjectType)) authorization.requireDepartmentScope(authentication,subjectId);
        return ApiResponse.ok(service.replaceRoleAssignments(subjectType, subjectId, roleIds));
    }

    @GetMapping("/assignments/{subjectType}/{subjectId}")
    public ApiResponse<List<RbacManagementService.RoleAssignmentItem>> assignments(
            @PathVariable String subjectType, @PathVariable long subjectId, Authentication authentication) {
        authorization.require(authentication, "rbac:roles:view");
        if ("EMPLOYEE".equalsIgnoreCase(subjectType)) authorization.requireEmployeeScope(authentication,subjectId);
        if ("DEPARTMENT".equalsIgnoreCase(subjectType)) authorization.requireDepartmentScope(authentication,subjectId);
        return ApiResponse.ok(service.roleAssignments(subjectType, subjectId));
    }

    @GetMapping("/employees/{employeeId}/permissions")
    public ApiResponse<PermissionService.PermissionSnapshot> employeePermissions(@PathVariable long employeeId,
                                                                                  Authentication authentication) {
        authorization.require(authentication, "employees:view");
        authorization.requireEmployeeScope(authentication,employeeId); return ApiResponse.ok(permissions.snapshot(employeeId));
    }

    /** 查询资源。 */
    @GetMapping("/resources")
    public ApiResponse<RbacManagementService.PageResult<RbacManagementService.ResourceItem>> resources(
            @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String keyword, @RequestParam(required = false) String status,
            @RequestParam(required = false) String applicationCode,
            Authentication authentication) {
        authorization.require(authentication, "rbac:resources:view");
        return ApiResponse.ok(service.resources(page, Math.min(Math.max(pageSize, 1), 100), keyword, status, applicationCode));
    }

    /** 创建资源。 */
    @PostMapping("/resources")
    @com.seeex.tech.web.audit.AuditLog(action = "CREATE_RESOURCE", resourceType = "RBAC")
    public ApiResponse<RbacManagementService.ResourceItem> createResource(
            @RequestBody RbacManagementService.ResourceCommand command, Authentication authentication) {
        authorization.require(authentication, "rbac:resources:create");
        return ApiResponse.ok(service.createResource(command));
    }

    /** 查询资源详情。 */
    @GetMapping("/resources/{id}")
    public ApiResponse<RbacManagementService.ResourceItem> resource(@PathVariable long id, Authentication authentication) {
        authorization.require(authentication, "rbac:resources:view");
        return ApiResponse.ok(service.resource(id));
    }

    /** 更新资源。 */
    @PutMapping("/resources/{id}")
    @com.seeex.tech.web.audit.AuditLog(action = "UPDATE_RESOURCE", resourceType = "RBAC")
    public ApiResponse<RbacManagementService.ResourceItem> updateResource(
            @PathVariable long id, @RequestBody RbacManagementService.ResourceCommand command,
            Authentication authentication) {
        authorization.require(authentication, "rbac:resources:update");
        return ApiResponse.ok(service.updateResource(id, command));
    }

    /** 删除资源。 */
    @DeleteMapping("/resources")
    @com.seeex.tech.web.audit.AuditLog(action = "DELETE_RESOURCES", resourceType = "RBAC")
    public ApiResponse<Void> deleteResources(@RequestBody List<Long> ids, Authentication authentication) {
        authorization.require(authentication, "rbac:resources:delete");
        service.deleteResources(ids);
        return ApiResponse.ok(null);
    }
    /** 角色使用主体反查。 */
    @GetMapping("/roles/{id}/usage")
    public ApiResponse<List<RbacManagementService.UsageItem>> roleUsage(@PathVariable long id, Authentication authentication) {
        authorization.require(authentication, "rbac:roles:view"); return ApiResponse.ok(service.roleUsage(id));
    }

    /** 资源授权关联反查。 */
    @GetMapping("/resources/{id}/usage")
    public ApiResponse<List<RbacManagementService.UsageItem>> resourceUsage(@PathVariable long id, Authentication authentication) {
        authorization.require(authentication, "rbac:resources:view"); return ApiResponse.ok(service.resourceUsage(id));
    }
    /** 授权选择器只读取应用名称，独立于应用管理权限。 */
    @GetMapping("/applications")
    public ApiResponse<List<ApplicationOption>> applications(Authentication authentication) {
        if (authentication == null) { throw new com.seeex.tech.common.exception.BizException(com.seeex.tech.common.api.ErrorCode.UNAUTHORIZED, "请先登录"); }
        long id = Long.parseLong(authentication.getName());
        if (!permissions.hasPermission(id, "rbac:resources:view") && !permissions.hasPermission(id, "rbac:roles:grant") && !permissions.hasPermission(id, "employees:grant")) {
            throw new com.seeex.tech.common.exception.BizException(com.seeex.tech.common.api.ErrorCode.FORBIDDEN, "暂无资源选择权限");
        }
        return ApiResponse.ok(registry.listApps().stream().map(row -> new ApplicationOption(row.appCode(), row.name())).toList());
    }
    /** 应用选择项，不含部署配置与版本。 */
    public record ApplicationOption(String appCode, String name) { }
}
