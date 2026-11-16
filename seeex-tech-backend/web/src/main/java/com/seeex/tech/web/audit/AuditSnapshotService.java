package com.seeex.tech.web.audit;

import com.seeex.tech.web.organization.OrganizationManagementService;
import com.seeex.tech.web.rbac.RbacManagementService;
import com.seeex.tech.web.registry.ApplicationRegistryService;
import com.seeex.tech.web.rbac.PermissionService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

/** 根据已授权的管理对象读取变更前快照，不读取凭证或事件原文。 */
@Service
@RequiredArgsConstructor
public class AuditSnapshotService {
    private final OrganizationManagementService organization;
    private final RbacManagementService rbac;
    private final ApplicationRegistryService applications;
    private final PermissionService permissions;
    @org.springframework.beans.factory.annotation.Autowired private com.seeex.tech.web.auth.ManagementAuthorizationService authorization;

    /** 只对持有对应读取权限的操作者捕获业务字段。 */
    public Object capture(HttpServletRequest request) {
        if (request == null || "GET".equals(request.getMethod())) { return null; }
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || "anonymousUser".equals(auth.getName())) { return null; }
        String[] path = request.getRequestURI().replaceFirst("^/api/", "").split("/");
        if (path.length < 2) { return null; }
        long employeeId = Long.parseLong(auth.getName());
        if ("platform".equals(path[0]) && path.length >= 3 && "apps".equals(path[1]) && permissions.hasPermission(employeeId, "platform:apps:view")) {
            return applications.detail(path[2]);
        }
        if ("rbac".equals(path[0]) && path.length >= 3 && permissions.hasPermission(employeeId, "rbac:roles:view")) {
            if ("assignments".equals(path[1]) && path.length == 4) { long target = Long.parseLong(path[3]); if ("EMPLOYEE".equalsIgnoreCase(path[2])) authorization.requireEmployeeScope(auth,target); if ("DEPARTMENT".equalsIgnoreCase(path[2])) authorization.requireDepartmentScope(auth,target); return rbac.roleAssignments(path[2], target); }
            long id = Long.parseLong(path[2]);
            if ("roles".equals(path[1])) { return path.length > 3 && "grants".equals(path[3]) ? rbac.grants(id) : rbac.role(id); }
            if ("resources".equals(path[1]) && permissions.hasPermission(employeeId, "rbac:resources:view")) { return rbac.resource(id); }
            if ("employees".equals(path[1]) && permissions.hasPermission(employeeId, "employees:view")) { authorization.requireEmployeeScope(auth,id); return rbac.employeeGrants(id); }
        }
        if (!path[1].matches("[0-9]+")) { return null; }
        long id = Long.parseLong(path[1]);
        if ("employees".equals(path[0]) && permissions.hasPermission(employeeId, "employees:view")) { authorization.requireEmployeeScope(auth,id); return organization.employee(id); }
        if ("departments".equals(path[0]) && permissions.hasPermission(employeeId, "departments:view")) { authorization.requireDepartmentScope(auth,id); return organization.department(id); }
        if ("job-grades".equals(path[0]) && permissions.hasPermission(employeeId, "job-grades:view")) { return organization.jobGrade(id); }
        return null;
    }
}
