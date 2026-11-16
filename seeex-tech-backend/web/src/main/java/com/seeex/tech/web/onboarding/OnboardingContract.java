package com.seeex.tech.web.onboarding;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/** 前后端共享含义的接入业务字段；所有身份由服务端派生。 */
public final class OnboardingContract {
    private OnboardingContract() { }
    /** 候选来源只能为固定员工、角色或申请部门负责人。 */
    public record Selector(String type,List<Long> employeeIds,List<Long> roleIds,List<Long> fallbackEmployeeIds) { }
    /** 条件节点只使用环境、数据级别和应用类型的枚举集合，空集合表示不限。 */
    public record ApprovalNode(String key,String name,String mode,Selector reviewers,Set<String> environments,Set<String> classifications,Set<String> appTypes,Integer dueHours,Boolean securityReview) { }
    /** 受限顺序审批模型；办理与本人交付为不可删除的业务终点。 */
    public record FlowModel(List<ApprovalNode> nodes,Selector fulfillers,Selector operators) { }
    /** 已确认的申请字段。附件以本人平台文件objectKey引用，不接受任意所有者。 */
    public record ApplicationForm(String name,String proposedAppCode,String description,String businessPurpose,String type,Set<String> requestedEntries,String environment,List<String> requestedDomains,List<Long> requestedAdminEmployeeIds,String dataClassification,List<String> attachmentKeys) {
        public ApplicationForm { name=name==null?"":name;description=description==null?"":description;businessPurpose=businessPurpose==null?"":businessPurpose; }
    }
    /** 办理只填写批准范围内的部署配置，不能改申请人或管理员。 */
    public record FulfillmentForm(String baseUrl,String adminUrl,String apiUrl,String repositoryUrl,String logo,String ssoCallbackPath,String integrationNotes) {
        public FulfillmentForm { baseUrl=baseUrl==null?"":baseUrl;adminUrl=adminUrl==null?"":adminUrl;apiUrl=apiUrl==null?"":apiUrl;repositoryUrl=repositoryUrl==null?"":repositoryUrl;logo=logo==null?"":logo;ssoCallbackPath=ssoCallbackPath==null?"":ssoCallbackPath;integrationNotes=integrationNotes==null?"":integrationNotes; }
    }
    /** 版本动作；补发必须显式确认轮换。 */
    public record ActionCommand(Long expectedVersion,String comment,String decision,Boolean confirmRotation,Long definitionId,Long deliveryId) { }
    /** 草稿更新。 */
    public record DraftCommand(ApplicationForm form,Long expectedVersion) { }
    /** 办理配置更新。 */
    public record FulfillmentCommand(FulfillmentForm form,Long expectedVersion) { }
    /** 流程保存，新建版本时sourceId指定复制源。 */
    public record DefinitionCommand(String definitionKey,String name,FlowModel model,Long expectedVersion,Long sourceId) { }
    /** 发布使用单独业务绑定集合版本，避免不同流程并发覆盖绑定。 */
    public record PublishCommand(Long expectedVersion,Long expectedBindingVersion,Set<String> environments,String comment) { }
    /** 分页查询。scope=MINE/MANAGE/FULFILL，category=PENDING/DONE。 */
    public record ListQuery(Integer page,Integer pageSize,String state,String keyword,String from,String to,String scope,String category) {
        public ListQuery { page=page==null?1:Math.max(1,page); pageSize=pageSize==null?20:Math.min(100,Math.max(1,pageSize)); }
    }
    /** 页结果。 */
    public record PageView<T>(List<T> items,long total) { }
    /** 仅含必要名称的远程选择项。 */
    public record Option(Long id,String name) { }
    /** 申请行。 */
    public record RequestItem(Long id,String requestNo,String name,String appCode,Long applicantId,String applicantName,String state,String decision,Long rowVersion,Integer attemptNo,LocalDateTime createdAt,LocalDateTime submittedAt) { }
    /** 包含服务端允许动作的申请详情。 */
    public record RequestView(RequestItem item,ApplicationForm form,List<Option> admins,FulfillmentForm fulfillment,Long fulfillerId,String fulfillerName,String exceptionMessage,Long definitionId,FlowModel flow,Integer nodeIndex,List<TaskView> tasks,List<HistoryView> history,List<RevisionView> revisions,Set<String> allowedActions) { }
    /** 历次审批图与意见只读，历史任务不返回任何可执行动作。 */
    public record AttemptView(Integer attemptNo,ApplicationForm form,FlowModel flow,List<TaskView> tasks,List<HistoryView> history) { }
    /** 不变修订，不含任何交付凭证。 */
    public record RevisionView(Integer revision,Long definitionId,ApplicationForm form,LocalDateTime submittedAt) { }
    /** 待办视图。 */
    public record TaskView(Long id,Long requestId,String requestNo,String applicationName,Integer attemptNo,Integer nodeIndex,String nodeName,String mode,String state,Long rowVersion,Long candidateId,String candidateName,LocalDateTime dueAt,Set<String> allowedActions) { }
    /** 只读动作历史。 */
    public record HistoryView(Long id,Long actorId,String actorName,String action,String comment,Integer attemptNo,LocalDateTime createdAt) { }
    /** 预检结果固定表单版本、流程与候选人，提交再次校验。 */
    public record ValidationView(Long requestVersion,Long definitionId,String definitionName,Integer definitionVersion,List<NodePreview> nodes,List<Option> fulfillers,List<Option> operators) { }
    /** 节点预览，不启动流程。 */
    public record NodePreview(String key,String name,String mode,boolean matched,List<Option> candidates) { }
    /** 流程视图。 */
    public record DefinitionView(Long id,String definitionKey,String name,Integer version,String status,Long rowVersion,FlowModel model,Set<String> environments,LocalDateTime publishedAt,String checksum) { }
    /** 配置页连同绑定集合版本读取。 */
    public record DefinitionPage(List<DefinitionView> items,long total,long bindingVersion,List<BindingView> bindings) { }
    /** 发布前展示被替换的环境绑定，非当前页版本也可识别。 */
    public record BindingView(String environment,Long definitionId,String name,Integer version) { }
    /** 定向交付普通信息，密文和摘要从不出现在此视图。 */
    public record DeliveryView(Long id,Long requestId,Integer deliveryVersion,Long rowVersion,FulfillmentForm config,String appCode,String state,LocalDateTime expiresAt,LocalDateTime redeemedAt,LocalDateTime acknowledgedAt,Set<String> allowedActions) { }
    /** POST一次性领取结果，禁止查询缓存、日志或持久化到浏览器。 */
    public record SecretView(String ciToken,String clientSecret) { }
    /** 站内消息。 */
    public record MessageView(Long id,Long requestId,String title,String content,LocalDateTime readAt,Long rowVersion,LocalDateTime createdAt) { }
    /** 外部通知运行状态，无原始令牌。 */
    public record NoticeView(Long id,Long requestId,Long recipientId,String state,Integer attempts,String lastError,Long rowVersion,LocalDateTime nextAttemptAt) { }
}
