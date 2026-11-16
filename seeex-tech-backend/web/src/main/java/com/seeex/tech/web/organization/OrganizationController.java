package com.seeex.tech.web.organization;

import com.seeex.tech.common.api.ApiResponse;
import com.seeex.tech.web.auth.ManagementAuthorizationService;
import jakarta.validation.Valid;
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

/** 员工、部门和职级管理接口。 */
@RestController
@RequestMapping("/api")
public class OrganizationController {
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;
    private final OrganizationManagementService service;
    private final OrganizationGrantGuard grants;
    private final ManagementAuthorizationService authorization;
    private final com.seeex.tech.web.auth.AccountService accounts;

    public OrganizationController(OrganizationManagementService service, ManagementAuthorizationService authorization, com.seeex.tech.web.auth.AccountService accounts, OrganizationGrantGuard grants) {
        this.service = service; this.grants = grants;
        this.authorization = authorization; this.accounts = accounts;
    }

    /** 查询员工。 */
    @GetMapping("/employees")
    public ApiResponse<OrganizationManagementService.PageResult<OrganizationManagementService.EmployeeItem>> employees(
            @org.springframework.web.bind.annotation.ModelAttribute OrganizationManagementService.EmployeeQuery query,
            Authentication authentication) {
        authorization.require(authentication, "employees:view");
        return ApiResponse.ok(service.employees(query));
    }

    /** 创建员工。 */
    @PostMapping("/employees")
    @com.seeex.tech.web.audit.AuditLog(action = "CREATE_EMPLOYEE", resourceType = "ORGANIZATION")
    public ApiResponse<OrganizationManagementService.EmployeeItem> createEmployee(
            @Valid @RequestBody OrganizationManagementService.EmployeeCommand command, Authentication authentication) {
        authorization.require(authentication, "employees:create");
        grants.employee(authentication,null,command); validateEmployeeDestinations(authentication,command); return ApiResponse.ok(service.createEmployee(command));
    }

    /** 查询员工详情。 */
    @GetMapping("/employees/{id}")
    public ApiResponse<OrganizationManagementService.EmployeeItem> employee(@PathVariable long id, Authentication authentication) {
        authorization.require(authentication, "employees:view");
        authorization.requireEmployeeScope(authentication,id);
        return ApiResponse.ok(service.employee(id));
    }

    /** 更新员工。 */
    @PutMapping("/employees/{id}")
    @com.seeex.tech.web.audit.AuditLog(action = "UPDATE_EMPLOYEE", resourceType = "ORGANIZATION")
    public ApiResponse<OrganizationManagementService.EmployeeItem> updateEmployee(
            @PathVariable long id, @RequestBody OrganizationManagementService.EmployeeCommand command,
            Authentication authentication) {
        authorization.require(authentication, "employees:update");
        authorization.requireEmployeeScope(authentication,id);
        grants.employee(authentication,id,command); validateEmployeeDestinations(authentication,command); return ApiResponse.ok(service.updateEmployee(id, command));
    }

    /** 批量删除员工。 */
    @DeleteMapping("/employees")
    @com.seeex.tech.web.audit.AuditLog(action = "DELETE_EMPLOYEES", resourceType = "ORGANIZATION")
    public ApiResponse<Void> deleteEmployees(@RequestBody List<Long> ids, Authentication authentication) {
        authorization.require(authentication, "employees:delete");
        for (Long target : ids) authorization.requireEmployeeScope(authentication,target);
        service.deleteEmployees(ids);
        return ApiResponse.ok(null);
    }

    /** 查询部门。 */
    @GetMapping("/departments")
    public ApiResponse<OrganizationManagementService.PageResult<OrganizationManagementService.DepartmentItem>> departments(
            @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String keyword, @RequestParam(required = false) String status,
            Authentication authentication) {
        authorization.require(authentication, "departments:view");
        return ApiResponse.ok(service.departments(page, normalizePageSize(pageSize), keyword, status));
    }

    /** 创建部门。 */
    @PostMapping("/departments")
    @com.seeex.tech.web.audit.AuditLog(action = "CREATE_DEPARTMENT", resourceType = "ORGANIZATION")
    public ApiResponse<OrganizationManagementService.DepartmentItem> createDepartment(
            @RequestBody OrganizationManagementService.DepartmentCommand command, Authentication authentication) {
        authorization.require(authentication, "departments:create");
        if (command.parentId() != null) authorization.requireDepartmentScope(authentication,command.parentId());
        if (command.leaderEmployeeId() != null) authorization.requireEmployeeScope(authentication,command.leaderEmployeeId());
        return ApiResponse.ok(service.createDepartment(command));
    }

    /** 查询部门详情。 */
    @GetMapping("/departments/{id}")
    public ApiResponse<OrganizationManagementService.DepartmentItem> department(@PathVariable long id, Authentication authentication) {
        authorization.require(authentication, "departments:view");
        authorization.requireDepartmentScope(authentication,id);
        return ApiResponse.ok(service.department(id));
    }

