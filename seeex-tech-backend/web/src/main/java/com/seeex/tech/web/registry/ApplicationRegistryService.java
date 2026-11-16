package com.seeex.tech.web.registry;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.seeex.tech.common.api.ErrorCode;
import com.seeex.tech.common.exception.BizException;
import com.seeex.tech.web.registry.entity.ApplicationDO;
import com.seeex.tech.web.registry.entity.ApplicationRouteReleaseDO;
import com.seeex.tech.web.registry.entity.SsoExchangeCodeDO;
import com.seeex.tech.web.registry.mapper.ApplicationMapper;
import com.seeex.tech.web.registry.mapper.ApplicationRouteReleaseMapper;
import com.seeex.tech.web.registry.mapper.SsoExchangeCodeMapper;
import com.seeex.tech.web.rbac.entity.ResourceDO;
import com.seeex.tech.web.rbac.mapper.ResourceMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** 应用注册、持久化版本发布、权限菜单和单次 SSO 交换服务。 */
@Service
public class ApplicationRegistryService {
    @Autowired private com.seeex.tech.web.onboarding.OnboardingReservationMapper reservations;
    @Autowired private com.seeex.tech.web.organization.mapper.EmployeeMapper owners;
    private static final long CODE_TTL = 60;
    private static final int FULL_ROLLOUT = 100;
    /** 导航祖先校验的最大深度。 */
    private static final int MAX_NAVIGATION_DEPTH = 64;
    private final ApplicationMapper applicationMapper;
    private final ApplicationRouteReleaseMapper releaseMapper;
    private final ObjectMapper json;
    private final SsoExchangeCodeMapper codeMapper;
    private final ResourceMapper resourceMapper;
    // 仅无持久层的单元测试实例使用；正常 Spring 实例始终读写数据库。
    private final Map<String, ApplicationDO> memoryApps = new ConcurrentHashMap<>();
    private final Map<String, ApplicationRouteReleaseDO> memoryReleases = new ConcurrentHashMap<>();
    private final Map<String, SsoExchangeCodeDO> memoryCodes = new ConcurrentHashMap<>();

    /** 创建隔离的内存测试实例。 */
    public ApplicationRegistryService() { this(null, null, new ObjectMapper(), null, null); }

    /** 注入数据库、序列化器与资源持久层。 */
    @Autowired
    public ApplicationRegistryService(ApplicationMapper applicationMapper, ApplicationRouteReleaseMapper releaseMapper,
                                      ObjectMapper json, SsoExchangeCodeMapper codeMapper, ResourceMapper resourceMapper) {
        this.applicationMapper = applicationMapper;
        this.releaseMapper = releaseMapper;
        this.json = json;
        this.codeMapper = codeMapper;
        this.resourceMapper = resourceMapper;
    }

    /** 注册应用；明文 CI 凭证仅在此响应中返回。 */
    @Transactional
    public AppRegistration register(RegisterAppCommand command) { return registerInternal(command,null); }

    /** 已批准申请首次注册，仍复用统一凭证生成和字段校验。 */
    @Transactional
    public AppRegistration registerForRequest(RegisterAppCommand command,Long requestId) {
        if(requestId==null) throw new BizException(ErrorCode.FORBIDDEN,"缺少批准申请关联");
        return registerInternal(command,requestId);
    }

    private AppRegistration registerInternal(RegisterAppCommand command,Long requestId) {
        if (command.appCode() == null || !command.appCode().matches("[a-z][a-z0-9-]{1,63}")) {
            invalid("应用编码须为 2–64 位小写字母、数字或连字符");
        }
        if ("platform".equals(command.appCode())) { invalid("platform 为基础平台保留编码"); }
        if (findApp(command.appCode()) != null) { throw new BizException(ErrorCode.CONFLICT, "应用编码已存在"); }
        if(reservations!=null) {
            var reserved=reservations.selectById(command.appCode());
            if(reserved!=null && !java.util.Objects.equals(reserved.getRequestId(),requestId) || requestId!=null && reserved==null) throw new BizException(ErrorCode.CONFLICT,"应用编码已被申请预留或批准记录不匹配");
            if(requestId==null && reserved==null) {
                var owner=new com.seeex.tech.web.onboarding.OnboardingReservationDO(); owner.setAppCode(command.appCode());
                owner.setRequestId(-1L-new java.security.SecureRandom().nextLong(Long.MAX_VALUE));
                reservations.insert(owner);
            }
        }
        ApplicationDO row = new ApplicationDO();
        row.setOriginatingRequestId(requestId); row.setInitialDeliveryPending(requestId!=null);
        row.setAppCode(command.appCode());
        apply(row, command);
        String token = newToken();
        row.setCiTokenHash(hash(token));
        String clientSecret = "app_" + UUID.randomUUID().toString().replace("-", "");
        row.setClientSecretHash(hash(clientSecret));
        row.setRolloutStrategy("ALL");
        row.setCreatedAt(LocalDateTime.now());
        if (applicationMapper == null) { memoryApps.put(row.getAppCode(), row); }
        else { applicationMapper.insert(row); }
        return new AppRegistration(view(row), token, clientSecret);
    }

