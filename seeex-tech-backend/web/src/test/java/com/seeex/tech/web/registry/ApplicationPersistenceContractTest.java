package com.seeex.tech.web.registry;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.seeex.tech.web.registry.entity.ApplicationDO;
import com.seeex.tech.web.registry.entity.ApplicationRouteReleaseDO;
import com.seeex.tech.web.registry.mapper.ApplicationMapper;
import com.seeex.tech.web.registry.mapper.ApplicationRouteReleaseMapper;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 发布版本回读及审核门禁回归用例；本轮只编写不执行。 */
class ApplicationPersistenceContractTest {
    @Test
    void databaseAppsRetainPublishedReleaseAndMenu() {
        ApplicationMapper apps = mock(ApplicationMapper.class);
        ApplicationRouteReleaseMapper releases = mock(ApplicationRouteReleaseMapper.class);
        ApplicationDO app = new ApplicationDO(); app.setAppCode("recruit"); app.setAppName("招聘"); app.setBaseUrl("https://recruit.example.com"); app.setStatus("ACTIVE"); app.setActiveVersion("1");
        ApplicationRouteReleaseDO release = new ApplicationRouteReleaseDO(); release.setAppCode("recruit"); release.setVersion("1"); release.setStatus("PUBLISHED"); release.setApprovalStatus("APPROVED"); release.setRolloutPercent(100); release.setReportedAt(LocalDateTime.now());
        release.setMetadataJson("[{\"routeCode\":\"home\",\"name\":\"首页\",\"path\":\"/\",\"permissionCode\":\"recruit:view\",\"visible\":true,\"sortOrder\":0,\"openType\":\"EXTERNAL\"}]");
        when(apps.selectList(any())).thenReturn(List.of(app)); when(releases.selectOne(any())).thenReturn(release);
        ApplicationRegistryService service = new ApplicationRegistryService(apps, releases, new ObjectMapper(), null, null);
        assertEquals("1", service.listApps().get(0).release().version());
        assertEquals(1, service.menus(List.of("recruit:view"), "1").size());
        assertEquals(0, service.menus(List.of(), "1").size());
    }

    @Test
    void unreviewedVersionsCannotPublish() {
        ApplicationRegistryService service = new ApplicationRegistryService();
        var app = service.register(new ApplicationRegistryService.RegisterAppCommand("recruit", "招聘", "https://recruit.example.com", null));
        service.report("recruit", app.ciToken(), new ApplicationRegistryService.MetadataReportCommand("1", null, "PROD", "https://recruit.example.com", List.of(new ApplicationRegistryService.RouteMetadata("home", "首页", "/", null, null, "EXTERNAL", true, 0))));
        assertThrows(com.seeex.tech.common.exception.BizException.class, () -> service.publish("recruit", "1"));
    }
}
