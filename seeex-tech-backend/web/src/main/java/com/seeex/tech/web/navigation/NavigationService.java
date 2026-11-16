package com.seeex.tech.web.navigation;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.seeex.tech.web.rbac.PermissionService;
import com.seeex.tech.web.rbac.entity.ResourceDO;
import com.seeex.tech.web.rbac.mapper.ResourceMapper;
import com.seeex.tech.web.registry.ApplicationRegistryService;
import com.seeex.tech.web.registry.ApplicationRegistryService.AppView;
import com.seeex.tech.web.registry.ApplicationRegistryService.MenuView;
import com.seeex.tech.web.registry.ApplicationRegistryService.RouteMetadata;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** 按当前员工权限聚合工作台、基础平台及各业务应用的可见导航树。 */
@Service
public class NavigationService {
    /** 基础平台的固定应用编码。 */
    private static final String PLATFORM = "platform";
    /** 权限分析复用员工查看权限，不重复创建同权限码资源。 */
    private static final String ANALYSIS_PERMISSION = "employees:view";
    /** 与前端真实路由保持一致的权限分析地址。 */
    private static final String ANALYSIS_PATH = "/permissions";
    /** 防止损坏或异常深层级导致递归耗尽调用栈。 */
    private static final int MAX_DEPTH = 64;
    /** 同一应用内端的固定展示顺序。 */
    private static final List<String> ENTRIES = List.of("WEB", "ADMIN");

    @org.springframework.beans.factory.annotation.Autowired private com.seeex.tech.web.governance.ApplicationAdminMapper appAdmins;
    private final ResourceMapper resourceMapper;
    private final PermissionService permissionService;
    private final ApplicationRegistryService registry;

    /** 注入资源、有效权限计算和应用注册服务。 */
    public NavigationService(ResourceMapper resourceMapper, PermissionService permissionService,
                             ApplicationRegistryService registry) {
        this.resourceMapper = resourceMapper;
        this.permissionService = permissionService;
        this.registry = registry;
    }

    /**
     * 读取已认证员工的当前导航，不将菜单可见性当作业务 API 授权。
     *
     * @param employeeId 已认证会话中的员工主键
     * @return 工作台、非空基础平台和各独立应用分组
     */
    public List<NavNode> navigation(long employeeId) {
        Set<String> permissions = permissionService.snapshot(employeeId).effectiveCodes();
        List<NavNode> result = new ArrayList<>();
        result.add(new NavNode("workspace", "工作台", "INTERNAL", PLATFORM, "ADMIN", "/",
                "CURRENT_TAB", List.of()));
        List<NavNode> platform = new ArrayList<>(platformNavigation(permissions));
        if (!containsPath(platform,"/applications") && appAdmins.selectCount(Wrappers.<com.seeex.tech.web.governance.ApplicationAdminDO>lambdaQuery().eq(com.seeex.tech.web.governance.ApplicationAdminDO::getEmployeeId,employeeId)) > 0) { platform.add(new NavNode("managed-applications","应用管理","INTERNAL",PLATFORM,"ADMIN","/applications","CURRENT_TAB",List.of())); }
        if (!platform.isEmpty()) {
            result.add(group("platform", "基础平台", PLATFORM, "ADMIN", platform));
        }

        // 使用注册中心完成应用状态、当前版本、灰度和叶节点权限过滤，再补足合法祖先。
        List<MenuView> visible = registry.menus(List.copyOf(permissions), String.valueOf(employeeId));
        Map<String, Set<String>> allowedByApp = new HashMap<>();
        for (MenuView menu : visible) {
            allowedByApp.computeIfAbsent(menu.appCode(), ignored -> new HashSet<>()).add(menu.route().routeCode());
        }
        for (AppView app : registry.listApps()) {
            if (PLATFORM.equals(app.appCode()) || !allowedByApp.containsKey(app.appCode())) {
                continue;
            }
            List<NavNode> entries = applicationNavigation(app, allowedByApp.get(app.appCode()));
            if (!entries.isEmpty()) {
                result.add(group("app:" + app.appCode(), app.name(), app.appCode(), null, entries));
            }
        }
        return List.copyOf(result);
    }

