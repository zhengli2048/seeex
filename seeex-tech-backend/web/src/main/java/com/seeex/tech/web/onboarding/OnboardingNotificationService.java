package com.seeex.tech.web.onboarding;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import java.time.LocalDateTime;
import static com.seeex.tech.web.onboarding.OnboardingContract.*;
import static com.seeex.tech.web.onboarding.OnboardingJson.*;
/** 站内交付通知先持久化，外部通知Outbox与业务同事务；从不接收凭证正文。 */
@Service @RequiredArgsConstructor
public class OnboardingNotificationService {
    private final OnboardingStore store;
    private final OnboardingDirectory directory;
    private final OnboardingAccess access;
    @Value("${onboarding.wecom-notifications-enabled:false}") private boolean wecomEnabled;
    /** 控制台仅获知渠道开关，不返回连接凭证。 */
    public boolean enabled() { return wecomEnabled; }
    /** 判断提醒是否已持久化，避免定时扫描无变化也推进申请版本。 */
    public boolean exists(OnboardingRequestDO request,long recipient,String event) { String key=request.getId()+":"+request.getAttemptNo()+":"+event; return store.messages.selectCount(Wrappers.<OnboardingMessageDO>lambdaQuery().eq(OnboardingMessageDO::getRecipientId,recipient).eq(OnboardingMessageDO::getEventKey,key))>0; }
    /** 事务内按事件与收件人去重；外部失败不推翻业务。 */
    @Transactional(propagation=Propagation.MANDATORY)
    public void enqueue(OnboardingRequestDO request,long recipient,String event,String title) {
        String key=request.getId()+":"+request.getAttemptNo()+":"+event;
        if(store.messages.selectCount(Wrappers.<OnboardingMessageDO>lambdaQuery().eq(OnboardingMessageDO::getRecipientId,recipient).eq(OnboardingMessageDO::getEventKey,key))>0) return;
        var row=new OnboardingMessageDO(); row.setRequestId(request.getId()); row.setRecipientId(recipient); row.setEventKey(key); row.setTitle(title); row.setContent("申请编号："+request.getRequestNo()+"。请登录平台查看，通知不包含接入凭证。"); row.setRowVersion(0L); row.setCreatedAt(LocalDateTime.now()); store.messages.insert(row);
        var notice=new OnboardingNoticeDO(); notice.setMessageId(row.getId()); notice.setRecipientId(recipient); notice.setEventKey(key); notice.setState(wecomEnabled?"PENDING":"DISABLED"); notice.setAttempts(0); notice.setNextAttemptAt(LocalDateTime.now()); notice.setRowVersion(0L); notice.setCreatedAt(LocalDateTime.now()); store.notices.insert(notice);
    }
    /** 异常同时通知流程中配置的有效运维员，不只通知已停用申请人。 */
    @Transactional(propagation=Propagation.MANDATORY)
    public void exception(OnboardingRequestDO request) {
        enqueue(request,request.getApplicantId(),"exception:"+request.getRowVersion(),"应用申请暂停，请联系平台处理");
        var selector=access.model(request).operators(); java.util.Set<Long> candidates;
        try { candidates=directory.qualified(selector,request.getDepartmentId(),null,OnboardingDirectory.CandidateKind.OPERATE); }
        catch(com.seeex.tech.common.exception.BizException ex) { return; }
        for(Long actor:candidates)if(directory.can(actor,"app-requests:manage") && directory.can(actor,"notifications:own:view"))enqueue(request,actor,"exception-operator:"+request.getRowVersion(),"应用申请出现异常，请处理候选身份或恢复流程");
    }
    /** 本人站内消息。 */
    public PageView<MessageView> inbox(long actor,ListQuery filter) {
        directory.require(actor,"notifications:own:view"); var query=Wrappers.<OnboardingMessageDO>lambdaQuery().eq(OnboardingMessageDO::getRecipientId,actor).isNull("UNREAD".equals(filter.state()),OnboardingMessageDO::getReadAt).orderByDesc(OnboardingMessageDO::getId); var page=store.messages.selectPage(new Page<>(filter.page(),filter.pageSize()),query);
        return new PageView<>(page.getRecords().stream().map(row->new MessageView(row.getId(),row.getRequestId(),row.getTitle(),row.getContent(),row.getReadAt(),row.getRowVersion(),row.getCreatedAt())).toList(),page.getTotal());
    }
    /** 已读不等于领取，不能改变收件人。 */
    @Transactional
    public void read(long actor,long id,ActionCommand command) {
        directory.require(actor,"notifications:own:view");
        if(command.expectedVersion()==null || command.expectedVersion()<0 || command.expectedVersion()==Long.MAX_VALUE) throw conflict();
        if(store.messages.update(null,Wrappers.<OnboardingMessageDO>lambdaUpdate().eq(OnboardingMessageDO::getId,id).eq(OnboardingMessageDO::getRecipientId,actor).eq(OnboardingMessageDO::getRowVersion,command.expectedVersion()).isNull(OnboardingMessageDO::getReadAt).set(OnboardingMessageDO::getReadAt,LocalDateTime.now()).set(OnboardingMessageDO::getRowVersion,command.expectedVersion()+1))!=1) throw conflict();
    }
    /** 通知运维只看到发送状态，无交付密文或令牌。 */
    public PageView<NoticeView> jobs(long actor,ListQuery filter) {
        directory.require(actor,"notifications:operate"); var page=store.notices.selectPage(new Page<>(filter.page(),filter.pageSize()),Wrappers.<OnboardingNoticeDO>lambdaQuery().eq(filter.state()!=null && !filter.state().isBlank(),OnboardingNoticeDO::getState,filter.state()).orderByDesc(OnboardingNoticeDO::getId));
        var messageIds=page.getRecords().stream().map(OnboardingNoticeDO::getMessageId).toList(); var requests=new java.util.HashMap<Long,Long>(); if(!messageIds.isEmpty()) store.messages.selectList(Wrappers.<OnboardingMessageDO>lambdaQuery().in(OnboardingMessageDO::getId,messageIds)).forEach(row->requests.put(row.getId(),row.getRequestId()));
        return new PageView<>(page.getRecords().stream().map(row->new NoticeView(row.getId(),requests.get(row.getMessageId()),row.getRecipientId(),row.getState(),row.getAttempts(),row.getLastError(),row.getRowVersion(),row.getNextAttemptAt())).toList(),page.getTotal());
    }
    /** 重试原通知，不重建应用、不轮换密钥；启用后才能重试外部渠道。 */
    @Transactional
    public void retry(long actor,long id,ActionCommand command) {
        directory.require(actor,"notifications:operate"); accessReason(command);
        if(!wecomEnabled) throw invalid("外部通知渠道尚未启用");
        if(store.notices.update(null,Wrappers.<OnboardingNoticeDO>lambdaUpdate().eq(OnboardingNoticeDO::getId,id).eq(OnboardingNoticeDO::getRowVersion,command.expectedVersion()).in(OnboardingNoticeDO::getState,java.util.List.of("DEAD_LETTER","SKIPPED","DISABLED")).set(OnboardingNoticeDO::getState,"PENDING").set(OnboardingNoticeDO::getAttempts,0).set(OnboardingNoticeDO::getLastError,null).set(OnboardingNoticeDO::getNextAttemptAt,LocalDateTime.now()).set(OnboardingNoticeDO::getOwnerToken,null).set(OnboardingNoticeDO::getLeaseUntil,null).set(OnboardingNoticeDO::getRowVersion,command.expectedVersion()+1))!=1) throw conflict();
    }
    private void accessReason(ActionCommand command) { if(command.expectedVersion()==null || command.expectedVersion()<0 || command.expectedVersion()==Long.MAX_VALUE || command.comment()==null || command.comment().isBlank() || command.comment().length()>1000) throw invalid("请填写版本和重试原因"); }
}
