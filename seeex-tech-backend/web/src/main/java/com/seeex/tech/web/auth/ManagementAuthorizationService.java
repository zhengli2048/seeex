package com.seeex.tech.web.auth;

import com.seeex.tech.common.api.ErrorCode;
import com.seeex.tech.common.exception.BizException;
import com.seeex.tech.web.rbac.PermissionService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

/** 管理接口的服务端权限校验。 */
@Service
public class ManagementAuthorizationService {
    private final PermissionService permissionService;
    @org.springframework.beans.factory.annotation.Autowired private com.seeex.tech.web.organization.mapper.EmployeeDepartmentMapper memberships;
    @org.springframework.beans.factory.annotation.Autowired
    private com.seeex.tech.web.governance.ApplicationAdminMapper applicationAdmins;

    public ManagementAuthorizationService(PermissionService permissionService) {
        this.permissionService = permissionService;
    }

    /** 读取显式应用管理员关系，平台权限不转换成应用管理员身份。 */
    public boolean isApplicationAdmin(Authentication auth,String app) {
        if (auth == null || "platform".equals(app)) return false;
        try { return applicationAdmins.selectCount(com.baomidou.mybatisplus.core.toolkit.Wrappers.<com.seeex.tech.web.governance.ApplicationAdminDO>lambdaQuery().eq(com.seeex.tech.web.governance.ApplicationAdminDO::getApplicationCode,app).eq(com.seeex.tech.web.governance.ApplicationAdminDO::getEmployeeId,Long.parseLong(auth.getName()))) > 0; }
        catch (NumberFormatException ex) { return false; }
    }
    /** 应用管理员仅能操作自己应用；平台权限仍按原模型判断。 */
    public boolean canApplication(Authentication auth, String app, String permission) {
        if (auth == null || !auth.isAuthenticated()) { return false; }
        try {
            long id = Long.parseLong(auth.getName());
            return permissionService.hasPermission(id, permission) || !"platform".equals(app) && applicationAdmins.selectCount(com.baomidou.mybatisplus.core.toolkit.Wrappers.<com.seeex.tech.web.governance.ApplicationAdminDO>lambdaQuery().eq(com.seeex.tech.web.governance.ApplicationAdminDO::getApplicationCode,app).eq(com.seeex.tech.web.governance.ApplicationAdminDO::getEmployeeId,id)) > 0;
        } catch (NumberFormatException ex) { return false; }
    }
    /** 校验应用边界，禁止以应用管理关系提升为平台管理员。 */
    public void requireApplication(Authentication auth, String app, String permission) {
        if (!canApplication(auth, app, permission)) { throw new BizException(ErrorCode.FORBIDDEN, "无权管理该应用"); }
    }

    /** 单条与批量操作使用同一行范围，不能通过详情或直接提交ID绕过列表。 */
    public void requireEmployeeScope(Authentication auth,long target) {
        var scope = permissionService.resolveDataScope(Long.parseLong(auth.getName()),"employees:view");
        boolean allowed = scope.type() == com.seeex.tech.web.rbac.DataScopeService.ScopeType.ALL || scope.employeeIds().contains(target);
        if (!allowed && !scope.departmentIds().isEmpty()) allowed = memberships.selectCount(com.baomidou.mybatisplus.core.toolkit.Wrappers.<com.seeex.tech.web.organization.entity.EmployeeDepartmentDO>lambdaQuery().eq(com.seeex.tech.web.organization.entity.EmployeeDepartmentDO::getEmployeeId,target).in(com.seeex.tech.web.organization.entity.EmployeeDepartmentDO::getDepartmentId,scope.departmentIds()).isNull(com.seeex.tech.web.organization.entity.EmployeeDepartmentDO::getDeletedAt)) > 0;
        if (!allowed) throw new BizException(ErrorCode.FORBIDDEN,"员工不在当前数据范围内");
    }
    /** 部门详情及写操作的行范围校验。 */
    public void requireDepartmentScope(Authentication auth,long target) {
        var scope = permissionService.resolveDataScope(Long.parseLong(auth.getName()),"departments:view");
        boolean allowed = scope.type() == com.seeex.tech.web.rbac.DataScopeService.ScopeType.ALL || scope.departmentIds().contains(target);
        if (!allowed && !scope.employeeIds().isEmpty()) allowed = memberships.selectCount(com.baomidou.mybatisplus.core.toolkit.Wrappers.<com.seeex.tech.web.organization.entity.EmployeeDepartmentDO>lambdaQuery().in(com.seeex.tech.web.organization.entity.EmployeeDepartmentDO::getEmployeeId,scope.employeeIds()).eq(com.seeex.tech.web.organization.entity.EmployeeDepartmentDO::getDepartmentId,target).isNull(com.seeex.tech.web.organization.entity.EmployeeDepartmentDO::getDeletedAt)) > 0;
        if (!allowed) throw new BizException(ErrorCode.FORBIDDEN,"部门不在当前数据范围内");
    }

    /** 校验当前请求是否拥有指定权限。 */
    public void require(Authentication authentication, String permission) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new BizException(ErrorCode.UNAUTHORIZED, ErrorCode.UNAUTHORIZED.defaultMessage());
        }
        String subject = authentication.getName();
        try {
            long employeeId = Long.parseLong(subject);
            if (!permissionService.hasPermission(employeeId, permission)) {
                throw new BizException(ErrorCode.FORBIDDEN, ErrorCode.FORBIDDEN.defaultMessage());
            }
        } catch (NumberFormatException ex) {
            throw new BizException(ErrorCode.FORBIDDEN, ErrorCode.FORBIDDEN.defaultMessage());
        }
    }
}