    private List<NavNode> platformNavigation(Set<String> permissions) {
        // 保留隐藏、停用及已删除记录用于判断祖先，避免将它们的孩子提升为根节点。
        List<ResourceDO> rows = resources(PLATFORM);
        Map<Long, List<ResourceDO>> children = new HashMap<>();
        for (ResourceDO row : rows) {
            if (row.getParentId() != null && row.getParentId() > 0) {
                children.computeIfAbsent(row.getParentId(), ignored -> new ArrayList<>()).add(row);
            }
        }
        PlatformContext context = new PlatformContext(children, permissions);
        List<NavNode> nodes = new ArrayList<>();
        for (ResourceDO row : rows.stream().filter(this::isRoot).sorted(resourceOrder()).toList()) {
            NavNode node = platformNode(row, context, new HashSet<>());
            if (node != null) {
                nodes.add(node);
            }
        }
        // 兼容未建立权限中心模块的旧数据；已有但被隐藏/停用的模块绝不绕过。
        boolean hasAuthorizationModule = rows.stream().anyMatch(this::isAuthorizationModule);
        if (!hasAuthorizationModule && permissions.contains(ANALYSIS_PERMISSION) && !containsPath(nodes, ANALYSIS_PATH)) {
            nodes.add(group("platform:authorization", "权限中心", PLATFORM, "ADMIN", List.of(analysisNode())));
        }
        return List.copyOf(nodes);
    }

    private NavNode platformNode(ResourceDO row, PlatformContext context, Set<Long> ancestry) {
        if (!isVisibleResource(row) || row.getId() == null || ancestry.size() >= MAX_DEPTH || !ancestry.add(row.getId())) {
            return null;
        }
        List<NavNode> children = new ArrayList<>();
        for (ResourceDO child : context.children().getOrDefault(row.getId(), List.of()).stream().sorted(resourceOrder()).toList()) {
            NavNode node = platformNode(child, context, ancestry);
            if (node != null) {
                children.add(node);
            }
        }
        if (isAuthorizationModule(row) && context.permissions().contains(ANALYSIS_PERMISSION)
                && !containsPath(children, ANALYSIS_PATH)) {
            children.add(analysisNode());
        }
        ancestry.remove(row.getId());
        boolean canNavigate = "MENU".equals(row.getResourceType())
                && context.permissions().contains(row.getPermissionCode()) && isInternalPath(row.getRoutePath())
                && !"/".equals(row.getRoutePath());
        if (!canNavigate && children.isEmpty()) {
            return null;
        }
        return new NavNode("platform:resource:" + row.getId(), label(row), canNavigate ? "INTERNAL" : "GROUP",
                PLATFORM, "ADMIN", canNavigate ? row.getRoutePath() : null,
                canNavigate ? "CURRENT_TAB" : null, children);
    }