    /** 更新部门。 */
    @PutMapping("/departments/{id}")
    @com.seeex.tech.web.audit.AuditLog(action = "UPDATE_DEPARTMENT", resourceType = "ORGANIZATION")
    public ApiResponse<OrganizationManagementService.DepartmentItem> updateDepartment(
            @PathVariable long id, @RequestBody OrganizationManagementService.DepartmentCommand command,
            Authentication authentication) {
        authorization.require(authentication, "departments:update");
        authorization.requireDepartmentScope(authentication,id);
        if (command.parentId() != null) authorization.requireDepartmentScope(authentication,command.parentId());
        if (command.leaderEmployeeId() != null) authorization.requireEmployeeScope(authentication,command.leaderEmployeeId());
        grants.departmentStatus(authentication,id,command.status());
        return ApiResponse.ok(service.updateDepartment(id, command));
    }

    /** 批量删除部门。 */
    @DeleteMapping("/departments")
    @com.seeex.tech.web.audit.AuditLog(action = "DELETE_DEPARTMENTS", resourceType = "ORGANIZATION")
    public ApiResponse<Void> deleteDepartments(@RequestBody List<Long> ids, Authentication authentication) {
        authorization.require(authentication, "departments:delete");
        for (Long target : ids) { authorization.requireDepartmentScope(authentication,target); grants.departmentStatus(authentication,target,"DELETED"); }
        service.deleteDepartments(ids);
        return ApiResponse.ok(null);
    }

    /** 查询职级。 */
    @GetMapping("/job-grades")
    public ApiResponse<OrganizationManagementService.PageResult<OrganizationManagementService.JobGradeItem>> jobGrades(
            @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String keyword, @RequestParam(required = false) String status,
            Authentication authentication) {
        authorization.require(authentication, "job-grades:view");
        return ApiResponse.ok(service.jobGrades(page, normalizePageSize(pageSize), keyword, status));
    }

    /** 创建职级。 */
    @PostMapping("/job-grades")
    @com.seeex.tech.web.audit.AuditLog(action = "CREATE_JOB_GRADE", resourceType = "ORGANIZATION")
    public ApiResponse<OrganizationManagementService.JobGradeItem> createJobGrade(
            @RequestBody OrganizationManagementService.JobGradeCommand command, Authentication authentication) {
        authorization.require(authentication, "job-grades:create");
        grants.grade(authentication,null,command.defaultRoleId(),command.status()); return ApiResponse.ok(service.createJobGrade(command));
    }

    /** 查询职级详情。 */
    @GetMapping("/job-grades/{id}")
    public ApiResponse<OrganizationManagementService.JobGradeItem> jobGrade(@PathVariable long id, Authentication authentication) {
        authorization.require(authentication, "job-grades:view");
        return ApiResponse.ok(service.jobGrade(id));
    }

    /** 更新职级。 */
    @PutMapping("/job-grades/{id}")
    @com.seeex.tech.web.audit.AuditLog(action = "UPDATE_JOB_GRADE", resourceType = "ORGANIZATION")
    public ApiResponse<OrganizationManagementService.JobGradeItem> updateJobGrade(
            @PathVariable long id, @RequestBody OrganizationManagementService.JobGradeCommand command,
            Authentication authentication) {
        authorization.require(authentication, "job-grades:update");
        grants.grade(authentication,id,command.defaultRoleId(),command.status()); return ApiResponse.ok(service.updateJobGrade(id, command));
    }

    /** 批量删除职级。 */
    @DeleteMapping("/job-grades")
    @com.seeex.tech.web.audit.AuditLog(action = "DELETE_JOB_GRADES", resourceType = "ORGANIZATION")
    public ApiResponse<Void> deleteJobGrades(@RequestBody List<Long> ids, Authentication authentication) {
        authorization.require(authentication, "job-grades:delete");
        for(Long id:ids) grants.grade(authentication,id,null,"DELETED");
        service.deleteJobGrades(ids);
        return ApiResponse.ok(null);
    }

    private void validateEmployeeDestinations(Authentication auth,OrganizationManagementService.EmployeeCommand command) {
        if (command.primaryDepartmentId() != null) authorization.requireDepartmentScope(auth,command.primaryDepartmentId());
        if (command.memberships() != null) for (var membership : command.memberships()) { if (membership != null && membership.departmentId() != null) authorization.requireDepartmentScope(auth,membership.departmentId()); }
    }

    private int normalizePageSize(int pageSize) {
        if (pageSize <= 0) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(pageSize, MAX_PAGE_SIZE);
    }
    /** 查看员工登录账号状态。 */
    @GetMapping("/employees/{id}/account")
    public ApiResponse<com.seeex.tech.web.auth.AccountService.AccountView> account(@PathVariable long id, Authentication authentication) {
        authorization.require(authentication, "employees:accounts"); authorization.requireEmployeeScope(authentication,id); return ApiResponse.ok(accounts.account(id));
    }
    /** 创建或重置员工登录账号。 */
    @PutMapping("/employees/{id}/account")
    @com.seeex.tech.web.audit.AuditLog(action = "UPDATE_ACCOUNT", resourceType = "AUTH")
    public ApiResponse<com.seeex.tech.web.auth.AccountService.AccountView> saveAccount(@PathVariable long id, @RequestBody com.seeex.tech.web.auth.AccountService.AccountCommand command, Authentication authentication) {
        authorization.require(authentication, "employees:accounts"); authorization.requireEmployeeScope(authentication,id); return ApiResponse.ok(accounts.save(id, command));
    }
}