    /** 修改基本信息与启停状态，应用编码不可变。 */
    @Transactional
    public AppView update(String appCode, RegisterAppCommand command) {
        ApplicationDO row = requireApp(appCode);
        requireReceived(row);
        if (command.appCode() != null && !appCode.equals(command.appCode())) { invalid("应用编码不可修改"); }
        apply(row, command);
        saveApp(row);
        return view(row);
    }

    /** 查询应用详情，不返回凭证摘要。 */
    public AppView detail(String appCode) { return view(requireApp(appCode)); }

    /** 查询含停用应用的管理列表。 */
    public List<AppView> listApps() {
        List<ApplicationDO> rows = applicationMapper == null ? List.copyOf(memoryApps.values())
                : applicationMapper.selectList(Wrappers.<ApplicationDO>lambdaQuery().orderByAsc(ApplicationDO::getAppCode));
        return rows.stream().map(this::view).sorted(Comparator.comparing(AppView::appCode)).toList();
    }

    /** 按应用凭证上报路由；相同版本相同内容幂等。 */
    @Transactional
    public MetadataRelease report(String appCode, String token, MetadataReportCommand command) {
        ApplicationDO app = requireApp(appCode);
        if (!"ACTIVE".equals(app.getStatus()) || token == null || !MessageDigest.isEqual(
                app.getCiTokenHash().getBytes(StandardCharsets.UTF_8), hash(token).getBytes(StandardCharsets.UTF_8))) {
            throw new BizException(ErrorCode.FORBIDDEN, "CI 凭证无效或应用已停用");
        }
        return saveReport(app, command);
    }

    /** 管理员通过结构化表单导入版本，独立于 CI 凭证。 */
    @Transactional
    public MetadataRelease reportManual(String appCode, MetadataReportCommand command) {
        return saveReport(requireApp(appCode), command);
    }

    /** 读取应用版本与审核、灰度状态。 */
    public List<MetadataRelease> releases(String appCode) {
        requireApp(appCode);
        List<ApplicationRouteReleaseDO> rows = releaseMapper == null ? memoryReleases.values().stream()
                .filter(row -> appCode.equals(row.getAppCode())).toList()
                : releaseMapper.selectList(Wrappers.<ApplicationRouteReleaseDO>lambdaQuery()
                    .eq(ApplicationRouteReleaseDO::getAppCode, appCode).orderByDesc(ApplicationRouteReleaseDO::getReportedAt));
        return rows.stream().map(this::releaseView).sorted(Comparator.comparing(MetadataRelease::reportedAt).reversed()).toList();
    }

    /** 轮换 CI 凭证，旧摘要立即失效。 */
    @Transactional
    public String rotateCiToken(String appCode) {
        ApplicationDO app = requireApp(appCode);
        requireReceived(app);
        String token = newToken();
        app.setCiTokenHash(hash(token));
        saveApp(app);
        return token;
    }

    /** 待收件的受控补发只由申请办理服务调用，明文不得通过普通应用接口返回。 */
    @Transactional
    public AppRegistration reissueForRequest(String appCode,long requestId) {
        var app=requireApp(appCode); if(!Boolean.TRUE.equals(app.getInitialDeliveryPending()) || !java.util.Objects.equals(app.getOriginatingRequestId(),requestId))throw new BizException(ErrorCode.CONFLICT,"申请与首次交付状态不匹配");
        String ci=newToken(),secret="app_"+UUID.randomUUID().toString().replace("-",""); app.setCiTokenHash(hash(ci)); app.setClientSecretHash(hash(secret)); saveApp(app); return new AppRegistration(view(app),ci,secret);
    }
    /** 收件与首次交付门禁解除在同一事务，来源申请不可替换。 */
    @Transactional
    public void confirmRequestDelivery(String appCode,long requestId) {
        if(applicationMapper.update(null,Wrappers.<ApplicationDO>lambdaUpdate().eq(ApplicationDO::getAppCode,appCode).eq(ApplicationDO::getOriginatingRequestId,requestId).eq(ApplicationDO::getInitialDeliveryPending,true).set(ApplicationDO::getInitialDeliveryPending,false))!=1)throw new BizException(ErrorCode.CONFLICT,"首次交付状态已变化，请重新载入");
    }
    private void requireReceived(ApplicationDO app) { if(Boolean.TRUE.equals(app.getInitialDeliveryPending()))throw new BizException(ErrorCode.CONFLICT,"首次交付尚未确认；请通过申请流程领取、确认或补发后再修改应用和轮换凭证"); }

