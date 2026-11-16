package com.seeex.tech.web.wecom;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.seeex.tech.common.api.ApiResponse;
import com.seeex.tech.common.api.ErrorCode;
import com.seeex.tech.common.exception.BizException;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import java.io.ByteArrayInputStream;

@RestController
@RequestMapping("/api")
public class WeComController {
    private final WeComSyncService syncService;
    private final SyncRunStateService runStates;
    private final WeComClient client;
    private final SyncEventMapper eventMapper;
    private final WeComSettingsService settings;
    private final SyncRunMapper runs;
    private final com.seeex.tech.web.auth.ManagementAuthorizationService authorization;
    private final WeComCallbackCodec callbackCodec;

    public WeComController(WeComSyncService syncService, SyncRunStateService runStates, WeComClient client,
                            SyncEventMapper eventMapper, WeComCallbackCodec callbackCodec, WeComSettingsService settings, SyncRunMapper runs, com.seeex.tech.web.auth.ManagementAuthorizationService authorization) {
        this.syncService = syncService; this.runStates=runStates; this.client = client; this.eventMapper = eventMapper;
        this.callbackCodec = callbackCodec; this.settings = settings; this.runs = runs; this.authorization = authorization;
    }
    public record EventRequest(String eventType, String eventId, String payload) {}
    public record EventResponse(String eventNo, String status) {}
    public record SyncRunResponse(String runId, String status, int userCount, String error, int failedCount) {}

    /** 读取脱敏配置。 */
    @GetMapping("/wecom/config")
    public ApiResponse<WeComSettingsService.ConfigView> config(org.springframework.security.core.Authentication authentication) {
        authorization.require(authentication, "wecom:config:view"); return ApiResponse.ok(settings.view());
    }
    /** 保存配置，不回显密钥。 */
    @PutMapping("/wecom/config")
    @com.seeex.tech.web.audit.AuditLog(action = "WECOM_CONFIG", resourceType = "WECOM")
    public ApiResponse<WeComSettingsService.ConfigView> save(@RequestBody WeComSettingsService.Settings command, org.springframework.security.core.Authentication authentication) {
        authorization.require(authentication, "wecom:config:update"); var value = settings.save(command); client.clearToken(); return ApiResponse.ok(value);
    }
    /** 真实检查企业微信令牌获取。 */
    @PostMapping("/wecom/config/check")
    public ApiResponse<ConnectionResponse> check(org.springframework.security.core.Authentication authentication) {
        authorization.require(authentication, "wecom:config:update"); client.clearToken(); client.getToken(); return ApiResponse.ok(new ConnectionResponse(true));
    }
    /** 同步批次分页。 */
    @GetMapping("/integrations/wecom/sync/runs")
    public ApiResponse<PageResult<SyncRunResponse>> runs(@RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int pageSize, org.springframework.security.core.Authentication authentication) {
        authorization.require(authentication, "wecom:sync:view");
        var result = runs.selectPage(new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(Math.max(1, page), Math.min(100, Math.max(1, pageSize))), new LambdaQueryWrapper<SyncRunDO>().orderByDesc(SyncRunDO::getId));
        return ApiResponse.ok(new PageResult<>(result.getRecords().stream().map(row -> new SyncRunResponse(row.getRunNo(), row.getStatus(), row.getUserCount() == null ? 0 : row.getUserCount(), row.getErrorMessage(), row.getFailedCount() == null ? 0 : row.getFailedCount())).toList(), result.getTotal()));
    }
    /** 事件列表不返回原文。 */
    @GetMapping(value = "/integrations/wecom/events", params = "!msg_signature")
    public ApiResponse<PageResult<EventItem>> events(@RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int pageSize, @RequestParam(required = false) String status, org.springframework.security.core.Authentication authentication) {
        authorization.require(authentication, "wecom:events:view");
        var result = eventMapper.selectPage(new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(Math.max(1, page), Math.min(100, Math.max(1, pageSize))), new LambdaQueryWrapper<SyncEventDO>().eq(status != null && !status.isBlank(), SyncEventDO::getStatus, status).orderByDesc(SyncEventDO::getId));
        return ApiResponse.ok(new PageResult<>(result.getRecords().stream().map(row -> new EventItem(row.getId(), row.getEventNo(), row.getEventType(), row.getStatus(), row.getRetryCount(), row.getReceivedAt(), row.getErrorMessage())).toList(), result.getTotal()));
    }
    /** 连接检查结果。 */
    public record ConnectionResponse(boolean ok) { }
    /** 分页结果。 */
    public record PageResult<T>(java.util.List<T> items, long total) { }
    /** 不含原文的事件条目。 */
    public record EventItem(Long id, String eventNo, String eventType, String status, Integer retryCount, LocalDateTime receivedAt, String errorMessage) { }

