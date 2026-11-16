package com.seeex.tech.web.api;

import com.seeex.tech.common.api.ApiResponse;
import com.seeex.tech.web.organization.entity.EmployeeDO;
import com.seeex.tech.web.organization.mapper.EmployeeMapper;
import com.seeex.tech.web.rbac.PermissionService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 平台基础接口：当前用户与权限。 */
@RestController
@RequestMapping("/api")
public class PlatformController {
    public record MeResponse(String employeeId, String displayName, String tenantId, List<String> roles, List<String> permissions) {}
    public record PermissionResponse(List<String> allow, List<String> deny, List<String> effective) {}
    private final PermissionService permissionService;
    private final EmployeeMapper employeeMapper;
    public PlatformController(PermissionService permissionService, EmployeeMapper employeeMapper) { this.permissionService = permissionService; this.employeeMapper = employeeMapper; }

    /** 返回当前登录员工信息。 */
    @GetMapping("/me")
    public ApiResponse<MeResponse> me(Authentication authentication) {
        String subject = authentication == null ? null : authentication.getName();
        EmployeeDO employee = subject == null ? null : employeeMapper.selectById(Long.parseLong(subject));
        if (employee == null) {
            return ApiResponse.ok(new MeResponse(subject, "Unknown", "default", List.of(), List.of()));
        }
        PermissionService.PermissionSnapshot snapshot = permissionService.snapshot(employee.getId());
        return ApiResponse.ok(new MeResponse(subject, employee.getDisplayName(), "default", List.of(), snapshot.effectiveCodes().stream().toList()));
    }

    /** 返回当前员工权限快照。 */
    @GetMapping("/permissions")
    public ApiResponse<PermissionResponse> permissions(Authentication authentication) {
        if (authentication == null) {
            return ApiResponse.ok(new PermissionResponse(List.of(), List.of(), List.of()));
        }
        try {
            PermissionService.PermissionSnapshot snapshot = permissionService.snapshot(Long.parseLong(authentication.getName()));
            return ApiResponse.ok(new PermissionResponse(snapshot.allowCodes().stream().toList(), snapshot.denyCodes().stream().toList(),
                    snapshot.effectiveCodes().stream().toList()));
        } catch (NumberFormatException ex) {
            return ApiResponse.ok(new PermissionResponse(List.of(), List.of(), List.of()));
        }
    }
}