    /** 持久化审核结论；当前发布版本不可直接拒绝。 */
    @Transactional
    public MetadataRelease review(String appCode, String version, boolean approved) {
        ApplicationRouteReleaseDO row = requireRelease(appCode, version);
        if (!approved && version.equals(requireApp(appCode).getActiveVersion())) { invalid("请先回滚当前版本再拒绝审核"); }
        row.setApprovalStatus(approved ? "APPROVED" : "REJECTED");
        saveRelease(row);
        return releaseView(row);
    }

    /** 调整当前版本的稳定用户分桶比例。 */
    @Transactional
    public MetadataRelease rollout(String appCode, String version, int percent) {
        if (percent < 0 || percent > FULL_ROLLOUT) { invalid("灰度比例必须为 0–100"); }
        ApplicationRouteReleaseDO row = requireRelease(appCode, version);
        row.setRolloutPercent(percent);
        saveRelease(row);
        return releaseView(row);
    }

    /** 发布已审核版本并同步其权限资源。 */
    @Transactional
    public MetadataRelease publish(String appCode, String version) {
        ApplicationDO app = requireApp(appCode);
        ApplicationRouteReleaseDO row = requireRelease(appCode, version);
        if (!"APPROVED".equals(row.getApprovalStatus())) { throw new BizException(ErrorCode.CONFLICT, "版本须先审核通过"); }
        if (!"ACTIVE".equals(app.getStatus())) { invalid("停用应用不可发布"); }
        syncResources(appCode, releaseView(row).routes());
        row.setStatus("PUBLISHED");
        saveRelease(row);
        app.setActiveVersion(version);
        saveApp(app);
        return releaseView(row);
    }

    /** 回滚到已发布且审核通过的版本。 */
    @Transactional
    public MetadataRelease rollback(String appCode, String version) {
        if (!"PUBLISHED".equals(requireRelease(appCode, version).getStatus())) { invalid("只能回滚到曾发布的版本"); }
        return publish(appCode, version);
    }

    /** 按当前有效权限返回可见菜单，不以空权限编码默认放行。 */
    public List<MenuView> menus(List<String> permissions) { return menus(permissions, ""); }

    /** 按真实用户稳定分桶，灰度比例为零时隐藏应用。 */
    public List<MenuView> menus(List<String> permissions, String subjectId) {
        int bucket = Math.floorMod(subjectId.hashCode(), FULL_ROLLOUT);
        return listApps().stream().filter(app -> "ACTIVE".equals(app.status()) && app.release() != null
                && bucket < app.release().rolloutPercent())
                .flatMap(app -> menuRoutes(app).stream().filter(route -> permissions.contains(route.permissionCode()))
                    .sorted(Comparator.comparingInt(RouteMetadata::sortOrder))
                    .map(route -> new MenuView(app.appCode(), app.name(), "ADMIN".equals(route.entry()) ? app.adminUrl() : app.baseUrl(), route))).toList();
    }

    /** 菜单展示检查完整祖先；隐藏/停用祖先不能通过工作台的平级入口绕过。 */
    private List<RouteMetadata> menuRoutes(AppView app) {
        List<RouteMetadata> routes = effectiveRoutes(app);
        Map<String, RouteMetadata> byCode = routes.stream().collect(java.util.stream.Collectors.toMap(RouteMetadata::routeCode, route -> route));
        if (resourceMapper == null) {
            return routes.stream().filter(route -> {
                RouteMetadata current = route; Set<String> visited = new HashSet<>();
                while (current != null) {
                    if (!current.visible() || !visited.add(current.routeCode())) { return false; }
                    if (current.parentRouteCode() == null) { return true; }
                    current = byCode.get(current.parentRouteCode());
                    if (current == null) { return false; }
                }
                return true;
            }).toList();
        }
        List<ResourceDO> rows = resourceMapper.selectList(Wrappers.<ResourceDO>lambdaQuery().eq(ResourceDO::getApplicationCode, app.appCode()));
        Map<Long, ResourceDO> byId = rows.stream().collect(java.util.stream.Collectors.toMap(ResourceDO::getId, row -> row));
        Map<String, ResourceDO> byPermission = rows.stream().collect(java.util.stream.Collectors.toMap(ResourceDO::getPermissionCode, row -> row));
        Set<String> published = routes.stream().map(RouteMetadata::permissionCode).collect(java.util.stream.Collectors.toSet());
        return routes.stream().filter(route -> {
            ResourceDO current = byPermission.get(route.permissionCode()); Set<Long> visited = new HashSet<>();
            while (current != null) {
                if (visited.size() >= MAX_NAVIGATION_DEPTH || !visited.add(current.getId()) || current.getDeletedAt() != null || !"ACTIVE".equals(current.getStatus())
                        || !Boolean.TRUE.equals(current.getVisible()) || !route.entry().equals(current.getEntryType()) || !route.category().equals(current.getMenuCategory())
                        || !("MENU".equals(current.getResourceType()) || "MODULE".equals(current.getResourceType())) || "MENU".equals(current.getResourceType()) && !published.contains(current.getPermissionCode())) { return false; }
                if (current.getParentId() == null || current.getParentId() <= 0) { return true; }
                current = byId.get(current.getParentId());
            }
            return false;
        }).toList();
    }