    @GetMapping(value = "/integrations/wecom/events", params = "msg_signature", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> verifyEvent(@RequestParam(name = "msg_signature") String msgSignature, @RequestParam String timestamp,
                                              @RequestParam String nonce, @RequestParam String echostr) {
        return ResponseEntity.ok(callbackCodec.decryptEcho(msgSignature, timestamp, nonce, echostr));
    }

    @PostMapping(value = "/integrations/wecom/events", consumes = MediaType.APPLICATION_XML_VALUE,
            produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> encryptedEvent(@RequestParam(name = "msg_signature") String msgSignature, @RequestParam String timestamp,
                                                 @RequestParam String nonce, @RequestBody String body) {
        String encrypted = xmlValue(body, "Encrypt");
        String plain = callbackCodec.decrypt(msgSignature, timestamp, nonce, encrypted);
        String eventType = xmlValue(plain, "Event");
        String eventId = xmlValue(plain, "EventID");
        if (eventId.isBlank()) eventId = HexFormat.of().formatHex(hash(plain));
        event(new EventRequest(eventType.isBlank() ? "UNKNOWN" : eventType, eventId, plain));
        return ResponseEntity.ok("success");
    }

    public ApiResponse<EventResponse> event(@RequestBody EventRequest request) {
        if (request.eventType() == null || request.eventId() == null || request.payload() == null) {
            throw new BizException(ErrorCode.VALIDATION_FAILED, "eventType, eventId and payload are required");
        }
        String eventNo = HexFormat.of().formatHex(hash(request.eventId())).substring(0, 32);
        SyncEventDO existing = eventMapper.selectOne(new LambdaQueryWrapper<SyncEventDO>().eq(SyncEventDO::getEventNo, eventNo));
        if (existing != null) return ApiResponse.ok(new EventResponse(eventNo, existing.getStatus()));
        SyncEventDO event = new SyncEventDO();
        event.setEventNo(eventNo); event.setProvider("WECOM"); event.setEventType(request.eventType());
        event.setPayloadHash(HexFormat.of().formatHex(hash(request.payload()))); event.setStatus("RECEIVED");
        event.setPayload(request.payload()); event.setRetryCount(0); event.setReceivedAt(LocalDateTime.now());
        try { eventMapper.insert(event); } catch (org.springframework.dao.DuplicateKeyException ex) { return ApiResponse.ok(new EventResponse(eventNo, "RECEIVED")); }
        return ApiResponse.ok(new EventResponse(eventNo, "RECEIVED"));
    }

    @PostMapping("/integrations/wecom/sync/full")
    public ApiResponse<SyncRunResponse> fullSync(org.springframework.security.core.Authentication authentication) {
        authorization.require(authentication, "wecom:sync:run");
        WeComSyncService.SyncStatus status = syncService.startFullSync();
        return ApiResponse.ok(new SyncRunResponse(status.runId(), status.status(), status.userCount(), status.error(), status.failedCount()));
    }

    @GetMapping("/integrations/wecom/sync/runs/{runId}")
    public ApiResponse<SyncRunResponse> run(@PathVariable String runId, org.springframework.security.core.Authentication authentication) {
        authorization.require(authentication, "wecom:sync:view");
        WeComSyncService.SyncStatus status = syncService.status(runId);
        return ApiResponse.ok(new SyncRunResponse(status.runId(), status.status(), status.userCount(), status.error(), status.failedCount()));
    }

    /** 查看历次尝试的处理明细，不返回执行令牌。 */
    @GetMapping("/integrations/wecom/sync/runs/{runId}/items")
    public ApiResponse<SyncRunStateService.ItemPage> runItems(@PathVariable String runId,@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int pageSize,org.springframework.security.core.Authentication auth) {
        authorization.require(auth,"wecom:sync:view"); return ApiResponse.ok(runStates.details(runId,page,pageSize));
    }
    /** 重试失败批次，保留原尝试历史，禁止重试仍在运行的任务。 */
    @PostMapping("/integrations/wecom/sync/runs/{runId}/retry")
    @com.seeex.tech.web.audit.AuditLog(action="RETRY_SYNC_RUN",resourceType="WECOM")
    public ApiResponse<Void> retryRun(@PathVariable String runId,org.springframework.security.core.Authentication auth) {
        authorization.require(auth,"wecom:sync:run"); runStates.retry(runId); return ApiResponse.ok(null);
    }

    /** 重试失败或死信事件。 */
    @PostMapping("/integrations/wecom/events/{id}/retry")
    @com.seeex.tech.web.audit.AuditLog(action = "RETRY_WECOM_EVENT", resourceType = "WECOM")
    public ApiResponse<EventResponse> retryEvent(@PathVariable long id, org.springframework.security.core.Authentication authentication) {
        authorization.require(authentication, "wecom:events:retry");
        SyncEventDO event = eventMapper.selectById(id);
        if (event == null) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND, "event not found");
        }
        if (!java.util.List.of("RETRY_WAIT", "DEAD_LETTER").contains(event.getStatus())) {
            throw new BizException(ErrorCode.CONFLICT, "only failed events can be retried");
        }
        event.setRetryCount(0);
        event.setStatus("RECEIVED");
        eventMapper.updateById(event);
        return ApiResponse.ok(new EventResponse(event.getEventNo(), "RECEIVED"));
    }

    /** 受控查看原始事件（不加密，普通前端无入口，调用需审计）。 */
    @GetMapping("/integrations/wecom/events/{id}/raw")
    @com.seeex.tech.web.audit.AuditLog(action = "READ_WECOM_RAW", resourceType = "WECOM")
    public ApiResponse<String> rawEvent(@PathVariable long id, org.springframework.security.core.Authentication authentication) {
        authorization.require(authentication, "wecom:events:raw");
        SyncEventDO event = eventMapper.selectById(id);
        if (event == null) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND, "event not found");
        }
        return ApiResponse.ok(event.getPayload());
    }

    private byte[] hash(String value) {
        try { return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)); }
        catch (java.security.NoSuchAlgorithmException ex) { throw new IllegalStateException("hash unavailable", ex); }
    }

    private String xmlValue(String xml, String name) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            Document document = factory.newDocumentBuilder()
                    .parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
            var nodes = document.getElementsByTagName(name);
            return nodes.getLength() == 0 ? "" : nodes.item(0).getTextContent();
        } catch (Exception ex) {
            throw new BizException(ErrorCode.VALIDATION_FAILED, "invalid WeCom callback XML");
        }
    }
}
