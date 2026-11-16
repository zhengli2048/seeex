package com.seeex.tech.web.onboarding;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import java.time.LocalDateTime;
import java.util.*;
/** 外部通知租约独立短事务，旧发送者不能覆盖新重试状态。 */
@Service @RequiredArgsConstructor
public class OnboardingNoticeState {
    private static final int MAX_ATTEMPTS=5;
    private final OnboardingStore store;
    /** 领取单个作业，回收崩溃租约，禁止持锁调用HTTP。 */
    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public OnboardingNoticeDO claim() {
        var now=LocalDateTime.now();
        store.notices.update(null,Wrappers.<OnboardingNoticeDO>lambdaUpdate().eq(OnboardingNoticeDO::getState,"SENDING").lt(OnboardingNoticeDO::getLeaseUntil,now).lt(OnboardingNoticeDO::getAttempts,MAX_ATTEMPTS).set(OnboardingNoticeDO::getState,"PENDING").set(OnboardingNoticeDO::getOwnerToken,null).set(OnboardingNoticeDO::getLeaseUntil,null));
        store.notices.update(null,Wrappers.<OnboardingNoticeDO>lambdaUpdate().eq(OnboardingNoticeDO::getState,"SENDING").lt(OnboardingNoticeDO::getLeaseUntil,now).ge(OnboardingNoticeDO::getAttempts,MAX_ATTEMPTS).set(OnboardingNoticeDO::getState,"DEAD_LETTER").set(OnboardingNoticeDO::getLastError,"发送多次中断，请人工重试").set(OnboardingNoticeDO::getOwnerToken,null).set(OnboardingNoticeDO::getLeaseUntil,null));
        var rows=store.notices.selectPage(new Page<>(1,1,false),Wrappers.<OnboardingNoticeDO>lambdaQuery().in(OnboardingNoticeDO::getState,List.of("PENDING","RETRY_WAIT")).le(OnboardingNoticeDO::getNextAttemptAt,now).orderByAsc(OnboardingNoticeDO::getId)).getRecords(); if(rows.isEmpty())return null;
        var row=rows.getFirst(); String token=UUID.randomUUID().toString();
        if(store.notices.update(null,Wrappers.<OnboardingNoticeDO>lambdaUpdate().eq(OnboardingNoticeDO::getId,row.getId()).eq(OnboardingNoticeDO::getRowVersion,row.getRowVersion()).in(OnboardingNoticeDO::getState,List.of("PENDING","RETRY_WAIT")).set(OnboardingNoticeDO::getState,"SENDING").set(OnboardingNoticeDO::getOwnerToken,token).set(OnboardingNoticeDO::getAttempts,row.getAttempts()+1).set(OnboardingNoticeDO::getLeaseUntil,now.plusSeconds(120)).set(OnboardingNoticeDO::getRowVersion,row.getRowVersion()+1))!=1) return null;
        row.setOwnerToken(token); row.setAttempts(row.getAttempts()+1); row.setRowVersion(row.getRowVersion()+1); return row;
    }
    /** 仅当前发送令牌可记录成功/退避/死信，状态无密钥。 */
    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public void finish(OnboardingNoticeDO job,String outcome) {
        String state="SUCCESS".equals(outcome)?"SENT":"SKIPPED".equals(outcome)?"SKIPPED":job.getAttempts()>=MAX_ATTEMPTS?"DEAD_LETTER":"RETRY_WAIT";
        store.notices.update(null,Wrappers.<OnboardingNoticeDO>lambdaUpdate().eq(OnboardingNoticeDO::getId,job.getId()).eq(OnboardingNoticeDO::getState,"SENDING").eq(OnboardingNoticeDO::getOwnerToken,job.getOwnerToken()).eq(OnboardingNoticeDO::getRowVersion,job.getRowVersion()).set(OnboardingNoticeDO::getState,state).set(OnboardingNoticeDO::getLastError,"SUCCESS".equals(outcome)?null:"SKIPPED".equals(outcome)?"无有效企业微信身份，站内消息仍可查看":"外部通知发送失败，请检查配置、可见范围和网络").set(OnboardingNoticeDO::getOwnerToken,null).set(OnboardingNoticeDO::getLeaseUntil,null).set(OnboardingNoticeDO::getNextAttemptAt,LocalDateTime.now().plusSeconds(30L*(1L<<Math.min(job.getAttempts(),6)))).set(OnboardingNoticeDO::getRowVersion,job.getRowVersion()+1));
    }
}