    /** 创建仅保存摘要、60 秒有效的交换码。调用方先校验应用访问权。 */
    public String createExchangeCode(String appCode, String subjectId, String redirectPath) { return createExchangeCode(appCode, subjectId, redirectPath, "WEB"); }

    /** 交换码绑定具体应用及端，不允许 Web 与后台串用。 */
    public String createExchangeCode(String appCode, String subjectId, String redirectPath, String entry) {
        if (!"ACTIVE".equals(requireApp(appCode).getStatus())) { invalid("应用已停用"); }
        if (entry == null || !Set.of("WEB", "ADMIN").contains(entry)) { invalid("入口类型不合法"); }
        text(subjectId, 128, "用户标识"); path(redirectPath);
        String code = UUID.randomUUID().toString().replace("-", "");
        SsoExchangeCodeDO row = new SsoExchangeCodeDO();
        row.setCodeHash(hash(code)); row.setAppCode(appCode); row.setEntryType(entry); row.setSubjectId(subjectId); row.setRedirectPath(redirectPath);
        row.setExpiresAt(LocalDateTime.now().plusSeconds(CODE_TTL));
        if (codeMapper == null) { memoryCodes.put(row.getCodeHash(), row); } else { codeMapper.insert(row); }
        return code;
    }

    /** 使用条件更新原子消费交换码，拒绝过期、重放及停用应用。 */
    @Transactional
    public SsoExchange exchange(String code, String appCode) { return exchange(code, appCode, "WEB"); }