    private List<NavNode> applicationNavigation(AppView app, Set<String> allowed) {
        List<ResourceDO> resources = resources(app.appCode());
        Map<String, ResourceDO> byPermission = new HashMap<>();
        for (ResourceDO resource : resources) {
            byPermission.put(resource.getPermissionCode(), resource);
        }
        Map<Long, String> keyByResource = new HashMap<>();
        Map<String, ExternalItem> byKey = new LinkedHashMap<>();
        List<RouteMetadata> routes = registry.effectiveRoutes(app);
        for (RouteMetadata route : routes) {
            ResourceDO resource = byPermission.get(route.permissionCode());
            if (resource != null && resource.getId() != null) {
                keyByResource.put(resource.getId(), routeKey(app.appCode(), route.routeCode()));
            }
        }
        for (ResourceDO resource : resources) {
            if ("MODULE".equals(resource.getResourceType()) && resource.getId() != null) {
                keyByResource.put(resource.getId(), moduleKey(app.appCode(), resource.getId()));
            }
        }
        for (RouteMetadata route : routes) {
            ResourceDO resource = byPermission.get(route.permissionCode());
            String parent = resource == null ? routeParent(app.appCode(), route.parentRouteCode())
                    : resourceParent(resource, keyByResource);
            ExternalItem item = new ExternalItem(routeKey(app.appCode(), route.routeCode()), route.name(), parent,
                    route.entry(), route.category(), route, resource, route.sortOrder());
            byKey.put(item.key(), item);
        }
        for (ResourceDO resource : resources) {
            if (!"MODULE".equals(resource.getResourceType()) || resource.getId() == null) {
                continue;
            }
            ExternalItem item = new ExternalItem(moduleKey(app.appCode(), resource.getId()), label(resource),
                    resourceParent(resource, keyByResource), entry(resource), category(resource), null, resource,
                    resource.getSortOrder() == null ? 0 : resource.getSortOrder());
            byKey.put(item.key(), item);
        }
        Map<String, List<ExternalItem>> children = new HashMap<>();
        for (ExternalItem item : byKey.values()) {
            if (item.parentKey() != null) {
                children.computeIfAbsent(item.parentKey(), ignored -> new ArrayList<>()).add(item);
            }
        }
        ExternalContext context = new ExternalContext(app.appCode(), children, allowed);
        List<NavNode> entries = new ArrayList<>();
        for (String entry : ENTRIES) {
            String baseUrl = "ADMIN".equals(entry) ? app.adminUrl() : app.baseUrl();
            if (baseUrl == null || baseUrl.isBlank()) {
                continue;
            }
            Map<String, List<NavNode>> categories = new LinkedHashMap<>();
            for (ExternalItem root : byKey.values().stream().filter(item -> item.parentKey() == null && entry.equals(item.entry()))
                    .sorted(externalOrder()).toList()) {
                NavNode node = externalNode(root, context, new HashSet<>());
                if (node != null) {
                    categories.computeIfAbsent(root.category(), ignored -> new ArrayList<>()).add(node);
                }
            }
            List<NavNode> categoryNodes = new ArrayList<>();
            categories.forEach((category, nodes) -> categoryNodes.add(group("app:" + app.appCode() + ":" + entry
                    + ":category:" + category, category, app.appCode(), entry, nodes)));
            if (!categoryNodes.isEmpty()) {
                entries.add(group("app:" + app.appCode() + ":entry:" + entry,
                        "ADMIN".equals(entry) ? "管理后台" : "业务端", app.appCode(), entry, categoryNodes));
            }
        }
        return List.copyOf(entries);
    }

    private NavNode externalNode(ExternalItem item, ExternalContext context, Set<String> ancestry) {
        if ((item.resource() != null && !isVisibleResource(item.resource()))
                || (item.route() != null && !item.route().visible())
                || ancestry.size() >= MAX_DEPTH || !ancestry.add(item.key())) {
            return null;
        }
        List<NavNode> children = new ArrayList<>();
        for (ExternalItem child : context.children().getOrDefault(item.key(), List.of()).stream().sorted(externalOrder()).toList()) {
            // 跨端、跨分类的父子属于不合法层级，不能重挂为顶层绕过祖先门禁。
            if (!Objects.equals(item.entry(), child.entry()) || !Objects.equals(item.category(), child.category())) {
                continue;
            }
            NavNode node = externalNode(child, context, ancestry);
            if (node != null) {
                children.add(node);
            }
        }
        ancestry.remove(item.key());
        boolean canNavigate = item.route() != null && context.allowed().contains(item.route().routeCode())
                && isInternalPath(item.route().path());
        if (!canNavigate && children.isEmpty()) {
            return null;
        }
        return new NavNode(item.key(), item.label(), canNavigate ? "EXTERNAL" : "GROUP", context.appCode(), item.entry(),
                canNavigate ? item.route().path() : null, canNavigate ? item.route().openType() : null, children);
    }

    private List<ResourceDO> resources(String appCode) {
        return resourceMapper.selectList(Wrappers.<ResourceDO>lambdaQuery().eq(ResourceDO::getApplicationCode, appCode));
    }

