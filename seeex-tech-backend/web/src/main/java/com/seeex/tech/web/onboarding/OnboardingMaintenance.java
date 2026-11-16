package com.seeex.tech.web.onboarding;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.seeex.tech.common.exception.BizException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.*;
/** 超时只提醒，身份或候选失效转异常，绝不自动通过。 */
@Service @RequiredArgsConstructor
public class OnboardingMaintenance {
    private final OnboardingStore store;
    private final OnboardingDirectory directory;
    private final OnboardingAccess access;
    private final OnboardingNotificationService notifications;
    private long cursor;
    /** 分段扫描避免每次调度反复只检查最早的100个申请。 */
    @Transactional
    public void scan() {
        var rows=store.requests.selectPage(new Page<>(1,100,false),Wrappers.<OnboardingRequestDO>lambdaQuery().gt(OnboardingRequestDO::getId,cursor).in(OnboardingRequestDO::getState,List.of("IN_APPROVAL","PENDING_FULFILLMENT","FULFILLING")).isNull(OnboardingRequestDO::getDeletedAt).orderByAsc(OnboardingRequestDO::getId)).getRecords(); if(rows.isEmpty()){cursor=0;return;}
        for(var request:rows) {
            cursor=request.getId(); boolean applicantActive=true; try { directory.active(request.getApplicantId()); } catch(BizException ex) { applicantActive=false; }
            String reason=applicantActive?null:"申请人身份已停用，暂停后续审批和办理";
            if(reason==null && "FULFILLING".equals(request.getState()) && (request.getFulfillerId()==null || !access.fulfillmentCandidate(request.getFulfillerId(),request) || !directory.can(request.getFulfillerId(),"app-fulfillments:act"))) reason="当前办理人已失去资格，请恢复后重新分派";
            if(reason==null && "PENDING_FULFILLMENT".equals(request.getState())) {
                var candidates=store.participants.selectList(Wrappers.<OnboardingParticipantDO>lambdaQuery().eq(OnboardingParticipantDO::getRequestId,request.getId()).eq(OnboardingParticipantDO::getAttemptNo,request.getAttemptNo()));
                if(candidates.stream().noneMatch(candidate->access.fulfillmentCandidate(candidate.getActorId(),request) && directory.can(candidate.getActorId(),"app-fulfillments:view") && directory.can(candidate.getActorId(),"app-fulfillments:act")))reason="办理候选已全部失去资格，请恢复后重新分派";
            }
            var tasks=store.tasks.selectList(Wrappers.<OnboardingTaskDO>lambdaQuery().eq(OnboardingTaskDO::getRequestId,request.getId()).eq(OnboardingTaskDO::getAttemptNo,request.getAttemptNo()).in(OnboardingTaskDO::getState,List.of("PENDING","CLAIMED")));
            if(reason==null && "IN_APPROVAL".equals(request.getState())) {
                long valid=tasks.stream().filter(task->access.reviewer(task.getCandidateId(),request,task) && directory.can(task.getCandidateId(),"workflow:tasks:act") && directory.can(task.getCandidateId(),"workflow:tasks:view")).count();
                boolean all=!tasks.isEmpty() && "ALL".equals(tasks.getFirst().getMode());
                if(valid==0 || all && valid<tasks.size()) reason="审批候选资格已失效，会签须全部可处理，请检查来源和兜底人员";
            }
            var overdue=tasks.stream().filter(task->task.getDueAt()!=null && task.getDueAt().isBefore(LocalDateTime.now()) && !notifications.exists(request,task.getCandidateId(),"overdue:"+task.getId())).toList();
            if(reason==null && overdue.isEmpty())continue;
            try { access.lock(request,request.getRowVersion()); }
            catch(BizException conflict) { continue; }
            if(reason!=null) { request.setState("EXCEPTION"); request.setExceptionMessage(reason); access.save(request); access.log(request,0L,"PAUSE_EXCEPTION",reason); notifications.exception(request); }
            else for(var task:overdue) notifications.enqueue(request,task.getCandidateId(),"overdue:"+task.getId(),"应用接入审批任务已超时，请及时处理");
        }
    }
}