    /** 条件更新消费与目标端匹配的交换码。 */
    @Transactional
    public SsoExchange exchange(String code, String appCode, String entry) {
        if (code == null || appCode == null || entry == null || !Set.of("WEB", "ADMIN").contains(entry)) { throw new BizException(ErrorCode.UNAUTHORIZED, "交换参数缺失或所属端不合法"); }
        if (!"ACTIVE".equals(requireApp(appCode).getStatus())) { throw new BizException(ErrorCode.FORBIDDEN, "应用已停用"); }
        String digest = hash(code);
        SsoExchangeCodeDO row;
        if (codeMapper == null) { row = memoryCodes.get(digest); }
        else { row = codeMapper.selectOne(Wrappers.<SsoExchangeCodeDO>lambdaQuery().eq(SsoExchangeCodeDO::getCodeHash, digest)); }
        LocalDateTime now = LocalDateTime.now();
        if (row == null || !appCode.equals(row.getAppCode()) || !entry.equals(row.getEntryType() == null ? "WEB" : row.getEntryType()) || row.getUsedAt() != null || !row.getExpiresAt().isAfter(now)) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "交换码无效或已过期");
        }
        if (codeMapper == null && !memoryCodes.remove(digest, row)) { throw new BizException(ErrorCode.UNAUTHORIZED, "交换码已消费"); }
        if (codeMapper != null && codeMapper.update(null, Wrappers.<SsoExchangeCodeDO>lambdaUpdate()
                .eq(SsoExchangeCodeDO::getId, row.getId()).eq(SsoExchangeCodeDO::getEntryType, entry).isNull(SsoExchangeCodeDO::getUsedAt)
                .gt(SsoExchangeCodeDO::getExpiresAt, now).set(SsoExchangeCodeDO::getUsedAt, now)) != 1) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "交换码已消费");
        }
        return new SsoExchange(row.getSubjectId(), appCode, row.getRedirectPath(), Instant.now().plusSeconds(300), entry, List.of());
    }

    private MetadataRelease saveReport(ApplicationDO app, MetadataReportCommand command) {
        text(command.version(), 128, "版本号");
        if (!command.version().matches("[A-Za-z0-9._-]{1,128}")) { invalid("版本号仅允许字母、数字、点、下划线和连字符"); }
        if (!Set.of("DEV", "TEST", "PROD").contains(command.environment() == null ? "" : command.environment())) { invalid("环境须为 DEV/TEST/PROD"); }
        if (!app.getBaseUrl().equals(command.baseUrl())) { invalid("版本 Web 地址必须与应用登记地址一致"); }
        if (command.routes() == null || command.routes().isEmpty() || command.routes().stream().anyMatch(java.util.Objects::isNull)) { invalid("至少添加一个非空路由，路由数组不能包含空项"); }
        if (command.gitCommit() != null && command.gitCommit().length() > 128) { invalid("Git Commit 最多128字"); }
        Set<String> codes = new HashSet<>(); Set<String> paths = new HashSet<>();
        List<RouteMetadata> routes = command.routes().stream().map(route -> {
            text(route.routeCode(), 64, "路由编码");
            if (!route.routeCode().matches("[A-Za-z0-9._-]{1,64}")) { invalid("路由编码格式不合法"); } text(route.name(), 128, "路由名称"); path(route.path());
            if (!Set.of("WEB", "ADMIN").contains(route.entry())) { invalid("路由所属端须为 WEB/ADMIN"); }
            if ("ADMIN".equals(route.entry()) && (app.getAdminUrl() == null || app.getAdminUrl().isBlank())) { invalid("后台路由需要先配置后台入口地址"); }
            if (route.icon() != null && route.icon().length() > 128) { invalid("图标标识最多128字"); }
            if (route.category().length() > 64) { invalid("菜单分类最多 64 字"); }
            if (!codes.add(route.routeCode()) || !paths.add(route.entry() + ":" + route.path())) { invalid("路由编码和路径不可重复"); }
            if (!Set.of("EXTERNAL", "CURRENT_TAB", "NEW_TAB").contains(route.openType() == null ? "" : route.openType())) { invalid("打开方式不合法"); }
            String permission = route.permissionCode() == null || route.permissionCode().isBlank()
                    ? "app:" + app.getAppCode() + ":" + route.entry().toLowerCase(java.util.Locale.ROOT) + ":" + route.routeCode() + ":view" : route.permissionCode();
            text(permission, 160, "权限编码");
            return new RouteMetadata(route.routeCode(), route.name(), route.path(), permission, route.icon(), route.openType(), route.visible(), route.sortOrder(), route.entry(), route.parentRouteCode(), route.category());
        }).toList();
        validateHierarchy(routes);
        ApplicationRouteReleaseDO existing = findRelease(app.getAppCode(), command.version());
        String metadata = write(routes);
        if (existing != null) {
            if (metadata.equals(write(releaseView(existing).routes())) && command.environment().equals(existing.getEnvironment())
                    && java.util.Objects.equals(command.gitCommit(), existing.getGitCommit())) { return releaseView(existing); }
            throw new BizException(ErrorCode.CONFLICT, "同版本内容不可变更，请使用新版本号");
        }
        ApplicationRouteReleaseDO row = new ApplicationRouteReleaseDO();
        row.setAppCode(app.getAppCode()); row.setVersion(command.version()); row.setGitCommit(command.gitCommit());
        row.setEnvironment(command.environment()); row.setBaseUrl(command.baseUrl()); row.setMetadataJson(metadata);
        row.setStatus("REPORTED"); row.setApprovalStatus("PENDING"); row.setRolloutPercent(FULL_ROLLOUT); row.setReportedAt(LocalDateTime.now());
        if (releaseMapper == null) { memoryReleases.put(app.getAppCode() + ":" + command.version(), row); } else { releaseMapper.insert(row); }
        return releaseView(row);
    }

    private void syncResources(String appCode, List<RouteMetadata> routes) {
        if (resourceMapper == null) { return; }
        for (RouteMetadata route : routes) {
            ResourceDO row = resourceMapper.selectOne(Wrappers.<ResourceDO>lambdaQuery().eq(ResourceDO::getPermissionCode, route.permissionCode()));
            if (row != null && (!appCode.equals(row.getApplicationCode()) || row.getDeletedAt() != null || !route.entry().equals(row.getEntryType() == null ? "WEB" : row.getEntryType()))) { invalid("权限编码已被其他应用或已删除资源使用"); }
            boolean fresh = row == null;
            if (fresh) { row = new ResourceDO(); row.setCode("route." + hash(appCode + ":" + route.entry() + ":" + route.routeCode() + ":" + route.permissionCode()).substring(0, 32)); }
            row.setPermissionCode(route.permissionCode()); row.setName(route.name()); row.setResourceType("MENU");
            row.setApplicationCode(appCode); row.setEntryType(route.entry()); row.setMenuCategory(route.category()); row.setRoutePath(route.path()); row.setIcon(route.icon());
            row.setVisible(route.visible()); row.setSortOrder(route.sortOrder()); row.setStatus("ACTIVE"); row.setPublicResource(false);
            if (fresh) { resourceMapper.insert(row); } else { resourceMapper.updateById(row); }
        }
        Set<String> retained = routes.stream().map(RouteMetadata::permissionCode).collect(java.util.stream.Collectors.toSet());
        resourceMapper.update(null, Wrappers.<ResourceDO>lambdaUpdate().eq(ResourceDO::getApplicationCode, appCode)
                .likeRight(ResourceDO::getCode, "route.").notIn(ResourceDO::getPermissionCode, retained).set(ResourceDO::getStatus, "INACTIVE"));
        // 所有资源先入库，再关联父节点，避免上报顺序影响层级。
        Map<String, RouteMetadata> byCode = routes.stream().collect(java.util.stream.Collectors.toMap(RouteMetadata::routeCode, route -> route));
        for (RouteMetadata route : routes) {
            ResourceDO child = resourceMapper.selectOne(Wrappers.<ResourceDO>lambdaQuery().eq(ResourceDO::getPermissionCode, route.permissionCode()));
            RouteMetadata parent = route.parentRouteCode() == null ? null : byCode.get(route.parentRouteCode());
            ResourceDO parentRow = parent == null ? null : resourceMapper.selectOne(Wrappers.<ResourceDO>lambdaQuery().eq(ResourceDO::getPermissionCode, parent.permissionCode()));
            child.setParentId(parentRow == null ? null : parentRow.getId()); resourceMapper.updateById(child);
        }
    }

    /** 应用后端凭证与 CI 上报凭证独立验证。 */
    public void authenticateClient(String appCode, String secret) {
        ApplicationDO app = appCode == null || appCode.isBlank() ? null : findApp(appCode);
        if (app == null || !"ACTIVE".equals(app.getStatus()) || secret == null || app.getClientSecretHash() == null
                || !MessageDigest.isEqual(hash(secret).getBytes(StandardCharsets.UTF_8), app.getClientSecretHash().getBytes(StandardCharsets.UTF_8))) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "应用服务凭证无效或尚未配置");
        }
    }

    /** 轮换应用后端凭证，旧凭证立即失效。 */
    @Transactional
    public String rotateClientSecret(String appCode) {
        ApplicationDO app = requireApp(appCode); requireReceived(app); String secret = "app_" + UUID.randomUUID().toString().replace("-", "");
        app.setClientSecretHash(hash(secret)); saveApp(app); return secret;
    }

    /** SSO 与外部 API 根据当前权限独立判断，菜单隐藏不等于禁止已授权页面。 */
    public RouteMetadata authorizedRoute(String appCode, String entry, String path, Set<String> permissions, String subjectId) {
        AppView app = detail(appCode);
        if (entry == null || !Set.of("WEB", "ADMIN").contains(entry) || !"ACTIVE".equals(app.status()) || app.release() == null
                || Math.floorMod(subjectId.hashCode(), FULL_ROLLOUT) >= app.release().rolloutPercent()) { throw new BizException(ErrorCode.FORBIDDEN, "当前不可访问此应用"); }
        return effectiveRoutes(app).stream().filter(route -> route.entry().equals(entry) && java.util.Objects.equals(route.path(), path) && permissions.contains(route.permissionCode()))
                .findFirst().orElseThrow(() -> new BizException(ErrorCode.FORBIDDEN, "无权访问此应用页面或所属端"));
    }

    /** 当前版本限定可用路由身份，资源表提供人工维护后的展示、路径和层级属性。 */
    public List<RouteMetadata> effectiveRoutes(AppView app) {
        if (app.release() == null) { return List.of(); }
        if (resourceMapper == null) { return app.release().routes(); }
        List<ResourceDO> rows = resourceMapper.selectList(Wrappers.<ResourceDO>lambdaQuery().eq(ResourceDO::getApplicationCode, app.appCode())
                .eq(ResourceDO::getStatus, "ACTIVE").isNull(ResourceDO::getDeletedAt));
        Map<String, ResourceDO> byPermission = rows.stream().collect(java.util.stream.Collectors.toMap(ResourceDO::getPermissionCode, row -> row));
        Map<Long, String> routeByResource = new java.util.HashMap<>();
        for (RouteMetadata route : app.release().routes()) { ResourceDO row = byPermission.get(route.permissionCode()); if (row != null) { routeByResource.put(row.getId(), route.routeCode()); } }
        return app.release().routes().stream().filter(route -> { ResourceDO row = byPermission.get(route.permissionCode()); return row != null && "MENU".equals(row.getResourceType()) && row.getRoutePath() != null && row.getRoutePath().startsWith("/"); }).map(route -> {
            ResourceDO row = byPermission.get(route.permissionCode());
            return new RouteMetadata(route.routeCode(), row.getName(), row.getRoutePath(), route.permissionCode(), row.getIcon(), route.openType(), Boolean.TRUE.equals(row.getVisible()),
                    row.getSortOrder() == null ? 0 : row.getSortOrder(), row.getEntryType() == null ? "WEB" : row.getEntryType(), routeByResource.get(row.getParentId()), row.getMenuCategory());
        }).toList();
    }

    /** 仅交付本应用、本端有效资源的权限码。 */
    public Set<String> scopedPermissions(String appCode, String entry, Set<String> codes) {
        if (codes.isEmpty() || resourceMapper == null) { return Set.of(); }
        return resourceMapper.selectList(Wrappers.<ResourceDO>lambdaQuery().eq(ResourceDO::getApplicationCode, appCode)
                .eq(entry != null, ResourceDO::getEntryType, entry).eq(ResourceDO::getStatus, "ACTIVE").isNull(ResourceDO::getDeletedAt)
                .in(ResourceDO::getPermissionCode, codes)).stream().map(ResourceDO::getPermissionCode).collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    private void validateHierarchy(List<RouteMetadata> routes) {
        Map<String, RouteMetadata> byCode = routes.stream().collect(java.util.stream.Collectors.toMap(RouteMetadata::routeCode, route -> route));
        Set<String> permissions = new HashSet<>();
        for (RouteMetadata route : routes) {
            if (!permissions.add(route.permissionCode())) { invalid("同一版本的路由必须使用不同权限编码，Web/后台不可共用"); }
            Set<String> visited = new HashSet<>(); visited.add(route.routeCode()); String parentCode = route.parentRouteCode();
            while (parentCode != null && !parentCode.isBlank()) {
                RouteMetadata parent = byCode.get(parentCode);
                if (parent == null || !visited.add(parentCode) || !parent.entry().equals(route.entry()) || !parent.category().equals(route.category())) { invalid("父路由须存在、同端、同分类，且不能成环"); }
                parentCode = parent.parentRouteCode();
            }
        }
    }

    private void apply(ApplicationDO row, RegisterAppCommand command) {
        if(command.ownerEmployeeId()!=null && owners!=null) { var owner=owners.selectById(command.ownerEmployeeId()); if(owner==null || owner.getDeletedAt()!=null || !"ACTIVE".equals(owner.getStatus()))invalid("应用负责人不存在或已停用"); }
        text(command.name(), 128, "应用名称"); url(command.baseUrl(), true); url(command.adminUrl(), false);
        url(command.apiUrl(), false); url(command.repositoryUrl(), false); url(command.logo(), false);
        String status = command.status() == null ? "ACTIVE" : command.status();
        String type = command.type() == null ? "WEB" : command.type();
        if (!Set.of("ACTIVE", "INACTIVE").contains(status) || !Set.of("WEB", "ADMIN", "AI", "OTHER").contains(type)) { invalid("应用类型或状态不合法"); }
        if (command.description() != null && command.description().length() > 500) { invalid("描述最多 500 字"); }
        String callback = command.ssoCallbackPath() == null ? "/sso/callback" : command.ssoCallbackPath(); path(callback);
        if (callback.length() > 256 || callback.contains("?") || callback.contains("#")) { invalid("SSO 回调路径最多256字且不能包含查询串或片段"); }
        row.setAppName(command.name()); row.setBaseUrl(command.baseUrl().replaceAll("/+$", ""));
        row.setAdminUrl(command.adminUrl()); row.setApiUrl(command.apiUrl()); row.setRepositoryUrl(command.repositoryUrl());
        row.setDescription(command.description()); row.setAppType(type); row.setLogo(command.logo()); row.setOwnerEmployeeId(command.ownerEmployeeId());
        row.setStatus(status); row.setSsoCallbackPath(callback); row.setUpdatedAt(LocalDateTime.now());
    }

    private ApplicationDO findApp(String code) {
        return applicationMapper == null ? memoryApps.get(code) : applicationMapper.selectOne(Wrappers.<ApplicationDO>lambdaQuery().eq(ApplicationDO::getAppCode, code));
    }
    private ApplicationDO requireApp(String code) {
        ApplicationDO app = findApp(code);
        if (app == null) { throw new BizException(ErrorCode.RESOURCE_NOT_FOUND, "应用不存在"); } return app;
    }
    private ApplicationRouteReleaseDO findRelease(String code, String version) {
        return releaseMapper == null ? memoryReleases.get(code + ":" + version) : releaseMapper.selectOne(Wrappers.<ApplicationRouteReleaseDO>lambdaQuery()
                .eq(ApplicationRouteReleaseDO::getAppCode, code).eq(ApplicationRouteReleaseDO::getVersion, version));
    }
    private ApplicationRouteReleaseDO requireRelease(String code, String version) {
        requireApp(code); ApplicationRouteReleaseDO row = findRelease(code, version);
        if (row == null) { throw new BizException(ErrorCode.RESOURCE_NOT_FOUND, "版本不存在"); } return row;
    }
    private void saveApp(ApplicationDO row) { if (applicationMapper != null) { applicationMapper.updateById(row); } }
    private void saveRelease(ApplicationRouteReleaseDO row) { if (releaseMapper != null) { releaseMapper.updateById(row); } }
    private AppView view(ApplicationDO row) {
        ApplicationRouteReleaseDO release = row.getActiveVersion() == null ? null : findRelease(row.getAppCode(), row.getActiveVersion());
        return new AppView(row.getAppCode(), row.getAppName(), row.getBaseUrl(), row.getRepositoryUrl(), release == null ? null : releaseView(release),
                row.getDescription(), row.getAppType(), row.getLogo(), row.getOwnerEmployeeId(), row.getStatus(), row.getAdminUrl(), row.getApiUrl(), row.getSsoCallbackPath(),Boolean.TRUE.equals(row.getInitialDeliveryPending()));
    }
    private MetadataRelease releaseView(ApplicationRouteReleaseDO row) {
        try {
            List<RouteMetadata> routes = json.readValue(row.getMetadataJson(), new TypeReference<List<RouteMetadata>>() { });
            return new MetadataRelease(row.getAppCode() + ":" + row.getVersion(), row.getAppCode(), row.getVersion(), row.getGitCommit(), row.getEnvironment(),
                    row.getBaseUrl(), routes, "PUBLISHED".equals(row.getStatus()), row.getReportedAt().toInstant(ZoneOffset.UTC),
                    row.getApprovalStatus(), row.getRolloutPercent() == null ? FULL_ROLLOUT : row.getRolloutPercent());
        } catch (JsonProcessingException ex) { throw new BizException(ErrorCode.INTERNAL_ERROR, "路由元数据读取失败"); }
    }
    private String write(Object value) { try { return json.writeValueAsString(value); } catch (JsonProcessingException ex) { throw new BizException(ErrorCode.VALIDATION_FAILED, "元数据格式不合法"); } }
    private String newToken() { return "ci_" + UUID.randomUUID().toString().replace("-", ""); }
    private String hash(String value) {
        try { return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException ex) { throw new IllegalStateException("SHA-256 unavailable", ex); }
    }
    private void text(String value, int max, String label) { if (value == null || value.isBlank() || value.length() > max) { invalid(label + "不能为空或超过长度限制"); } }
    private void path(String value) { if (value == null || value.length() > 512 || !value.startsWith("/") || value.startsWith("//") || value.contains("\\") || value.contains("\r") || value.contains("\n") || value.contains("..")) { invalid("须为安全的站内路径"); } }
    private void url(String value, boolean required) {
        if (value == null || value.isBlank()) { if (required) { invalid("Web 入口必填"); } return; }
        try { URI uri = URI.create(value); if (!("http".equals(uri.getScheme()) || "https".equals(uri.getScheme())) || uri.getHost() == null || uri.getUserInfo() != null || uri.getFragment() != null || uri.getQuery() != null || value.length() > 512) { invalid("须为 HTTP(S) 地址，不包含账号、查询串或片段"); } }
        catch (IllegalArgumentException ex) { invalid("地址格式不合法"); }
    }
    private void invalid(String message) { throw new BizException(ErrorCode.VALIDATION_FAILED, message); }

    /** 应用完整表单，兼容旧四字段调用。 */
    public record RegisterAppCommand(String appCode, String name, String baseUrl, String repositoryUrl, String description,
            String type, String logo, Long ownerEmployeeId, String status, String adminUrl, String apiUrl, String ssoCallbackPath) {
        public RegisterAppCommand(String appCode, String name, String baseUrl, String repositoryUrl) { this(appCode, name, baseUrl, repositoryUrl, null, "WEB", null, null, "ACTIVE", null, null, "/sso/callback"); }
    }
    /** 元数据上报表单。 */
    public record MetadataReportCommand(String version, String gitCommit, String environment, String baseUrl, List<RouteMetadata> routes) { }
    /** 单条路由定义。 */
    public record RouteMetadata(String routeCode, String name, String path, String permissionCode, String icon, String openType, boolean visible, int sortOrder, String entry, String parentRouteCode, String category) {
        public RouteMetadata { entry = entry == null ? "WEB" : entry; category = category == null || category.isBlank() ? "常用功能" : category.trim(); parentRouteCode = parentRouteCode == null || parentRouteCode.isBlank() ? null : parentRouteCode; }
        public RouteMetadata(String routeCode, String name, String path, String permissionCode, String icon, String openType, boolean visible, int sortOrder) { this(routeCode, name, path, permissionCode, icon, openType, visible, sortOrder, "WEB", null, "常用功能"); }
    }
    /** 一次性创建结果。 */
    public record AppRegistration(AppView app, String ciToken, String clientSecret) { }
    /** 路由版本及审核和灰度状态。 */
    public record MetadataRelease(String id, String appCode, String version, String gitCommit, String environment, String baseUrl,
            List<RouteMetadata> routes, boolean published, Instant reportedAt, String approvalStatus, int rolloutPercent) { }
    /** 无凭证的应用视图。 */
    public record AppView(String appCode, String name, String baseUrl, String repositoryUrl, MetadataRelease release,
            String description, String type, String logo, Long ownerEmployeeId, String status, String adminUrl, String apiUrl, String ssoCallbackPath,boolean initialDeliveryPending) { }
    /** 当前用户可见菜单。 */
    public record MenuView(String appCode, String appName, String baseUrl, RouteMetadata route) { }
    /** 交换后的短期身份，目标应用建立自身会话。 */
    public record SsoExchange(String subjectId, String appCode, String redirectPath, Instant expiresAt, String entry, List<String> permissions) { }
}
