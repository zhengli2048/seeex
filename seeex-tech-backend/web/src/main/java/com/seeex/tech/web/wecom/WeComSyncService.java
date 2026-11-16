package com.seeex.tech.web.wecom;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.seeex.tech.common.api.ErrorCode;
import com.seeex.tech.common.exception.BizException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.time.*;
import java.util.*;

/** 无长事务的同步编排；外部读取与独立受代理的原子写入分开。 */
@Service @RequiredArgsConstructor
public class WeComSyncService {
    private static final int MAX_USERS=10000;
    private static final int MAX_DEPARTMENTS=2000;
    private final WeComClient client;
    private final SyncRunMapper runs;
    private final SyncRunStateService states;
    private final WeComDirectoryWriter writer;

    /** 创建批次，实际执行由持有租约的消费者完成。 */
    public SyncStatus startFullSync() {
        var row=new SyncRunDO(); row.setRunNo(UUID.randomUUID().toString().replace("-","")); row.setSyncType("FULL"); row.setStatus("PENDING"); row.setUserCount(0); row.setFailedCount(0); row.setAttemptCount(0); runs.insert(row);
        return status(row.getRunNo());
    }
    /** 先完成完整远端快照读取，之后逐员工提交；失败明细不随员工回滚。 */
    public void executeFullSync(SyncRunStateService.Lease lease) {
        try {
            states.pulse(lease); var observed=LocalDateTime.now(); String token=client.getToken().access_token();
            var departments=client.listDepartments(token).department();
            if(departments==null || departments.isEmpty() || departments.size()>MAX_DEPARTMENTS) throw new BizException(ErrorCode.BUSINESS_ERROR,"部门快照为空或超过同步上限");
            Map<String,WeComClient.User> users=new LinkedHashMap<>();
            for(var department:departments) {
                states.pulse(lease); var response=client.listUsers(token,department.id());
                if(response.userlist()==null) throw new BizException(ErrorCode.BUSINESS_ERROR,"远端成员快照不完整");
                for(var user:response.userlist()) { if(user==null || user.userid()==null || user.userid().length()>128) throw new BizException(ErrorCode.BUSINESS_ERROR,"远端成员标识无效"); users.putIfAbsent(user.userid(),user); }
                if(users.size()>MAX_USERS) throw new BizException(ErrorCode.VALIDATION_FAILED,"成员超过10000人同步上限");
            }
            writer.departments(departments,new WeComDirectoryWriter.WriteContext(lease,observed));
            for(var user:users.values()) {
                states.pulse(lease);
                try { writer.employee(user,new WeComDirectoryWriter.WriteContext(lease,observed)); }
                catch(SyncRunStateService.LeaseLostException ex) { throw ex; }
                catch(RuntimeException ex) { states.failed(lease,"EMPLOYEE",user.userid(),"成员原子写入失败，原资料与关系已回滚，请检查部门、职级或版本冲突"); }
            }
            states.finish(lease,false);
        } catch(SyncRunStateService.LeaseLostException ex) {
            // 旧执行器立即停止，不允许再改写新执行器的状态或结果。
        } catch(RuntimeException ex) {
            try { states.failed(lease,"BATCH","batch","快照读取或部门树写入失败，组织未按不完整快照继续更新"); states.finish(lease,true); }
            catch(SyncRunStateService.LeaseLostException lost) { /* 新执行器已接管，旧结果丢弃。 */ }
        }
    }
    /** 查询脱敏批次状态。 */
    public SyncStatus status(String runNo) {
        var row=runs.selectOne(Wrappers.<SyncRunDO>lambdaQuery().eq(SyncRunDO::getRunNo,runNo));
        if(row==null) throw new BizException(ErrorCode.RESOURCE_NOT_FOUND,"同步批次不存在");
        return new SyncStatus(row.getRunNo(),row.getStatus(),row.getUserCount()==null?0:row.getUserCount(),row.getFinishedAt()==null?null:row.getFinishedAt().toInstant(ZoneOffset.UTC),row.getErrorMessage(),row.getFailedCount()==null?0:row.getFailedCount());
    }
    /** 增量回源在事务外，复用同一写服务；业务提交后事件消费者才标为成功。 */
    public void applyEvent(String payload) {
        if(payload==null) throw new BizException(ErrorCode.VALIDATION_FAILED,"事件原文缺失");
        String change=xml(payload,"ChangeType"), userId=xml(payload,"UserID");
        var observed=LocalDateTime.now(); var eventAt=eventTime(payload);
        if("create_user".equals(change) || "update_user".equals(change)) {
            var departments=client.listDepartments(client.getToken().access_token()).department();
            var user=client.user(userId);
            writer.departments(departments,new WeComDirectoryWriter.WriteContext(null,observed));
            writer.employee(user,new WeComDirectoryWriter.WriteContext(null,observed,eventAt));
        } else if("delete_user".equals(change) || "delete_party".equals(change)) {
            writer.deactivate("delete_user".equals(change)?"EMPLOYEE":"DEPARTMENT","delete_user".equals(change)?userId:xml(payload,"Id"),eventAt);
        } else if("create_party".equals(change) || "update_party".equals(change)) {
            var departments=client.listDepartments(client.getToken().access_token()).department();
            writer.departments(departments,new WeComDirectoryWriter.WriteContext(null,observed,eventAt));
        } else throw new BizException(ErrorCode.VALIDATION_FAILED,"暂不支持此事件类型");
    }
    private LocalDateTime eventTime(String payload) {
        try { return LocalDateTime.ofInstant(Instant.ofEpochSecond(Long.parseLong(xml(payload,"CreateTime"))),ZoneOffset.UTC); }
        catch(RuntimeException ex) { throw new BizException(ErrorCode.VALIDATION_FAILED,"事件缺少有效源时间戳"); }
    }
    private String xml(String payload,String tag) {
        try {
            var factory=javax.xml.parsers.DocumentBuilderFactory.newInstance(); factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true); factory.setFeature("http://xml.org/sax/features/external-general-entities",false); factory.setFeature("http://xml.org/sax/features/external-parameter-entities",false); factory.setXIncludeAware(false); factory.setExpandEntityReferences(false);
            var document=factory.newDocumentBuilder().parse(new java.io.ByteArrayInputStream(payload.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
            var nodes=document.getElementsByTagName(tag); return nodes.getLength()==0?"":nodes.item(0).getTextContent();
        } catch(Exception ex) { throw new BizException(ErrorCode.VALIDATION_FAILED,"事件XML不合法"); }
    }
    /** 处理对象数包含已更新或已有较新版本而跳过的员工。 */
    public record SyncStatus(String runId,String status,int userCount,Instant finishedAt,String error,int failedCount) { }
}
