package com.seeex.tech.web.rbac;
import com.seeex.tech.common.api.ApiResponse;
import com.seeex.tech.web.auth.ManagementAuthorizationService;
import com.seeex.tech.web.audit.AuditLog;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
/** 范围接口只接收字段、鉴权并调用服务；事务位于业务服务。 */
@RestController @RequestMapping("/api/rbac/roles/{id}/data-scopes") @RequiredArgsConstructor
public class RoleDataScopeController {
    private final RoleDataScopeService service;
    private final ManagementAuthorizationService authorization;
    /** 查询范围及版本。 */
    @GetMapping
    public ApiResponse<RoleDataScopeService.ScopeView> list(@PathVariable long id,Authentication auth) { authorization.require(auth,"rbac:roles:view"); return ApiResponse.ok(service.list(id)); }
    /** 保存必须提交读取到的版本。 */
    @PutMapping @AuditLog(action="SAVE_DATA_SCOPE",resourceType="RBAC")
    public ApiResponse<Void> save(@PathVariable long id,@RequestBody RoleDataScopeService.ScopeCommand command,Authentication auth) { authorization.require(auth,"rbac:roles:grant"); service.save(id,command); return ApiResponse.ok(null); }
    /** 删除也需版本，防止旧页面清空新配置。 */
    @DeleteMapping @AuditLog(action="DELETE_DATA_SCOPE",resourceType="RBAC")
    public ApiResponse<Void> delete(@PathVariable long id,@RequestParam String resourceCode,@RequestParam Long expectedVersion,Authentication auth) { authorization.require(auth,"rbac:roles:grant"); service.delete(id,resourceCode,expectedVersion); return ApiResponse.ok(null); }
}
