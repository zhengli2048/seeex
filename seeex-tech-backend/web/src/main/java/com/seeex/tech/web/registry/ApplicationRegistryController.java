package com.seeex.tech.web.registry;

import com.seeex.tech.common.api.ApiResponse;
import com.seeex.tech.common.api.ErrorCode;
import com.seeex.tech.common.exception.BizException;
import com.seeex.tech.web.audit.AuditLog;
import com.seeex.tech.web.auth.ManagementAuthorizationService;
import com.seeex.tech.web.rbac.PermissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** 应用管理、版本控制与受控跨应用身份交换。 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ApplicationRegistryController {
    private final ApplicationRegistryService registry;
    private final PermissionService permissions;
    private final ManagementAuthorizationService authorization;

    /** 注册应用并仅返回一次 CI 及应用服务凭证。 */
    @PostMapping("/platform/apps")
    @AuditLog(action = "REGISTER_APPLICATION", resourceType = "APPLICATION")
    public ApiResponse<ApplicationRegistryService.AppRegistration> register(@RequestBody ApplicationRegistryService.RegisterAppCommand command, Authentication auth) {
        authorization.require(auth, "platform:apps:create"); return ApiResponse.ok(registry.register(command));
    }
    /** 查询管理列表。 */
    @GetMapping("/platform/apps")
    public ApiResponse<List<ApplicationRegistryService.AppView>> apps(Authentication auth) {
        return ApiResponse.ok(registry.listApps().stream().filter(app -> (authorization.canApplication(auth,app.appCode(),"platform:apps:view") || authorization.canApplication(auth,app.appCode(),"platform:apps:update"))).toList());
    }
    /** 返回本人可用或可管理的文件应用选择项，无部署和凭证信息。 */
    @GetMapping("/me/file-applications")
    public ApiResponse<List<FileApplication>> fileApplications(Authentication auth) {
        var effective = permissions.snapshot(Long.parseLong(auth.getName())).effectiveCodes();
        var rows = new java.util.ArrayList<FileApplication>(); rows.add(new FileApplication("platform","基础平台"));
        registry.listApps().stream().filter(app -> "ACTIVE".equals(app.status()) && (authorization.canApplication(auth,app.appCode(),"platform:apps:update") || !registry.scopedPermissions(app.appCode(),null,effective).isEmpty())).forEach(app -> rows.add(new FileApplication(app.appCode(),app.name())));
        return ApiResponse.ok(rows);
    }
    /** 文件应用名称选项。 */
    public record FileApplication(String appCode,String name) { }
    /** 本人可管理的应用编码；不授予平台其他管理权限。 */
    @GetMapping("/me/application-management")
    public ApiResponse<List<String>> managed(Authentication auth) { return ApiResponse.ok(registry.listApps().stream().filter(app -> authorization.isApplicationAdmin(auth,app.appCode())).map(ApplicationRegistryService.AppView::appCode).toList()); }
    /** 查询应用详情。 */
    @GetMapping("/platform/apps/{appCode}")
    public ApiResponse<ApplicationRegistryService.AppView> detail(@PathVariable String appCode, Authentication auth) {
        authorization.requireApplication(auth, appCode, "platform:apps:view"); return ApiResponse.ok(registry.detail(appCode));
    }
    /** 更新基本信息和状态。 */
    @PutMapping("/platform/apps/{appCode}")
    @AuditLog(action = "UPDATE_APPLICATION", resourceType = "APPLICATION")
    public ApiResponse<ApplicationRegistryService.AppView> update(@PathVariable String appCode, @RequestBody ApplicationRegistryService.RegisterAppCommand command, Authentication auth) {
        authorization.requireApplication(auth, appCode, "platform:apps:update"); return ApiResponse.ok(registry.update(appCode, command));
    }
    /** CI 使用应用凭证上报，无需用户令牌。 */
    @PostMapping("/platform/apps/{appCode}/route-metadata")
    public ApiResponse<ApplicationRegistryService.MetadataRelease> report(@PathVariable String appCode,
            @RequestHeader(value = "X-CI-Token", required = false) String token, @RequestBody ApplicationRegistryService.MetadataReportCommand command) {
        return ApiResponse.ok(registry.report(appCode, token, command));
    }
    /** 管理员手工录入结构化路由。 */
    @PostMapping("/platform/apps/{appCode}/route-metadata/manual")
    @AuditLog(action = "IMPORT_ROUTES", resourceType = "APPLICATION")
    public ApiResponse<ApplicationRegistryService.MetadataRelease> manual(@PathVariable String appCode, @RequestBody ApplicationRegistryService.MetadataReportCommand command, Authentication auth) {
        authorization.requireApplication(auth, appCode, "platform:apps:update"); return ApiResponse.ok(registry.reportManual(appCode, command));
    }
    /** 读取版本列表。 */
    @GetMapping("/platform/apps/{appCode}/route-metadata")
    public ApiResponse<List<ApplicationRegistryService.MetadataRelease>> releases(@PathVariable String appCode, Authentication auth) {
        authorization.requireApplication(auth, appCode, "platform:apps:view"); return ApiResponse.ok(registry.releases(appCode));
    }
    /** 发布审核通过的版本。 */
    @PostMapping("/platform/apps/{appCode}/route-metadata/{version}/publish")
    @AuditLog(action = "PUBLISH_APPLICATION", resourceType = "APPLICATION")
    public ApiResponse<ApplicationRegistryService.MetadataRelease> publish(@PathVariable String appCode, @PathVariable String version, Authentication auth) {
        authorization.requireApplication(auth, appCode, "platform:apps:publish"); return ApiResponse.ok(registry.publish(appCode, version));
    }
    /** 回滚到历史发布版本。 */
    @PostMapping("/platform/apps/{appCode}/route-metadata/{version}/rollback")
    @AuditLog(action = "ROLLBACK_APPLICATION", resourceType = "APPLICATION")
    public ApiResponse<ApplicationRegistryService.MetadataRelease> rollback(@PathVariable String appCode, @PathVariable String version, Authentication auth) {
        authorization.requireApplication(auth, appCode, "platform:apps:publish"); return ApiResponse.ok(registry.rollback(appCode, version));
    }
    /** 轮换 CI 凭证。 */
    @PostMapping("/platform/apps/{appCode}/ci-credentials/rotate")
    @AuditLog(action = "ROTATE_CI_CREDENTIAL", resourceType = "APPLICATION")
    public ApiResponse<CiTokenResponse> rotate(@PathVariable String appCode, Authentication auth) {
        authorization.requireApplication(auth, appCode, "platform:apps:ci"); return ApiResponse.ok(new CiTokenResponse(registry.rotateCiToken(appCode)));
    }
    /** 审核元数据。 */
    @PostMapping("/platform/apps/{appCode}/route-metadata/{version}/review")
    @AuditLog(action = "REVIEW_APPLICATION", resourceType = "APPLICATION")
    public ApiResponse<ApplicationRegistryService.MetadataRelease> review(@PathVariable String appCode, @PathVariable String version, @RequestBody ReviewRequest command, Authentication auth) {
        authorization.requireApplication(auth, appCode, "platform:apps:review"); return ApiResponse.ok(registry.review(appCode, version, command.approved()));
    }
    /** 调整灰度比例。 */
    @PostMapping("/platform/apps/{appCode}/route-metadata/{version}/rollout")
    @AuditLog(action = "ROLLOUT_APPLICATION", resourceType = "APPLICATION")
    public ApiResponse<ApplicationRegistryService.MetadataRelease> rollout(@PathVariable String appCode, @PathVariable String version, @RequestBody RolloutRequest command, Authentication auth) {
        authorization.requireApplication(auth, appCode, "platform:apps:review"); return ApiResponse.ok(registry.rollout(appCode, version, command.percent()));
    }
    /** 当前用户有效权限过滤后的菜单。 */
    @GetMapping("/me/menus")
    public ApiResponse<List<ApplicationRegistryService.MenuView>> menus(Authentication auth) {
        return ApiResponse.ok(visibleMenus(auth));
    }
    /** 仅返回当前用户实际可访问的应用。 */
    @GetMapping("/me/apps")
    public ApiResponse<List<ApplicationRegistryService.AppView>> myApps(Authentication auth) {
        var visible = visibleMenus(auth);
        var codes = visible.stream().map(ApplicationRegistryService.MenuView::appCode).collect(java.util.stream.Collectors.toSet());
        return ApiResponse.ok(registry.listApps().stream().filter(app -> codes.contains(app.appCode())).map(app -> {
            var release = app.release();
            var routes = visible.stream().filter(menu -> menu.appCode().equals(app.appCode())).map(ApplicationRegistryService.MenuView::route).toList();
            var filtered = new ApplicationRegistryService.MetadataRelease(release.id(), release.appCode(), release.version(), release.gitCommit(), release.environment(), release.baseUrl(), routes, release.published(), release.reportedAt(), release.approvalStatus(), release.rolloutPercent());
            return new ApplicationRegistryService.AppView(app.appCode(), app.name(), app.baseUrl(), null, filtered, app.description(), app.type(), app.logo(), null, app.status(), null, null, app.ssoCallbackPath());
        }).toList());
    }
    /** 为真实用户生成授权跳转地址。 */
    @GetMapping("/sso/authorize")
    public ApiResponse<ExchangeCodeResponse> authorize(@RequestParam String appCode, @RequestParam(defaultValue = "/") String redirectPath,
            @RequestParam(defaultValue = "WEB") String entry, Authentication auth) {
        if (!java.util.Set.of("WEB", "ADMIN").contains(entry)) { throw new BizException(ErrorCode.VALIDATION_FAILED, "入口类型不合法"); }
        registry.authorizedRoute(appCode, entry, redirectPath, permissions.snapshot(Long.parseLong(auth.getName())).effectiveCodes(), auth.getName());
        var app = registry.detail(appCode);
        String base = "ADMIN".equals(entry) ? app.adminUrl() : app.baseUrl();
        if (base == null || base.isBlank()) { throw new BizException(ErrorCode.VALIDATION_FAILED, "应用未配置该入口"); }
        String code = registry.createExchangeCode(appCode, auth.getName(), redirectPath, entry);
        String url = base.replaceAll("/+$", "") + app.ssoCallbackPath() + "?code=" + URLEncoder.encode(code, StandardCharsets.UTF_8)
                + "&appCode=" + URLEncoder.encode(appCode, StandardCharsets.UTF_8) + "&entry=" + entry;
        return ApiResponse.ok(new ExchangeCodeResponse(code, 60, url));
    }
    /** 目标应用消费短期单次交换码。 */
    @PostMapping("/sso/token/exchange")
    public ApiResponse<ApplicationRegistryService.SsoExchange> exchange(@RequestBody ExchangeRequest request, @RequestHeader(value = "X-App-Secret", required = false) String secret) {
        registry.authenticateClient(request.appCode(), secret);
        var exchanged = registry.exchange(request.code(), request.appCode(), request.entry());
        var snapshot = permissions.snapshot(Long.parseLong(exchanged.subjectId()));
        registry.authorizedRoute(exchanged.appCode(), exchanged.entry(), exchanged.redirectPath(), snapshot.effectiveCodes(), exchanged.subjectId());
        return ApiResponse.ok(new ApplicationRegistryService.SsoExchange(exchanged.subjectId(), exchanged.appCode(), exchanged.redirectPath(), exchanged.expiresAt(), exchanged.entry(), List.copyOf(registry.scopedPermissions(exchanged.appCode(), exchanged.entry(), snapshot.effectiveCodes()))));
    }
    /** 轮换目标应用后端的服务凭证，与 CI Token 分离。 */
    @PostMapping("/platform/apps/{appCode}/service-credentials/rotate")
    @AuditLog(action = "ROTATE_APP_CREDENTIAL", resourceType = "APPLICATION")
    public ApiResponse<ClientSecretResponse> rotateClient(@PathVariable String appCode, Authentication auth) {
        authorization.requireApplication(auth, appCode, "platform:apps:auth"); return ApiResponse.ok(new ClientSecretResponse(registry.rotateClientSecret(appCode)));
    }
    /** 服务凭证仅在创建或轮换时返回。 */
    public record ClientSecretResponse(String clientSecret) { }

    private List<ApplicationRegistryService.MenuView> visibleMenus(Authentication auth) {
        if (auth == null) { throw new BizException(ErrorCode.UNAUTHORIZED, "请先登录"); }
        return registry.menus(List.copyOf(permissions.snapshot(Long.parseLong(auth.getName())).effectiveCodes()), auth.getName());
    }
    /** 授权跳转结果。 */
    public record ExchangeCodeResponse(String code, long expiresIn, String redirectUrl) { }
    /** 交换请求。 */
    public record ExchangeRequest(String code, String appCode, String entry) {
        public ExchangeRequest { entry = entry == null ? "WEB" : entry; }
    }
    /** 一次性凭证结果。 */
    public record CiTokenResponse(String ciToken) { }
    /** 审核参数。 */
    public record ReviewRequest(boolean approved) { }
    /** 灰度参数。 */
    public record RolloutRequest(int percent) { }
}
