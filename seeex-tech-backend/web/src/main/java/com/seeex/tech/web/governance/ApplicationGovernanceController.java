package com.seeex.tech.web.governance;
import com.seeex.tech.common.api.ApiResponse;
import com.seeex.tech.web.auth.ManagementAuthorizationService;
import com.seeex.tech.web.audit.AuditLog;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.*;
/** 按应用隔离的管理员、权限策略及选择字典接口。 */
@RestController
@RequestMapping("/api/platform/apps/{app}/governance")
@RequiredArgsConstructor
public class ApplicationGovernanceController {
    private final ApplicationGovernanceService service;
    private final com.seeex.tech.web.rbac.PermissionService permissions;
    private final ManagementAuthorizationService authorization;
    /** 查看治理信息及当前调用者管理能力。 */
    @GetMapping
    public ApiResponse<Map<String,Object>> detail(@PathVariable String app, Authentication auth) {
        authorization.requireApplication(auth,app,"platform:apps:update");
        boolean platform = authorization.canApplication(auth,"platform","platform:apps:update");
        var snapshot=service.adminSnapshot(app);
        return ApiResponse.ok(Map.of("admins",snapshot.items(),"adminVersion",snapshot.version(),"policies",service.policies(app),"options",service.options(app),"canManageAdmins",platform));
    }
    /** 用当前权限计算指定员工的授权和实际数据集合。 */
    @GetMapping("/evaluate")
    public ApiResponse<Map<String,Object>> evaluate(@PathVariable String app,@RequestParam long employeeId,@RequestParam long resourceId,Authentication auth) {
        authorization.requireApplication(auth,app,"platform:apps:update");
        var resource = service.resource(app,resourceId);
        boolean allowed = permissions.snapshot(employeeId).effectiveCodes().contains(resource.getPermissionCode());
        return ApiResponse.ok(Map.of("allowed",allowed,"scope",allowed ? permissions.resolveDataScope(employeeId,resource.getPermissionCode()) : new com.seeex.tech.web.rbac.DataScopeService.DataScope(com.seeex.tech.web.rbac.DataScopeService.ScopeType.SELF,Set.of(),Set.of())));
    }
    /** 搜索应用治理选择项，沿用应用管理权限。 */
    @GetMapping("/options")
    public ApiResponse<Map<String,Object>> options(@PathVariable String app,@ModelAttribute ApplicationGovernanceService.OptionQuery query,Authentication auth) { authorization.requireApplication(auth,app,"platform:apps:update"); return ApiResponse.ok(service.options(app,query)); }
    /** 仅平台管理员可以委任或移除应用管理员。 */
    @PutMapping("/admins") @AuditLog(action="ASSIGN_APP_ADMINS",resourceType="APPLICATION")
    public ApiResponse<Void> admins(@PathVariable String app,@RequestBody ApplicationGovernanceService.AdminCommand command,Authentication auth) { authorization.require(auth,"platform:apps:update"); service.replaceAdmins(app,command); return ApiResponse.ok(null); }
    /** 创建应用权限策略。 */
    @PostMapping("/policies") @AuditLog(action="CREATE_APP_POLICY",resourceType="APPLICATION")
    public ApiResponse<ApplicationPolicyDO> create(@PathVariable String app,@RequestBody ApplicationGovernanceService.PolicyCommand command,Authentication auth) { authorization.requireApplication(auth,app,"platform:apps:update"); return ApiResponse.ok(service.save(app,null,command)); }
    /** 编辑策略和数据范围。 */
    @PutMapping("/policies/{id}") @AuditLog(action="UPDATE_APP_POLICY",resourceType="APPLICATION")
    public ApiResponse<ApplicationPolicyDO> update(@PathVariable String app,@PathVariable long id,@RequestBody ApplicationGovernanceService.PolicyCommand command,Authentication auth) { authorization.requireApplication(auth,app,"platform:apps:update"); return ApiResponse.ok(service.save(app,id,command)); }
    /** 删除策略。 */
    @DeleteMapping("/policies/{id}") @AuditLog(action="DELETE_APP_POLICY",resourceType="APPLICATION")
    public ApiResponse<Void> delete(@PathVariable String app,@PathVariable long id,@RequestParam Long expectedVersion,Authentication auth) { authorization.requireApplication(auth,app,"platform:apps:update"); service.delete(app,id,expectedVersion); return ApiResponse.ok(null); }
}