    private boolean isVisibleResource(ResourceDO resource) {
        return resource.getDeletedAt() == null && "ACTIVE".equals(resource.getStatus()) && Boolean.TRUE.equals(resource.getVisible())
                && ("MENU".equals(resource.getResourceType()) || "MODULE".equals(resource.getResourceType()));
    }

    private boolean isRoot(ResourceDO resource) {
        return resource.getParentId() == null || resource.getParentId() <= 0;
    }

    private boolean isAuthorizationModule(ResourceDO resource) {
        return "MODULE".equals(resource.getResourceType()) && ("nav.authorization".equals(resource.getCode())
                || "platform:navigation:authorization".equals(resource.getPermissionCode()));
    }

    private boolean isInternalPath(String path) {
        return path != null && path.startsWith("/") && !path.startsWith("//") && !path.contains("\\")
                && !path.contains("..") && !path.contains("\r") && !path.contains("\n");
    }

    private boolean containsPath(List<NavNode> nodes, String path) {
        return nodes.stream().anyMatch(node -> path.equals(node.path()) || containsPath(node.children(), path));
    }

    private String resourceParent(ResourceDO resource, Map<Long, String> keys) {
        if (isRoot(resource)) {
            return null;
        }
        // 缺失、跨应用或未发布的父资源使用不可达键，不能误当成无父级资源显示。
        return keys.getOrDefault(resource.getParentId(), "unavailable:resource:" + resource.getParentId());
    }

    private String routeParent(String appCode, String routeCode) {
        return routeCode == null || routeCode.isBlank() ? null : routeKey(appCode, routeCode);
    }

    private String routeKey(String appCode, String routeCode) {
        return "app:" + appCode + ":route:" + routeCode;
    }

    private String moduleKey(String appCode, Long resourceId) {
        return "app:" + appCode + ":module:" + resourceId;
    }

    private String entry(ResourceDO resource) {
        return resource.getEntryType() == null ? "WEB" : resource.getEntryType();
    }

    private String category(ResourceDO resource) {
        return resource.getMenuCategory() == null || resource.getMenuCategory().isBlank()
                ? "常用功能" : resource.getMenuCategory().trim();
    }

    private String label(ResourceDO resource) {
        return resource.getName() == null || resource.getName().isBlank() ? resource.getCode() : resource.getName();
    }

    private Comparator<ResourceDO> resourceOrder() {
        return Comparator.comparingInt((ResourceDO resource) -> resource.getSortOrder() == null ? 0 : resource.getSortOrder())
                .thenComparing(ResourceDO::getId, Comparator.nullsLast(Comparator.naturalOrder()));
    }

    private Comparator<ExternalItem> externalOrder() {
        return Comparator.comparingInt(ExternalItem::sortOrder).thenComparing(ExternalItem::key);
    }

    private NavNode analysisNode() {
        return new NavNode("platform:permission-analysis", "权限分析", "INTERNAL", PLATFORM, "ADMIN", ANALYSIS_PATH,
                "CURRENT_TAB", List.of());
    }

    private NavNode group(String key, String label, String appCode, String entry, List<NavNode> children) {
        return new NavNode(key, label, "GROUP", appCode, entry, null, null, children);
    }

    private record PlatformContext(Map<Long, List<ResourceDO>> children, Set<String> permissions) { }

    private record ExternalItem(String key, String label, String parentKey, String entry, String category,
                                RouteMetadata route, ResourceDO resource, int sortOrder) { }

    private record ExternalContext(String appCode, Map<String, List<ExternalItem>> children, Set<String> allowed) { }

    /**
     * 供前端递归渲染的导航节点；GROUP 仅展开，INTERNAL 使用站内路由，EXTERNAL 使用应用 SSO。
     * 可点击节点也可能有 children；不具备自身权限的祖先只返回 GROUP，不提供可点击地址。
     */
    public record NavNode(String key, String label, String kind, String appCode, String entry, String path,
                          String openType, List<NavNode> children) {
        /** 复制子节点，保证对外导航树不可修改且 children 永不为 null。 */
        public NavNode {
            children = children == null ? List.of() : List.copyOf(children);
        }
    }
}
