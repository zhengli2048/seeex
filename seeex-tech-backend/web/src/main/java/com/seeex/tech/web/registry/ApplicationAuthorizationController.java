package com.seeex.tech.web.registry;

import com.seeex.tech.common.api.ApiResponse;
import com.seeex.tech.common.api.ErrorCode;
import com.seeex.tech.common.exception.BizException;
import com.seeex.tech.web.rbac.PermissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.time.Instant;
import java.util.List;
import java.util.Set;

/** 可信应用后端使用独立服务凭证查询实时权限，subjectId 必须来自自身已验证会话。 */
@RestController
@RequestMapping("/api/app-auth")
@RequiredArgsConstructor
public class ApplicationAuthorizationController {
    private final ApplicationRegistryService applications;
    private final PermissionService permissions;

    /** 读取本应用、本端权限快照，不交付其他应用的权限。 */
    @PostMapping("/permissions")
    public ApiResponse<AppPermissions> snapshot(@RequestHeader(value = "X-App-Code", required = false) String appCode,
            @RequestHeader(value = "X-App-Secret", required = false) String secret, @RequestBody PermissionQuery query) {
        applications.authenticateClient(appCode, secret);
        long id = subject(query.subjectId()); String entry = entry(query.entry());
        var snapshot = permissions.snapshot(id);
        return ApiResponse.ok(new AppPermissions(appCode, query.subjectId(), entry,
                List.copyOf(applications.scopedPermissions(appCode, entry, snapshot.allowCodes())), List.copyOf(applications.scopedPermissions(appCode, entry, snapshot.denyCodes())),
                List.copyOf(applications.scopedPermissions(appCode, entry, snapshot.effectiveCodes())), Instant.now()));
    }
    /** 敏感 API 独立校验指定权限；无权限返回 allowed=false，错误时调用方必须拒绝。 */
    @PostMapping("/check")
    public ApiResponse<Decision> check(@RequestHeader(value = "X-App-Code", required = false) String appCode,
            @RequestHeader(value = "X-App-Secret", required = false) String secret, @RequestBody CheckQuery query) {
        applications.authenticateClient(appCode, secret);
        long id = subject(query.subjectId()); String entry = entry(query.entry());
        if (query.permissionCode() == null || query.permissionCode().isBlank()) { throw new BizException(ErrorCode.VALIDATION_FAILED, "权限编码必填"); }
        Set<String> effective = applications.scopedPermissions(appCode, entry, permissions.snapshot(id).effectiveCodes());
        return ApiResponse.ok(new Decision(appCode, query.subjectId(), entry, query.permissionCode(), effective.contains(query.permissionCode()), Instant.now()));
    }
    /** 读取实际数据范围；权限不足时拒绝，不能仅靠浏览器行过滤。 */
    @PostMapping("/data-scope")
    public ApiResponse<com.seeex.tech.web.rbac.DataScopeService.DataScope> dataScope(@RequestHeader("X-App-Code") String appCode,@RequestHeader("X-App-Secret") String secret,@RequestBody CheckQuery query) {
        applications.authenticateClient(appCode,secret); long id = subject(query.subjectId());
        if (query.permissionCode() == null || query.permissionCode().isBlank()) throw new BizException(ErrorCode.VALIDATION_FAILED,"权限编码必填");
        if (!applications.scopedPermissions(appCode,entry(query.entry()),permissions.snapshot(id).effectiveCodes()).contains(query.permissionCode())) { throw new BizException(ErrorCode.FORBIDDEN,"无权访问该资源"); }
        return ApiResponse.ok(permissions.resolveDataScope(id,query.permissionCode()));
    }
    /** 用目标应用后端读取的真实记录归属检查单行，拒绝仅靠列表过滤后的ID越权。 */
    @PostMapping("/check-data")
    public ApiResponse<Decision> checkData(@RequestHeader("X-App-Code") String appCode,@RequestHeader("X-App-Secret") String secret,@RequestBody DataQuery query) {
        applications.authenticateClient(appCode,secret); long id = subject(query.subjectId()); String selectedEntry = entry(query.entry());
        if (query.permissionCode() == null || query.permissionCode().isBlank() || query.targetEmployeeId() == null && query.targetDepartmentId() == null || query.targetEmployeeId() != null && query.targetEmployeeId() <= 0 || query.targetDepartmentId() != null && query.targetDepartmentId() <= 0) { throw new BizException(ErrorCode.VALIDATION_FAILED,"权限编码和记录归属必填且须有效"); }
        boolean allowed = applications.scopedPermissions(appCode,selectedEntry,permissions.snapshot(id).effectiveCodes()).contains(query.permissionCode());
        if (allowed) { var scope = permissions.resolveDataScope(id,query.permissionCode()); allowed = scope.type() == com.seeex.tech.web.rbac.DataScopeService.ScopeType.ALL || query.targetEmployeeId() != null && scope.employeeIds().contains(query.targetEmployeeId()) || query.targetDepartmentId() != null && scope.departmentIds().contains(query.targetDepartmentId()); }
        return ApiResponse.ok(new Decision(appCode,query.subjectId(),selectedEntry,query.permissionCode(),allowed,Instant.now()));
    }
    /** 目标归属必须由可信应用后端从记录读取，不信任浏览器传值。 */
    public record DataQuery(String subjectId,String entry,String permissionCode,Long targetEmployeeId,Long targetDepartmentId) { }
    private long subject(String value) {
        try { long id = Long.parseLong(value); if (id <= 0) { throw new NumberFormatException(); } return id; }
        catch (NumberFormatException ex) { throw new BizException(ErrorCode.VALIDATION_FAILED, "用户标识不合法"); }
    }
    private String entry(String value) {
        String entry = value == null ? "WEB" : value;
        if (!Set.of("WEB", "ADMIN").contains(entry)) { throw new BizException(ErrorCode.VALIDATION_FAILED, "端类型须为 WEB/ADMIN"); } return entry;
    }
    /** 应用权限查询参数。 */
    public record PermissionQuery(String subjectId, String entry) { }
    /** 单个动作校验参数。 */
    public record CheckQuery(String subjectId, String entry, String permissionCode) { }
    /** 本应用的权限快照。 */
    public record AppPermissions(String appCode, String subjectId, String entry, List<String> allow, List<String> deny, List<String> effective, Instant checkedAt) { }
    /** 明确的授权决策。 */
    public record Decision(String appCode, String subjectId, String entry, String permissionCode, boolean allowed, Instant checkedAt) { }
}
