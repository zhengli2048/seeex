package com.seeex.tech.web.audit;

import com.seeex.tech.common.api.ApiResponse;
import com.seeex.tech.common.api.ErrorCode;
import com.seeex.tech.common.exception.BizException;
import com.seeex.tech.web.auth.ManagementAuthorizationService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 审计日志查询接口。 */
@RestController
@RequestMapping("/api/audit-logs")
@RequiredArgsConstructor
public class AuditController {

    private final AuditService auditService;
    private final ManagementAuthorizationService authorization;

    /** 分页查询审计日志。 */
    @GetMapping
    public ApiResponse<AuditService.PageResult> list(
            @org.springframework.web.bind.annotation.ModelAttribute AuditService.AuditQuery query, Authentication authentication) {
        authorization.require(authentication, "audit:view");
        return ApiResponse.ok(auditService.list(query));
    }

    /** 受控审计筛选字典。 */
    @GetMapping("/options")
    public ApiResponse<java.util.Map<String,Object>> options(@org.springframework.web.bind.annotation.ModelAttribute AuditService.OptionQuery query,Authentication auth) { authorization.require(auth,"audit:view"); return ApiResponse.ok(auditService.options(query)); }
    /** 独立导出权限，固定字段不包含原始请求和敏感变更JSON。 */
    @GetMapping("/export")
    @com.seeex.tech.web.audit.AuditLog(action="EXPORT_AUDIT",resourceType="AUDIT")
    public org.springframework.http.ResponseEntity<byte[]> export(@org.springframework.web.bind.annotation.ModelAttribute AuditService.AuditQuery query,Authentication auth) {
        authorization.require(auth,"audit:view"); authorization.require(auth,"audit:export");
        return org.springframework.http.ResponseEntity.ok().header("Content-Disposition","attachment; filename=seeex-audit.csv").header("Cache-Control","no-store").contentType(org.springframework.http.MediaType.parseMediaType("text/csv;charset=UTF-8")).body(auditService.export(query));
    }

    /** 查询单条审计日志详情。 */
    @GetMapping("/{id}")
    public ApiResponse<AuditLogDO> detail(@PathVariable long id, Authentication authentication) {
        authorization.require(authentication, "audit:view");
        AuditLogDO detail = auditService.detail(id);
        if (detail == null) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND, "audit log not found");
        }
        return ApiResponse.ok(detail);
    }
}
