package com.seeex.tech.web.wecom;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.seeex.tech.common.api.ErrorCode;
import com.seeex.tech.common.exception.BizException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.*;

/** 批次租约和持久结果；写入前锁定执行令牌，失效worker不能再提交。 */
@Service @RequiredArgsConstructor
public class SyncRunStateService {
    private static final int LEASE_SECONDS=120;
    private static final int MAX_ATTEMPTS=3;
    private final SyncRunMapper runs;
    private final SyncRunItemMapper items;

    /** CAS领取一条任务，自动恢复过期执行；每次尝试使用新令牌。 */
    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public Lease claim() {
        var now=LocalDateTime.now();
        runs.update(null,Wrappers.<SyncRunDO>lambdaUpdate().eq(SyncRunDO::getStatus,"RUNNING").lt(SyncRunDO::getLeaseUntil,now).lt(SyncRunDO::getAttemptCount,MAX_ATTEMPTS).set(SyncRunDO::getStatus,"PENDING").set(SyncRunDO::getOwnerToken,null).set(SyncRunDO::getLeaseUntil,null));
        runs.update(null,Wrappers.<SyncRunDO>lambdaUpdate().eq(SyncRunDO::getStatus,"RUNNING").lt(SyncRunDO::getLeaseUntil,now).ge(SyncRunDO::getAttemptCount,MAX_ATTEMPTS).set(SyncRunDO::getStatus,"FAILED").set(SyncRunDO::getErrorMessage,"执行多次中断，请查看明细并手动重试").set(SyncRunDO::getOwnerToken,null).set(SyncRunDO::getLeaseUntil,null).set(SyncRunDO::getFinishedAt,now));
        var page=runs.selectPage(new Page<>(1,1),Wrappers.<SyncRunDO>lambdaQuery().eq(SyncRunDO::getStatus,"PENDING").orderByAsc(SyncRunDO::getId));
        if(page.getRecords().isEmpty()) return null;
        var row=page.getRecords().getFirst(); int attempt=row.getAttemptCount()==null?0:row.getAttemptCount(); String token=UUID.randomUUID().toString();
        int count=runs.update(null,Wrappers.<SyncRunDO>lambdaUpdate().eq(SyncRunDO::getId,row.getId()).eq(SyncRunDO::getStatus,"PENDING").eq(SyncRunDO::getAttemptCount,attempt).set(SyncRunDO::getStatus,"RUNNING").set(SyncRunDO::getAttemptCount,attempt+1).set(SyncRunDO::getOwnerToken,token).set(SyncRunDO::getLeaseUntil,now.plusSeconds(LEASE_SECONDS)).set(SyncRunDO::getErrorMessage,null).set(SyncRunDO::getUserCount,0).set(SyncRunDO::getFailedCount,0));
        return count==1?new Lease(row.getId(),row.getRunNo(),token,attempt+1):null;
    }
    /** 在调用者写事务中加锁并续租；锁持续到调用者提交，避免回收与写入竞争。 */
    @Transactional(propagation=Propagation.MANDATORY)
    public void fence(Lease lease) {
        if(lease==null) return;
        var now=LocalDateTime.now();
        var row=runs.selectById(lease.id());
        if(row==null || row.getLeaseUntil()==null || !row.getLeaseUntil().isAfter(now) || !Objects.equals(row.getOwnerToken(),lease.token()) || !"RUNNING".equals(row.getStatus())) throw new LeaseLostException();
        var renewed=now.plusSeconds(LEASE_SECONDS); if(!renewed.isAfter(row.getLeaseUntil())) renewed=row.getLeaseUntil().plusNanos(1_000_000);
        int count=runs.update(null,Wrappers.<SyncRunDO>lambdaUpdate().eq(SyncRunDO::getId,lease.id()).eq(SyncRunDO::getLeaseUntil,row.getLeaseUntil()).eq(SyncRunDO::getStatus,"RUNNING").eq(SyncRunDO::getOwnerToken,lease.token()).eq(SyncRunDO::getAttemptCount,lease.attempt()).gt(SyncRunDO::getLeaseUntil,now).set(SyncRunDO::getLeaseUntil,renewed));
        if(count!=1) throw new LeaseLostException();
    }
    /** HTTP读取之间独立续租，不持锁等待外部服务。 */
    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public void pulse(Lease lease) { fence(lease); }
    /** 单项成功与组织写入同一事务，崩溃后可查真实提交结果。 */
    @Transactional(propagation=Propagation.MANDATORY)
    public void committed(Lease lease,String kind,String key,boolean skipped) { if(lease!=null) item(lease,kind,key,skipped?"SKIPPED":"SUCCESS",null); }
    /** 失败在员工事务已回滚后独立持久化。 */
    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public void failed(Lease lease,String kind,String key,String message) { fence(lease); item(lease,kind,key,"FAILED",message); }
    /** 计算当前尝试的实际结果，禁止旧worker修改结束状态。 */
    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public void finish(Lease lease,boolean fatal) {
        fence(lease);
        var rows=items.selectList(Wrappers.<SyncRunItemDO>lambdaQuery().eq(SyncRunItemDO::getRunNo,lease.runNo()).eq(SyncRunItemDO::getAttemptNo,lease.attempt()));
        int failures=(int)rows.stream().filter(row->"FAILED".equals(row.getStatus())).count();
        int successes=(int)rows.stream().filter(row->"EMPLOYEE".equals(row.getObjectType()) && !"FAILED".equals(row.getStatus())).count();
        runs.update(null,Wrappers.<SyncRunDO>lambdaUpdate().eq(SyncRunDO::getId,lease.id()).eq(SyncRunDO::getOwnerToken,lease.token()).set(SyncRunDO::getStatus,fatal?"FAILED":failures>0?"PARTIAL":"SUCCESS").set(SyncRunDO::getUserCount,successes).set(SyncRunDO::getFailedCount,failures).set(SyncRunDO::getErrorMessage,failures>0?"部分对象处理失败，请查看批次明细":null).set(SyncRunDO::getFinishedAt,LocalDateTime.now()).set(SyncRunDO::getOwnerToken,null).set(SyncRunDO::getLeaseUntil,null));
    }
    /** 手动重试失败/部分成功批次，保留之前尝试明细。 */
    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public void retry(String runNo) {
        int count=runs.update(null,Wrappers.<SyncRunDO>lambdaUpdate().eq(SyncRunDO::getRunNo,runNo).in(SyncRunDO::getStatus,List.of("FAILED","PARTIAL")).set(SyncRunDO::getStatus,"PENDING").set(SyncRunDO::getOwnerToken,null).set(SyncRunDO::getLeaseUntil,null));
        if(count!=1) throw new BizException(ErrorCode.CONFLICT,"仅失败或部分成功批次可重试");
    }
    /** 分页读取单项结果，不暴露执行令牌或原文。 */
    public ItemPage details(String runNo,int page,int pageSize) {
        var result=items.selectPage(new Page<>(Math.max(1,page),Math.min(100,Math.max(1,pageSize))),Wrappers.<SyncRunItemDO>lambdaQuery().eq(SyncRunItemDO::getRunNo,runNo).orderByDesc(SyncRunItemDO::getId));
        return new ItemPage(result.getRecords(),result.getTotal());
    }
    private void item(Lease lease,String kind,String key,String status,String message) {
        var row=items.selectOne(Wrappers.<SyncRunItemDO>lambdaQuery().eq(SyncRunItemDO::getRunNo,lease.runNo()).eq(SyncRunItemDO::getAttemptNo,lease.attempt()).eq(SyncRunItemDO::getObjectType,kind).eq(SyncRunItemDO::getExternalKey,key));
        boolean fresh=row==null; if(fresh) row=new SyncRunItemDO();
        row.setRunNo(lease.runNo()); row.setAttemptNo(lease.attempt()); row.setObjectType(kind); row.setExternalKey(key); row.setStatus(status); row.setErrorMessage(message);
        if(fresh) items.insert(row); else items.updateById(row);
    }
    /** 不可伪造的内部执行上下文。 */
    public record Lease(Long id,String runNo,String token,int attempt) { }
    /** 不包含敏感参数的失效信号。 */
    public static class LeaseLostException extends BizException { public LeaseLostException() { super(ErrorCode.CONFLICT,"同步执行权已失效，旧任务停止"); } }
    /** 明细分页。 */
    public record ItemPage(List<SyncRunItemDO> items,long total) { }
}
