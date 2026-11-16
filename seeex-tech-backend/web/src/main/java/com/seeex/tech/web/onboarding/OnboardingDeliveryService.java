package com.seeex.tech.web.onboarding;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.seeex.tech.common.api.ErrorCode;
import com.seeex.tech.common.exception.BizException;
import com.seeex.tech.web.registry.ApplicationRegistryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import java.time.LocalDateTime;
import java.util.*;
import static com.seeex.tech.web.onboarding.OnboardingContract.*;
import static com.seeex.tech.web.onboarding.OnboardingJson.*;
/** 只有原申请人能消费密文；办理响应从不返回注册服务的明文凭证。 */
@Service @RequiredArgsConstructor
public class OnboardingDeliveryService {
    private static final int EXPIRY_HOURS=24;
    private final OnboardingStore store;
    private final OnboardingAccess access;
    private final OnboardingDirectory directory;
    private final OnboardingSecretCodec secrets;
    private final OnboardingJson json;
    private final OnboardingNotificationService notifications;
    private final ApplicationRegistryService registry;
    /** 办理预检时明确提示缺少部署密钥。 */
    public void requireConfigured() { secrets.requireConfigured(); }
    /** 普通页面只知道密钥是否就绪。 */
    public boolean configured() { return secrets.configured(); }
    /** 首次交付与应用注册必须在同一事务。 */
    @Transactional(propagation=Propagation.MANDATORY)
    public void create(OnboardingRequestDO request,ApplicationRegistryService.AppRegistration registration) { issue(request,new SecretView(registration.ciToken(),registration.clientSecret())); }
    /** 本人元数据可重复查看，绝不返回密文、摘要或明文。 */
    public DeliveryView view(long actor,long requestId) {
        var request=recipient(actor,requestId); var row=latest(requestId); String state=row.getAcknowledgedAt()!=null?"ACKNOWLEDGED":row.getRedeemedAt()!=null?"REDEEMED":row.getExpiresAt().isBefore(LocalDateTime.now())?"EXPIRED":"AVAILABLE"; Set<String> actions=new HashSet<>(); if("PENDING_RECEIPT".equals(request.getState())) { if("AVAILABLE".equals(state))actions.add("REDEEM"); if(row.getRedeemedAt()!=null)actions.add("ACKNOWLEDGE"); }
        return new DeliveryView(row.getId(),requestId,row.getDeliveryVersion(),row.getRowVersion(),json.read(row.getInfoJson(),FulfillmentForm.class),request.getAppCode(),state,row.getExpiresAt(),row.getRedeemedAt(),row.getAcknowledgedAt(),actions);
    }
    /** POST一次性领取；并发只允许一次成功，响应丢失需受控补发。 */
    @Transactional
    public SecretView redeem(long actor,long requestId,ActionCommand command,String key) {
        var request=recipient(actor,requestId); if(!"PENDING_RECEIPT".equals(request.getState())) throw conflict();
        if(access.previous(actor,"REDEEM",key,command,requestId)!=null) throw new BizException(ErrorCode.CONFLICT,"该领取已消费，不能回查明文；若响应丢失请联系平台补发");
        var row=latest(requestId); checkDelivery(row,command); if(row.getExpiresAt().isBefore(LocalDateTime.now())) throw new BizException(ErrorCode.DELIVERY_EXPIRED); if(row.getRedeemedAt()!=null || row.getSecretCipher()==null) throw new BizException(ErrorCode.CONFLICT,"凭证已领取，不能再次显示");
        access.lock(request,request.getRowVersion()); var result=json.read(secrets.decrypt(row.getSecretCipher(),row.getKeyId(),aad(row)),SecretView.class);
        if(store.deliveries.update(null,Wrappers.<OnboardingDeliveryDO>lambdaUpdate().eq(OnboardingDeliveryDO::getId,row.getId()).eq(OnboardingDeliveryDO::getRecipientId,actor).eq(OnboardingDeliveryDO::getRowVersion,command.expectedVersion()).isNull(OnboardingDeliveryDO::getRedeemedAt).gt(OnboardingDeliveryDO::getExpiresAt,LocalDateTime.now()).set(OnboardingDeliveryDO::getRedeemedAt,LocalDateTime.now()).set(OnboardingDeliveryDO::getSecretCipher,null).set(OnboardingDeliveryDO::getRowVersion,row.getRowVersion()+1))!=1) throw conflict();
        access.remember(actor,"REDEEM",key,command,requestId); access.log(request,actor,"REDEEM_DELIVERY","交付版本"+row.getDeliveryVersion()); return result;
    }
    /** 确认收件独立于通知已读和凭证领取。 */
    @Transactional
    public DeliveryView acknowledge(long actor,long requestId,ActionCommand command) {
        var request=recipient(actor,requestId); if(!"PENDING_RECEIPT".equals(request.getState())) throw conflict(); var row=latest(requestId); checkDelivery(row,command); if(row.getRedeemedAt()==null || row.getAcknowledgedAt()!=null) throw conflict(); access.lock(request,request.getRowVersion());
        if(store.deliveries.update(null,Wrappers.<OnboardingDeliveryDO>lambdaUpdate().eq(OnboardingDeliveryDO::getId,row.getId()).eq(OnboardingDeliveryDO::getRecipientId,actor).eq(OnboardingDeliveryDO::getRowVersion,command.expectedVersion()).isNull(OnboardingDeliveryDO::getAcknowledgedAt).isNotNull(OnboardingDeliveryDO::getRedeemedAt).set(OnboardingDeliveryDO::getAcknowledgedAt,LocalDateTime.now()).set(OnboardingDeliveryDO::getRowVersion,row.getRowVersion()+1))!=1) throw conflict();
        registry.confirmRequestDelivery(request.getAppCode(),requestId); request.setState("COMPLETED"); access.save(request); access.log(request,actor,"ACKNOWLEDGE_DELIVERY",null); if(request.getFulfillerId()!=null)notifications.enqueue(request,request.getFulfillerId(),"receipt","申请人已确认接入交付"); return view(actor,requestId);
    }
    /** 补发必定轮换两类旧凭证，且仍只交付原申请人；通知重试不调用此方法。 */
    @Transactional
    public void reissue(long actor,long requestId,ActionCommand command,String key) {
        directory.require(actor,"app-deliveries:reissue"); var request=access.view(actor,requestId); if(access.previous(actor,"REISSUE",key,command,requestId)!=null) return;
        if(!"PENDING_RECEIPT".equals(request.getState()) || !access.fulfillmentCandidate(actor,request) || !directory.can(actor,"app-fulfillments:act")) throw new BizException(ErrorCode.FORBIDDEN); directory.active(request.getApplicantId()); access.comment(command.comment(),true); if(!Boolean.TRUE.equals(command.confirmRotation())) throw invalid("补发将立即使旧CI和应用服务凭证失效，请明确确认轮换"); requireConfigured();
        access.lock(request,command.expectedVersion()); var previous=latest(requestId); store.deliveries.update(null,Wrappers.<OnboardingDeliveryDO>lambdaUpdate().eq(OnboardingDeliveryDO::getId,previous.getId()).set(OnboardingDeliveryDO::getSecretCipher,null).set(OnboardingDeliveryDO::getExpiresAt,LocalDateTime.now()).set(OnboardingDeliveryDO::getRowVersion,previous.getRowVersion()+1));
        var registration=registry.reissueForRequest(request.getAppCode(),requestId); issue(request,new SecretView(registration.ciToken(),registration.clientSecret())); access.log(request,actor,"REISSUE_DELIVERY",command.comment()); access.remember(actor,"REISSUE",key,command,requestId);
    }
    /** 清除到期密文；当前已领取凭证在领取事务内就已清除。 */
    @Transactional
    public void expire() { store.deliveries.update(null,Wrappers.<OnboardingDeliveryDO>lambdaUpdate().isNotNull(OnboardingDeliveryDO::getSecretCipher).lt(OnboardingDeliveryDO::getExpiresAt,LocalDateTime.now()).set(OnboardingDeliveryDO::getSecretCipher,null)); }
    private void issue(OnboardingRequestDO request,SecretView value) {
        requireConfigured(); var rows=store.deliveries.selectPage(new Page<>(1,1,false),Wrappers.<OnboardingDeliveryDO>lambdaQuery().eq(OnboardingDeliveryDO::getRequestId,request.getId()).orderByDesc(OnboardingDeliveryDO::getDeliveryVersion)).getRecords();
        var row=new OnboardingDeliveryDO(); row.setRequestId(request.getId()); row.setRecipientId(request.getApplicantId()); row.setDeliveryVersion(rows.isEmpty()?1:rows.getFirst().getDeliveryVersion()+1); row.setInfoJson(request.getFulfillmentJson()); row.setKeyId(secrets.keyId()); row.setSecretCipher(secrets.encrypt(json.write(value),aad(row))); row.setExpiresAt(LocalDateTime.now().plusHours(EXPIRY_HOURS)); row.setRowVersion(0L); row.setCreatedAt(LocalDateTime.now()); store.deliveries.insert(row); notifications.enqueue(request,request.getApplicantId(),"delivery:"+row.getDeliveryVersion(),"应用接入信息已就绪，请本人在24小时内领取凭证");
    }
    private String aad(OnboardingDeliveryDO row) { return row.getRequestId()+":"+row.getRecipientId()+":"+row.getDeliveryVersion(); }
    private OnboardingRequestDO recipient(long actor,long requestId) { directory.require(actor,"app-deliveries:own:view"); var request=access.request(requestId); if(!Objects.equals(actor,request.getApplicantId()) || !Set.of("PENDING_RECEIPT","COMPLETED").contains(request.getState())) throw new BizException(ErrorCode.FORBIDDEN); return request; }
    private OnboardingDeliveryDO latest(long requestId) { var rows=store.deliveries.selectPage(new Page<>(1,1,false),Wrappers.<OnboardingDeliveryDO>lambdaQuery().eq(OnboardingDeliveryDO::getRequestId,requestId).orderByDesc(OnboardingDeliveryDO::getDeliveryVersion)).getRecords(); if(rows.isEmpty())throw new BizException(ErrorCode.RESOURCE_NOT_FOUND,"交付记录不存在"); return rows.getFirst(); }
    private void checkDelivery(OnboardingDeliveryDO row,ActionCommand command) { if(!Objects.equals(command.deliveryId(),row.getId()) || command.expectedVersion()==null || command.expectedVersion()<0 || command.expectedVersion()==Long.MAX_VALUE || !Objects.equals(command.expectedVersion(),row.getRowVersion())) throw conflict(); }
}
