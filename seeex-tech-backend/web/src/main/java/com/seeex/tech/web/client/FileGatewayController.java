package com.seeex.tech.web.client;

import com.seeex.tech.web.auth.ManagementAuthorizationService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.core.Authentication;

import java.util.List;
import java.util.Map;

/** 面向浏览器的文件服务网关，服务令牌只保留在 web 服务端。 */
@RestController
@RequestMapping("/api/files")
public class FileGatewayController {
    @org.springframework.beans.factory.annotation.Autowired private com.seeex.tech.web.registry.ApplicationRegistryService applications;
    @org.springframework.beans.factory.annotation.Autowired private com.seeex.tech.web.rbac.PermissionService permissions;
    private final FileServiceClient client;
    private final ManagementAuthorizationService authorization;

    public FileGatewayController(FileServiceClient client, ManagementAuthorizationService authorization) {
        this.client = client;
        this.authorization = authorization;
    }

    /** 查询文件列表。 */
    @GetMapping
    public Map<String, Object> list(@RequestParam(defaultValue = "1") int page,
                                    @RequestParam(name = "page_size", defaultValue = "20") int pageSize,
                                    @RequestParam(required = false) String keyword,
                                    @RequestParam(required = false) String status,
                                    @RequestParam(required = false) String contentType,
                                    @RequestParam(defaultValue = "platform") String applicationCode, Authentication authentication) {
        authorization.require(authentication, "files:view");
        requireApplication(authentication,applicationCode);
        return client.list(page, Math.min(Math.max(pageSize, 1), 100), keyword, status, contentType, authentication.getName(), "default");
    }

    /** 申请上传 URL。 */
    @PostMapping("/upload-url")
    @com.seeex.tech.web.audit.AuditLog(action = "UPLOAD_URL_FILE", resourceType = "FILE")
    public Map<String, Object> uploadUrl(@RequestParam String fileName, @RequestParam String contentType,
                                         @RequestParam long size, @RequestHeader("Idempotency-Key") String idempotencyKey,
                                         @RequestParam(defaultValue = "platform") String applicationCode, Authentication authentication) {
        authorization.require(authentication, "files:create");
        requireApplication(authentication,applicationCode);
        return client.requestUploadUrl(fileName, contentType, size, idempotencyKey, authentication.getName(), "default");
    }

    /** 确认上传。 */
    @PostMapping("/confirm")
    @com.seeex.tech.web.audit.AuditLog(action = "CONFIRM_FILE", resourceType = "FILE")
    public Map<String, Object> confirm(@RequestParam String objectKey,
                                       @RequestParam(defaultValue = "platform") String applicationCode, Authentication authentication) {
        authorization.require(authentication, "files:create");
        requireApplication(authentication,applicationCode);
        return client.confirm(objectKey, authentication.getName(), "default");
    }

    /** 获取下载 URL。 */
    @GetMapping("/download-url")
    public Map<String, Object> downloadUrl(@RequestParam String objectKey,
                                            @RequestParam(defaultValue = "platform") String applicationCode, Authentication authentication) {
        authorization.require(authentication, "files:view");
        requireApplication(authentication,applicationCode);
        return client.downloadUrl(objectKey, authentication.getName(), "default");
    }

    /** 批量删除。 */
    @DeleteMapping("/batch")
    @com.seeex.tech.web.audit.AuditLog(action = "DELETE_FILE", resourceType = "FILE")
    public Map<String, Object> delete(@RequestBody List<String> objectKeys,
                                      @RequestParam(defaultValue = "platform") String applicationCode, Authentication authentication) {
        authorization.require(authentication, "files:delete");
        requireApplication(authentication,applicationCode);
        return client.delete(objectKeys, authentication.getName(), "default");
    }
    /** 文件仍限制本人归属，同时再次校验应用访问，不能伪造跨应用上下文。 */
    private void requireApplication(Authentication auth, String app) {
        if ("platform".equals(app)) { bindApplication(app); return; }
        var view = applications.detail(app);
        if (!"ACTIVE".equals(view.status()) || !authorization.canApplication(auth,app,"platform:apps:update") && applications.scopedPermissions(app,null,permissions.snapshot(Long.parseLong(auth.getName())).effectiveCodes()).isEmpty()) {
            throw new com.seeex.tech.common.exception.BizException(com.seeex.tech.common.api.ErrorCode.FORBIDDEN,"无权访问该应用文件");
        }
        bindApplication(app);
    }
    private void bindApplication(String app) {
        if (org.springframework.web.context.request.RequestContextHolder.getRequestAttributes() instanceof org.springframework.web.context.request.ServletRequestAttributes attrs) attrs.getRequest().setAttribute("seeex.file.application",app);
    }

}
