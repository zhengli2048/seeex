package com.seeex.tech.web.registry;

import org.junit.jupiter.api.Test;
import com.seeex.tech.web.auth.TokenSessionService;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ApplicationRegistryServiceTest {

    @Test
    void exchangeCodeCanOnlyBeConsumedOnce() {
        ApplicationRegistryService service = new ApplicationRegistryService();
        ApplicationRegistryService.AppRegistration registration = service.register(
                new ApplicationRegistryService.RegisterAppCommand("demo-app", "Demo", "https://demo.example.com", null));
        service.report("demo-app", registration.ciToken(), new ApplicationRegistryService.MetadataReportCommand(
                "1.0.0", "abc", "PROD", "https://demo.example.com",
                List.of(new ApplicationRegistryService.RouteMetadata("home", "首页", "/", null, null, "EXTERNAL", true, 1))));
        service.review("demo-app", "1.0.0", true);
        service.publish("demo-app", "1.0.0");
        String code = service.createExchangeCode("demo-app", "employee-1", "/");

        assertEquals("employee-1", service.exchange(code, "demo-app").subjectId());
        assertThrows(RuntimeException.class, () -> service.exchange(code, "demo-app"));
    }

    @Test
    void metadataRequiresApprovalAndSupportsRollout() {
        ApplicationRegistryService service = new ApplicationRegistryService();
        ApplicationRegistryService.AppRegistration registration = service.register(
                new ApplicationRegistryService.RegisterAppCommand("review-app", "Review", "https://review.example.com", null));
        service.report("review-app", registration.ciToken(), new ApplicationRegistryService.MetadataReportCommand(
                "2.0.0", "def", "PROD", "https://review.example.com", List.of(new ApplicationRegistryService.RouteMetadata("home", "首页", "/", null, null, "EXTERNAL", true, 1))));
        service.review("review-app", "2.0.0", true);
        service.rollout("review-app", "2.0.0", 25);
        service.publish("review-app", "2.0.0");
        assertEquals("2.0.0", service.listApps().get(0).release().version());
    }

    @Test
    void refreshTokenRotatesAndOldTokenStopsWorking() {
        TokenSessionService service = new TokenSessionService();
        TokenSessionService.TokenPair first = service.issue("employee-1", "web-console");
        TokenSessionService.TokenPair second = service.rotate(first.refreshToken());
        assertEquals("employee-1", service.introspect(second.accessToken()).subjectId());
        assertThrows(RuntimeException.class, () -> service.rotate(first.refreshToken()));
    }
    /** 双端同路径使用独立权限，Web 权限不能打开后台。用例仅编写，未执行。 */
    @Test
    void webAndAdminRequireIndependentPermissions() {
        ApplicationRegistryService service = new ApplicationRegistryService();
        var registered = service.register(new ApplicationRegistryService.RegisterAppCommand("dual-app", "双端应用", "https://web.example.com", null, null,
                "WEB", null, null, "ACTIVE", "https://admin.example.com", null, "/sso/callback"));
        service.report("dual-app", registered.ciToken(), new ApplicationRegistryService.MetadataReportCommand("1", null, "PROD", "https://web.example.com", List.of(
                new ApplicationRegistryService.RouteMetadata("web-home", "业务首页", "/", null, null, "NEW_TAB", true, 0, "WEB", null, "业务"),
                new ApplicationRegistryService.RouteMetadata("admin-home", "管理首页", "/", null, null, "NEW_TAB", true, 0, "ADMIN", null, "管理"))));
        service.review("dual-app", "1", true); service.publish("dual-app", "1");
        Set<String> webOnly = Set.of("app:dual-app:web:web-home:view");
        assertEquals("WEB", service.authorizedRoute("dual-app", "WEB", "/", webOnly, "1").entry());
        assertThrows(com.seeex.tech.common.exception.BizException.class, () -> service.authorizedRoute("dual-app", "ADMIN", "/", webOnly, "1"));
        String code = service.createExchangeCode("dual-app", "1", "/", "ADMIN");
        assertThrows(com.seeex.tech.common.exception.BizException.class, () -> service.exchange(code, "dual-app", "WEB"));
        assertEquals("ADMIN", service.exchange(code, "dual-app", "ADMIN").entry());
    }

    /** CI 凭证不能用于应用鉴权；轮换立即拒绝旧服务凭证。用例仅编写，未执行。 */
    @Test
    void appServiceCredentialIsSeparateAndRotatable() {
        ApplicationRegistryService service = new ApplicationRegistryService();
        var registered = service.register(new ApplicationRegistryService.RegisterAppCommand("secure-app", "应用", "https://secure.example.com", null));
        service.authenticateClient("secure-app", registered.clientSecret());
        assertThrows(com.seeex.tech.common.exception.BizException.class, () -> service.authenticateClient("secure-app", registered.ciToken()));
        String rotated = service.rotateClientSecret("secure-app");
        assertThrows(com.seeex.tech.common.exception.BizException.class, () -> service.authenticateClient("secure-app", registered.clientSecret()));
        service.authenticateClient("secure-app", rotated);
    }

}
