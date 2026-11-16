package com.seeex.tech.web.onboarding;
import com.seeex.tech.common.api.ApiResponse;
import com.seeex.tech.web.audit.AuditLog;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import static com.seeex.tech.web.onboarding.OnboardingContract.*;
/** 流程版本与通知接口，读写发布权限分开。 */
@RestController @RequestMapping("/api") @RequiredArgsConstructor
public class OnboardingAdministrationController {
    private final OnboardingDirectory directory;
    private final OnboardingDefinitionService definitions;
    private final OnboardingNotificationService notifications;
    /** 流程分页及绑定版本。 */
    @GetMapping("/workflow/definitions") public ApiResponse<DefinitionPage> definitions(@ModelAttribute ListQuery query,Authentication auth) { return ApiResponse.ok(definitions.list(directory.actor(auth),query)); }
    /** 读取固定流程版本以比较发布差异。 */
    @GetMapping("/workflow/definitions/{id}") public ApiResponse<DefinitionView> detail(@PathVariable long id,Authentication auth) { return ApiResponse.ok(definitions.detail(directory.actor(auth),id)); }
    /** 新建或复制新草稿版本。 */
    @PostMapping("/workflow/definitions") @AuditLog(action="CREATE_WORKFLOW",resourceType="ONBOARDING")
    public ApiResponse<DefinitionView> create(@RequestBody DefinitionCommand command,@RequestHeader("Idempotency-Key") String key,Authentication auth) { return ApiResponse.ok(definitions.save(directory.actor(auth),null,command,key)); }
    /** 保存草稿；已发布版本拒绝修改。 */
    @PutMapping("/workflow/definitions/{id}/draft") @AuditLog(action="SAVE_WORKFLOW",resourceType="ONBOARDING")
    public ApiResponse<DefinitionView> save(@PathVariable long id,@RequestBody DefinitionCommand command,Authentication auth) { return ApiResponse.ok(definitions.save(directory.actor(auth),id,command,null)); }
    /** 检查已保存版本。 */
    @PostMapping("/workflow/definitions/{id}/validate") public ApiResponse<DefinitionView> validate(@PathVariable long id,@RequestBody ActionCommand command,Authentication auth) { return ApiResponse.ok(definitions.validate(directory.actor(auth),id,command)); }
    /** 只读模拟，不能伪造审批人或实例变量。 */
    @PostMapping("/workflow/definitions/{id}/preview") public ApiResponse<ValidationView> preview(@PathVariable long id,@RequestBody ApplicationForm form,Authentication auth) { return ApiResponse.ok(definitions.preview(directory.actor(auth),id,form)); }
    /** 发布并替换确认过的环境绑定。 */
    @PostMapping("/workflow/definitions/{id}/publish") @AuditLog(action="PUBLISH_WORKFLOW",resourceType="ONBOARDING")
    public ApiResponse<DefinitionView> publish(@PathVariable long id,@RequestBody PublishCommand command,Authentication auth) { return ApiResponse.ok(definitions.publish(directory.actor(auth),id,command)); }
    /** 停用新实例入口，不影响旧实例。 */
    @PostMapping("/workflow/definitions/{id}/disable") @AuditLog(action="DISABLE_WORKFLOW",resourceType="ONBOARDING")
    public ApiResponse<DefinitionView> disable(@PathVariable long id,@RequestBody PublishCommand command,Authentication auth) { return ApiResponse.ok(definitions.disable(directory.actor(auth),id,command)); }
    /** 本人通知。 */
    @GetMapping("/me/inbox") public ApiResponse<PageView<MessageView>> inbox(@ModelAttribute ListQuery query,Authentication auth) { return ApiResponse.ok(notifications.inbox(directory.actor(auth),query)); }
    /** 本人标记已读。 */
    @PostMapping("/me/inbox/{id}/read") public ApiResponse<Void> read(@PathVariable long id,@RequestBody ActionCommand command,Authentication auth) { notifications.read(directory.actor(auth),id,command); return ApiResponse.ok(null); }
    /** 外部渠道发送状态。 */
    @GetMapping("/notification-jobs") public ApiResponse<PageView<NoticeView>> jobs(@ModelAttribute ListQuery query,Authentication auth) { return ApiResponse.ok(notifications.jobs(directory.actor(auth),query)); }
    /** 运维重试原通知，原因进入审计。 */
    @PostMapping("/notification-jobs/{id}/retry") @AuditLog(action="RETRY_ONBOARDING_NOTICE",resourceType="ONBOARDING")
    public ApiResponse<Void> retry(@PathVariable long id,@RequestBody ActionCommand command,Authentication auth) { notifications.retry(directory.actor(auth),id,command); return ApiResponse.ok(null); }
}
