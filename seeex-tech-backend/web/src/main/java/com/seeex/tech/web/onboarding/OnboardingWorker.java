package com.seeex.tech.web.onboarding;
import com.seeex.tech.web.wecom.WeComClient;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
/** 默认关闭外部渠道；启用后在数据库事务外发送，站内消息始终独立可用。 */
@Component @RequiredArgsConstructor
public class OnboardingWorker {
    private final OnboardingNoticeState states;
    private final OnboardingStore store;
    private final OnboardingDirectory directory;
    private final WeComClient wecom;
    private final OnboardingMaintenance maintenance;
    private final OnboardingDeliveryService deliveries;
    @Value("${onboarding.portal-url:}") private String portalUrl;
    @Value("${onboarding.wecom-notifications-enabled:false}") private boolean enabled;
    /** Outbox至多一个发送者持有当前令牌；重复外部发送按at-least-once语义。 */
    @Scheduled(fixedDelay=5000)
    public void send() {
        if(!enabled)return; var job=states.claim(); if(job==null)return;
        try { var employee=directory.active(job.getRecipientId()); var message=store.messages.selectById(job.getMessageId()); if(message==null || !"WECOM".equals(employee.getSource()) || employee.getEmployeeNo()==null || !employee.getEmployeeNo().startsWith("wc_")) { states.finish(job,"SKIPPED"); return; } wecom.sendApplicationNotice(employee.getEmployeeNo().substring(3),message.getTitle()+"\n"+message.getContent()+"\n"+details(message.getRequestId())); states.finish(job,"SUCCESS"); }
        catch(com.seeex.tech.common.exception.BizException ex) { states.finish(job,"FAILED"); }
    }
    private String details(long requestId) {
        try { var uri=java.net.URI.create(portalUrl); if(!java.util.Set.of("http","https").contains(java.util.Objects.toString(uri.getScheme(),"")) || uri.getHost()==null || uri.getUserInfo()!=null || uri.getQuery()!=null || uri.getFragment()!=null || portalUrl.length()>512) throw new IllegalArgumentException(); return portalUrl.replaceAll("/+$","")+"/application-requests?request="+requestId; }
        catch(IllegalArgumentException ex) { throw new com.seeex.tech.common.exception.BizException(com.seeex.tech.common.api.ErrorCode.BUSINESS_ERROR,"外部通知需配置有效APP_ONBOARDING_PORTAL_URL"); }
    }
    /** 到期密文清理、非自动通过的提醒与身份异常检查。 */
    @Scheduled(fixedDelay=60000)
    public void maintain() { deliveries.expire(); maintenance.scan(); }
}
