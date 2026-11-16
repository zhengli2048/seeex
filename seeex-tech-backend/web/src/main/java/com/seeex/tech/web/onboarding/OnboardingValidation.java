package com.seeex.tech.web.onboarding;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.net.URI;
import java.util.*;
import static com.seeex.tech.web.onboarding.OnboardingContract.*;
import static com.seeex.tech.web.onboarding.OnboardingJson.invalid;
/** 申请、部署地址与白名单流程模型的统一服务端校验。 */
@Component @RequiredArgsConstructor
public class OnboardingValidation {
    private static final int MAX_NODES=20;
    private final OnboardingDirectory directory;
    /** 草稿允许空文本；提交时严格验证全部业务字段。 */
    public void form(ApplicationForm form,long actor,boolean submitting) {
        if(form==null) throw invalid("申请表不能为空");
        text(form.name(),128,submitting,"应用名称"); text(form.description(),500,submitting,"应用描述"); text(form.businessPurpose(),2000,submitting,"业务用途");
        if(form.proposedAppCode()==null || !form.proposedAppCode().matches("[a-z][a-z0-9-]{1,63}") || "platform".equals(form.proposedAppCode())) throw invalid("应用编码须为2–64位小写字母、数字或连字符，不能为platform");
        one(form.type(),Set.of("WEB","ADMIN","AI","OTHER"),"应用类型"); one(form.environment(),Set.of("DEV","TEST","PROD"),"环境"); one(form.dataClassification(),Set.of("INTERNAL","CONFIDENTIAL"),"数据级别");
        if(form.requestedEntries()==null || form.requestedEntries().isEmpty() || form.requestedEntries().stream().anyMatch(Objects::isNull) || !Set.of("WEB","ADMIN").containsAll(form.requestedEntries()) || !form.requestedEntries().contains("WEB")) throw invalid("至少声明Web入口；后台接入同时声明ADMIN");
        if(form.requestedDomains()==null || form.requestedDomains().size()>20 || submitting && form.requestedDomains().isEmpty() || form.requestedDomains().stream().anyMatch(Objects::isNull)) throw invalid("请填写最多20个精确域名");
        Set<String> domains=new HashSet<>();
        for(String value:form.requestedDomains()) {
            if(!value.matches("[a-z0-9](?:[a-z0-9.-]{0,251}[a-z0-9])?") || value.contains("..") || !domains.add(value)) throw invalid("域名须小写、无协议/路径/端口，不允许通配或重复");
            try { if(URI.create("https://"+value).getHost()==null) throw invalid("域名无效"); } catch(IllegalArgumentException ex) { throw invalid("域名无效"); }
        }
        if(form.requestedAdminEmployeeIds()==null || form.requestedAdminEmployeeIds().isEmpty() || form.requestedAdminEmployeeIds().size()>100 || new HashSet<>(form.requestedAdminEmployeeIds()).size()!=form.requestedAdminEmployeeIds().size() || !form.requestedAdminEmployeeIds().contains(actor)) throw invalid("管理员须包含申请人、不能重复且最多100人");
        directory.validEmployees(form.requestedAdminEmployeeIds());
        if(form.attachmentKeys()==null || form.attachmentKeys().size()>10 || form.attachmentKeys().stream().anyMatch(value->value==null || value.isBlank() || value.length()>512) || new HashSet<>(form.attachmentKeys()).size()!=form.attachmentKeys().size()) throw invalid("附件最多10个且不能重复");
    }
    /** 草稿也必须具有可安全渲染的结构；人员可以暂未选完，不能保存损坏节点。 */
    public void draftModel(FlowModel model) {
        if(model==null || model.nodes()==null || model.nodes().isEmpty() || model.nodes().size()>MAX_NODES)throw invalid("审批节点须为1–20个");
        var keys=new HashSet<String>();
        for(var node:model.nodes()) {
            if(node==null || node.key()==null || !node.key().matches("[a-z][a-z0-9-]{0,63}") || !keys.add(node.key()))throw invalid("节点编码无效或重复");
            text(node.name(),128,true,"节点名称"); one(node.mode(),Set.of("ALL","ANY"),"会签规则");
            subset(node.environments(),Set.of("DEV","TEST","PROD"));subset(node.classifications(),Set.of("INTERNAL","CONFIDENTIAL"));subset(node.appTypes(),Set.of("WEB","ADMIN","AI","OTHER"));
            if(node.dueHours()==null || node.dueHours()<1 || node.dueHours()>720)throw invalid("提醒时限为1–720小时");
            selectorShape(node.reviewers());
        }
        selectorShape(model.fulfillers());selectorShape(model.operators());
    }
    private void selectorShape(Selector selector) {
        if(selector==null || !Set.of("EMPLOYEE","ROLE","DEPARTMENT_LEADER").contains(Objects.toString(selector.type(),"")))throw invalid("候选来源无效");
        for(var values:java.util.Arrays.asList(selector.employeeIds(),selector.roleIds(),selector.fallbackEmployeeIds()))if(values==null || values.size()>200 || values.stream().anyMatch(value->value==null || value<=0) || new HashSet<>(values).size()!=values.size())throw invalid("候选项必须为有效、唯一的人员/角色集合，最多200项");
    }
    /** 发布前的白名单检查，拒绝任何不支持的节点/表达式。 */
    public void model(FlowModel model) {
        draftModel(model);
        if(model==null || model.nodes()==null || model.nodes().isEmpty() || model.nodes().size()>MAX_NODES) throw invalid("审批节点须为1–20个");
        Set<String> keys=new HashSet<>(); boolean mandatory=false;
        for(var node:model.nodes()) {
            if(node==null || node.key()==null || !node.key().matches("[a-z][a-z0-9-]{0,63}") || !keys.add(node.key())) throw invalid("节点编码无效或重复");
            text(node.name(),128,true,"节点名称"); one(node.mode(),Set.of("ALL","ANY"),"会签规则"); directory.validate(node.reviewers());
            subset(node.environments(),Set.of("DEV","TEST","PROD")); subset(node.classifications(),Set.of("INTERNAL","CONFIDENTIAL")); subset(node.appTypes(),Set.of("WEB","ADMIN","AI","OTHER"));
            if(node.dueHours()==null || node.dueHours()<1 || node.dueHours()>720) throw invalid("提醒时限为1–720小时");
            if(node.environments().isEmpty() && node.classifications().isEmpty() && node.appTypes().isEmpty()) mandatory=true;
        }
        if(!mandatory) throw invalid("至少保留一个不带条件的必经审批节点");
        for(String env:Set.of("DEV","TEST","PROD")) for(String classification:Set.of("INTERNAL","CONFIDENTIAL")) for(String type:Set.of("WEB","ADMIN","AI","OTHER")) {
            boolean secure=model.nodes().stream().anyMatch(node->Boolean.TRUE.equals(node.securityReview()) && match(node,env,classification,type));
            if(!secure) throw invalid("首次接入涉及新域名与凭证，所有申请路径必须保留安全审批，不能由自报低风险跳过");
        }
        directory.validate(model.fulfillers());
        directory.validate(model.operators());
    }
    /** 固定枚举条件，不执行用户表达式。 */
    public boolean matches(ApprovalNode node,ApplicationForm form) { return match(node,form.environment(),form.dataClassification(),form.type()); }
    private boolean match(ApprovalNode node,String env,String classification,String type) { return (node.environments().isEmpty() || node.environments().contains(env)) && (node.classifications().isEmpty() || node.classifications().contains(classification)) && (node.appTypes().isEmpty() || node.appTypes().contains(type)); }
    /** 办理草稿允许暂缺必填项，但不能保存超长字段。 */
    public void fulfillmentDraft(FulfillmentForm config) {
        if(config==null)throw invalid("配置草稿不能为空");
        text(config.baseUrl(),512,false,"Web地址");text(config.adminUrl(),512,false,"后台地址");text(config.apiUrl(),512,false,"API地址");text(config.repositoryUrl(),512,false,"仓库地址");text(config.logo(),512,false,"Logo地址");text(config.ssoCallbackPath(),256,false,"SSO回调");text(config.integrationNotes(),2000,false,"接入说明");
    }
    /** 办理不能扩大审批批准的域名、端或身份；无地址时保留未完成状态。 */
    public void fulfillment(FulfillmentForm config,ApplicationForm approved) {
        fulfillmentDraft(config);
        url(config.baseUrl(),true,approved); url(config.adminUrl(),approved.requestedEntries().contains("ADMIN"),approved); url(config.apiUrl(),false,approved);
        if(!approved.requestedEntries().contains("ADMIN") && config.adminUrl()!=null && !config.adminUrl().isBlank()) throw invalid("未批准后台入口，不能在办理时新增");
        url(config.repositoryUrl(),false,approved); url(config.logo(),false,approved);
        String path=config.ssoCallbackPath();
        if(path==null || path.length()>256 || !path.startsWith("/") || path.startsWith("//") || path.contains("..") || path.contains("\\") || path.contains("\r") || path.contains("\n") || path.contains("?") || path.contains("#")) throw invalid("SSO回调须为安全站内路径，无查询串、片段或反斜杠");
        text(config.integrationNotes(),2000,false,"接入说明");
    }
    private void url(String value,boolean required,ApplicationForm form) {
        if(value==null || value.isBlank()) { if(required) throw invalid("获批入口必须填写部署地址"); return; }
        URI uri=optionalUrl(value);
        if(!form.requestedDomains().contains(uri.getHost().toLowerCase(Locale.ROOT))) throw invalid("部署地址不在已批准精确域名内，须重新申请审批");
        if("PROD".equals(form.environment()) && !"https".equals(uri.getScheme())) throw invalid("生产入口必须使用HTTPS");
    }
    private URI optionalUrl(String value) {
        if(value==null || value.isBlank()) return null;
        try { URI uri=URI.create(value); if(value.length()>512 || !Set.of("http","https").contains(Objects.toString(uri.getScheme(),"")) || uri.getHost()==null || uri.getUserInfo()!=null || uri.getQuery()!=null || uri.getFragment()!=null) throw invalid("地址须为HTTP(S)，不能含账号、查询串或片段"); return uri; }
        catch(IllegalArgumentException ex) { throw invalid("地址无效"); }
    }
    private void subset(Set<String> values,Set<String> allowed) { if(values==null || values.stream().anyMatch(Objects::isNull) || !allowed.containsAll(values)) throw invalid("条件值无效"); }
    private void one(String value,Set<String> allowed,String label) { if(value==null || !allowed.contains(value)) throw invalid(label+"无效"); }
    private void text(String value,int max,boolean required,String label) { if(required && (value==null || value.isBlank()) || value!=null && value.length()>max) throw invalid(label+"必填或超长"); }
}
