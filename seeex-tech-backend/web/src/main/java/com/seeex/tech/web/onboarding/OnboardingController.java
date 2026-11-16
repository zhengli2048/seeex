package com.seeex.tech.web.onboarding;

import com.seeex.tech.common.api.ApiResponse;
import com.seeex.tech.common.api.ErrorCode;
import com.seeex.tech.common.exception.BizException;
import com.seeex.tech.web.audit.AuditLog;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.*;
import static com.seeex.tech.web.onboarding.OnboardingContract.*;

/** 应用接入页面API；只接收字段、派生身份、鉴权编排和返回视图。 */
@RestController @RequestMapping("/api") @RequiredArgsConstructor
public class OnboardingController {
    private final OnboardingDirectory directory;
    private final OnboardingService service;
    private final OnboardingDeliveryService deliveries;
    private final OnboardingAttachmentService attachments;
    private final OnboardingNotificationService notifications;
    /** 页面上下文，无令牌或员工隐私字段。 */
    @GetMapping("/onboarding/context")
    public ApiResponse<Map<String,Object>> context(Authentication auth) { long actor=directory.actor(auth); any(actor); return ApiResponse.ok(Map.of("actorId",actor,"name",directory.active(actor).getDisplayName(),"deliveryConfigured",deliveries.configured(),"notificationsEnabled",notifications.enabled())); }
    /** 名称字典按功能来源授权。 */
    @GetMapping("/onboarding/options")
    public ApiResponse<List<Option>> options(@RequestParam(defaultValue="EMPLOYEE") String kind,@RequestParam(required=false) String keyword,@RequestParam(required=false) List<Long> selected,Authentication auth) { long actor=directory.actor(auth); if("ROLE".equals(kind)) { if(!directory.can(actor,"workflow:definitions:view") && !directory.can(actor,"workflow:definitions:write"))throw new BizException(ErrorCode.FORBIDDEN); } else any(actor); return ApiResponse.ok(directory.options(kind,keyword,selected)); }
    /** 本人/平台管理申请列表。 */
    @GetMapping("/app-requests")
    public ApiResponse<PageView<RequestItem>> list(@ModelAttribute ListQuery query,Authentication auth) { return ApiResponse.ok(service.list(directory.actor(auth),query)); }
    /** 本人列表强制本人范围，忽略浏览器提供的scope。 */
    @GetMapping("/me/app-requests")
    public ApiResponse<PageView<RequestItem>> mine(@ModelAttribute ListQuery query,Authentication auth) { return ApiResponse.ok(service.list(directory.actor(auth),new ListQuery(query.page(),query.pageSize(),query.state(),query.keyword(),query.from(),query.to(),"MINE",null))); }
    /** 保存新申请，身份只来自会话。 */
    @PostMapping("/app-requests") @AuditLog(action="CREATE_APP_REQUEST",resourceType="ONBOARDING")
    public ApiResponse<RequestView> create(@RequestBody ApplicationForm form,@RequestHeader("Idempotency-Key") String key,Authentication auth) { long actor=directory.actor(auth); directory.require(actor,"app-requests:create"); attachments.validateNew(actor,form); return ApiResponse.ok(service.create(actor,form,key)); }
    /** 同一ACL返回详情和只读流程图。 */
    @GetMapping({"/app-requests/{id}","/workflow/instances/{id}/diagram"})
    public ApiResponse<RequestView> detail(@PathVariable long id,Authentication auth) { return ApiResponse.ok(service.detail(directory.actor(auth),id)); }
    /** 只读查看历史尝试的原流程、原材料与意见。 */
    @GetMapping("/app-requests/{id}/attempts/{attempt}")
    public ApiResponse<AttemptView> attempt(@PathVariable long id,@PathVariable int attempt,Authentication auth) { return ApiResponse.ok(service.attempt(directory.actor(auth),id,attempt)); }
    /** 编辑本人草稿。 */
    @PutMapping("/app-requests/{id}/draft") @AuditLog(action="SAVE_APP_REQUEST",resourceType="ONBOARDING")
    public ApiResponse<RequestView> draft(@PathVariable long id,@RequestBody DraftCommand command,Authentication auth) { long actor=directory.actor(auth); directory.require(actor,"app-requests:create"); attachments.validateNew(actor,command.form()); return ApiResponse.ok(service.draft(actor,id,command)); }
    /** 预检不启动真实流程。 */
    @PostMapping("/app-requests/{id}/validate")
    public ApiResponse<ValidationView> validate(@PathVariable long id,@RequestBody ActionCommand command,Authentication auth) { long actor=directory.actor(auth); attachments.validateSubmit(actor,id,command.expectedVersion()); return ApiResponse.ok(service.validate(actor,id,command)); }
    /** 提交时固定用户确认的流程版本。 */
    @PostMapping("/app-requests/{id}/submit") @AuditLog(action="SUBMIT_APP_REQUEST",resourceType="ONBOARDING")
    public ApiResponse<RequestView> submit(@PathVariable long id,@RequestBody ActionCommand command,@RequestHeader("Idempotency-Key") String key,Authentication auth) { long actor=directory.actor(auth); if(service.needsSubmitValidation(actor,id,command,key))attachments.validateSubmit(actor,id,command.expectedVersion()); return ApiResponse.ok(service.submit(actor,id,command,key)); }
    /** 撤回未决申请。 */
    @PostMapping("/app-requests/{id}/withdraw") @AuditLog(action="WITHDRAW_APP_REQUEST",resourceType="ONBOARDING")
    public ApiResponse<RequestView> withdraw(@PathVariable long id,@RequestBody ActionCommand command,Authentication auth) { return ApiResponse.ok(service.withdraw(directory.actor(auth),id,command)); }
    /** 删除从未提交的本人草稿。 */
    @DeleteMapping("/app-requests/{id}/draft") @AuditLog(action="DELETE_APP_REQUEST",resourceType="ONBOARDING")
    public ApiResponse<Void> delete(@PathVariable long id,@RequestParam Long expectedVersion,Authentication auth) { service.deleteDraft(directory.actor(auth),id,expectedVersion); return ApiResponse.ok(null); }
    /** 本人候选/已办队列。 */
    @GetMapping("/me/approval-tasks")
    public ApiResponse<PageView<TaskView>> tasks(@ModelAttribute ListQuery query,Authentication auth) { return ApiResponse.ok(service.tasks(directory.actor(auth),query)); }
    /** 领取当前本人审批任务。 */
    @PostMapping("/approval-tasks/{id}/claim") @AuditLog(action="CLAIM_APPROVAL_TASK",resourceType="ONBOARDING")
    public ApiResponse<RequestView> claim(@PathVariable long id,@RequestBody ActionCommand command,Authentication auth) { return ApiResponse.ok(service.claimTask(directory.actor(auth),id,command)); }
    /** 通过前再次检查材料；退回或驳回不依赖已失效附件。 */
    @PostMapping("/approval-tasks/{id}/decision") @AuditLog(action="DECIDE_APPROVAL_TASK",resourceType="ONBOARDING")
    public ApiResponse<RequestView> decision(@PathVariable long id,@RequestBody ActionCommand command,Authentication auth) { long actor=directory.actor(auth); if("APPROVE".equals(command.decision()))attachments.validateBound(actor,attachments.taskRequest(actor,id)); return ApiResponse.ok(service.decision(actor,id,command)); }
    /** 平台办理列表仍按本人参与关系过滤。 */
    @GetMapping("/application-fulfillments")
    public ApiResponse<PageView<RequestItem>> fulfillments(@ModelAttribute ListQuery query,Authentication auth) { return ApiResponse.ok(service.list(directory.actor(auth),new ListQuery(query.page(),query.pageSize(),query.state(),query.keyword(),query.from(),query.to(),"FULFILL",null))); }
    /** 领取办理。 */
    @PostMapping("/application-fulfillments/{id}/claim") @AuditLog(action="CLAIM_APP_FULFILLMENT",resourceType="ONBOARDING")
    public ApiResponse<RequestView> claimFulfillment(@PathVariable long id,@RequestBody ActionCommand command,Authentication auth) { return ApiResponse.ok(service.claimFulfillment(directory.actor(auth),id,command)); }
    /** 保存部署配置草稿。 */
    @PutMapping("/application-fulfillments/{id}/config") @AuditLog(action="SAVE_APP_FULFILLMENT",resourceType="ONBOARDING")
    public ApiResponse<RequestView> config(@PathVariable long id,@RequestBody FulfillmentCommand command,Authentication auth) { return ApiResponse.ok(service.config(directory.actor(auth),id,command)); }
    /** 配置与批准域名、入口一致性校验。 */
    @PostMapping("/application-fulfillments/{id}/validate")
    public ApiResponse<RequestView> validateFulfillment(@PathVariable long id,@RequestBody ActionCommand command,Authentication auth) { long actor=directory.actor(auth); attachments.validateBound(actor,id); return ApiResponse.ok(service.validateFulfillment(actor,id,command)); }
    /** 原子完成注册并加密交付，响应无明文凭证。 */
    @PostMapping("/application-fulfillments/{id}/complete") @AuditLog(action="COMPLETE_APP_FULFILLMENT",resourceType="ONBOARDING")
    public ApiResponse<RequestView> complete(@PathVariable long id,@RequestBody ActionCommand command,@RequestHeader("Idempotency-Key") String key,Authentication auth) { long actor=directory.actor(auth); if(service.needsCompletionValidation(actor,id,command,key))attachments.validateBound(actor,id); return ApiResponse.ok(service.complete(actor,id,command,key)); }
    /** 平台运维取消未注册申请，原因审计并释放该申请编码。 */
    @PostMapping("/application-fulfillments/{id}/cancel") @AuditLog(action="CANCEL_APP_FULFILLMENT",resourceType="ONBOARDING")
    public ApiResponse<RequestView> cancel(@PathVariable long id,@RequestBody ActionCommand command,Authentication auth) { return ApiResponse.ok(service.cancel(directory.actor(auth),id,command)); }
    /** 异常恢复不得直接批准。 */
    @PostMapping("/app-requests/{id}/recover") @AuditLog(action="RECOVER_APP_REQUEST",resourceType="ONBOARDING")
    public ApiResponse<RequestView> recover(@PathVariable long id,@RequestBody ActionCommand command,Authentication auth) { return ApiResponse.ok(service.recover(directory.actor(auth),id,command)); }
    /** 原收件人的普通交付信息。 */
    @GetMapping("/me/app-deliveries/{id}")
    public ResponseEntity<ApiResponse<DeliveryView>> delivery(@PathVariable long id,Authentication auth) { return ResponseEntity.ok().header("Cache-Control","no-store").body(ApiResponse.ok(deliveries.view(directory.actor(auth),id))); }
    /** 首次明文响应完全跳过审计参数和结果序列化。 */
    @PostMapping("/me/app-deliveries/{id}/redeem") @AuditLog(action="REDEEM_APP_DELIVERY",resourceType="AUTH")
    public ResponseEntity<ApiResponse<SecretView>> redeem(@PathVariable long id,@RequestBody ActionCommand command,@RequestHeader("Idempotency-Key") String key,Authentication auth) { return ResponseEntity.ok().header("Cache-Control","no-store, private").header("Pragma","no-cache").body(ApiResponse.ok(deliveries.redeem(directory.actor(auth),id,command,key))); }
    /** 确认收件。 */
    @PostMapping("/me/app-deliveries/{id}/acknowledge") @AuditLog(action="ACK_APP_DELIVERY",resourceType="ONBOARDING")
    public ApiResponse<DeliveryView> acknowledge(@PathVariable long id,@RequestBody ActionCommand command,Authentication auth) { return ApiResponse.ok(deliveries.acknowledge(directory.actor(auth),id,command)); }
    /** 补发只返回成功状态，明文仍只给原申请人。 */
    @PostMapping("/application-fulfillments/{id}/reissue") @AuditLog(action="REISSUE_APP_DELIVERY",resourceType="ONBOARDING")
    public ApiResponse<Void> reissue(@PathVariable long id,@RequestBody ActionCommand command,@RequestHeader("Idempotency-Key") String key,Authentication auth) { deliveries.reissue(directory.actor(auth),id,command,key); return ApiResponse.ok(null); }
    /** 当前绑定附件最小信息。 */
    @GetMapping("/app-requests/{id}/attachments")
    public ApiResponse<List<OnboardingAttachmentService.Attachment>> attachments(@PathVariable long id,Authentication auth) { return ApiResponse.ok(attachments.list(directory.actor(auth),id)); }
    /** 参与者受控下载申请附件，不接受浏览器自报所有者。 */
    @GetMapping("/app-requests/{id}/attachments/download") @AuditLog(action="READ_APP_ATTACHMENT",resourceType="AUTH")
    public ResponseEntity<ApiResponse<String>> download(@PathVariable long id,@RequestParam String objectKey,Authentication auth) { return ResponseEntity.ok().header("Cache-Control","no-store").body(ApiResponse.ok(attachments.download(directory.actor(auth),id,objectKey))); }
    private void any(long actor) { if(!List.of("app-requests:own:view","app-requests:create","app-requests:manage","workflow:tasks:view","workflow:definitions:view","workflow:definitions:write","app-fulfillments:view","notifications:own:view","notifications:operate").stream().anyMatch(code->directory.can(actor,code)))throw new BizException(ErrorCode.FORBIDDEN); }
}
