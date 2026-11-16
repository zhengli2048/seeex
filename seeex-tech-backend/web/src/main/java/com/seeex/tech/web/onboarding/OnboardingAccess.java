package com.seeex.tech.web.onboarding;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.seeex.tech.common.api.ErrorCode;
import com.seeex.tech.common.exception.BizException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;
import java.util.*;
import static com.seeex.tech.web.onboarding.OnboardingContract.*;
import static com.seeex.tech.web.onboarding.OnboardingJson.*;
/** 所有申请详情、图、附件和交付共享对象边界；配置权限不意味着实例读取权限。 */
@Component @RequiredArgsConstructor
public class OnboardingAccess {
    private final OnboardingStore store;
    private final OnboardingDirectory directory;
    private final OnboardingDefinitionService definitions;
    private final OnboardingJson json;
    /** 读取未删除业务记录。 */
    public OnboardingRequestDO request(long id) { var row=store.requests.selectById(id); if(row==null || row.getDeletedAt()!=null) throw new BizException(ErrorCode.RESOURCE_NOT_FOUND,"申请不存在"); return row; }
    /** 详情和附属资料的同一ACL。 */
    public OnboardingRequestDO view(long actor,long id) {
        var row=request(id); directory.active(actor);
        boolean own=Objects.equals(actor,row.getApplicantId()) && directory.can(actor,"app-requests:own:view");
        boolean task=directory.can(actor,"workflow:tasks:view") && store.tasks.selectCount(Wrappers.<OnboardingTaskDO>lambdaQuery().eq(OnboardingTaskDO::getRequestId,id).eq(OnboardingTaskDO::getCandidateId,actor))>0;
        boolean fulfillment=directory.can(actor,"app-fulfillments:view") && (Objects.equals(actor,row.getFulfillerId()) || store.participants.selectCount(Wrappers.<OnboardingParticipantDO>lambdaQuery().eq(OnboardingParticipantDO::getRequestId,id).eq(OnboardingParticipantDO::getActorId,actor))>0);
        if(!own && !task && !fulfillment && !directory.can(actor,"app-requests:manage")) throw new BizException(ErrorCode.FORBIDDEN);
        return row;
    }
    /** 只能本人编辑，不能代申请；角色仍独立校验。 */
    public OnboardingRequestDO own(long actor,long id) { var row=request(id); directory.require(actor,"app-requests:create"); if(!Objects.equals(actor,row.getApplicantId())) throw new BizException(ErrorCode.FORBIDDEN); return row; }
    /** CAS锁定申请；审批、撤回、办理、领取等都在同一行上串行。 */
    public void lock(OnboardingRequestDO row,Long version) {
        if(version==null || version<0 || version==Long.MAX_VALUE || !version.equals(row.getRowVersion())) throw conflict();
        if(store.requests.update(null,Wrappers.<OnboardingRequestDO>lambdaUpdate().eq(OnboardingRequestDO::getId,row.getId()).eq(OnboardingRequestDO::getRowVersion,version).eq(OnboardingRequestDO::getState,row.getState()).isNull(OnboardingRequestDO::getDeletedAt).set(OnboardingRequestDO::getRowVersion,version+1).set(OnboardingRequestDO::getUpdatedAt,LocalDateTime.now()))!=1) throw conflict(); row.setRowVersion(version+1); row.setUpdatedAt(LocalDateTime.now());
    }
    /** 调用者已持有申请锁，保存派生状态。 */
    public void save(OnboardingRequestDO row) { store.requests.updateById(row); }
    /** 固定模型，不因停用新入口而改变在途申请。 */
    public FlowModel model(OnboardingRequestDO row) { return json.read(definitions.require(row.getDefinitionId()).getModelJson(),FlowModel.class); }
    /** 任务候选快照外，还验证当前来源资格；申请人永不自审批。 */
    public boolean reviewer(long actor,OnboardingRequestDO row,OnboardingTaskDO task) {
        if(Objects.equals(actor,row.getApplicantId()) || !Objects.equals(task.getCandidateId(),actor)) return false;
        var model=model(row); if(task.getNodeIndex()<0 || task.getNodeIndex()>=model.nodes().size()) return false;
        try { return directory.configuredCandidate(actor,model.nodes().get(task.getNodeIndex()).reviewers(),row.getDepartmentId(),row.getApplicantId(),OnboardingDirectory.CandidateKind.REVIEW); } catch(BizException ex) { return false; }
    }
    /** 办理要求已批准候选快照与当前来源同时有效，办理人不能是申请人。 */
    public boolean fulfillmentCandidate(long actor,OnboardingRequestDO row) {
        if(row.getDefinitionId()==null || row.getFulfillmentCandidates()==null || Objects.equals(actor,row.getApplicantId())) return false;
        var candidates=json.read(row.getFulfillmentCandidates(),Long[].class);
        try { return Arrays.asList(candidates).contains(actor) && directory.configuredCandidate(actor,model(row).fulfillers(),row.getDepartmentId(),row.getApplicantId(),OnboardingDirectory.CandidateKind.FULFILL); } catch(BizException ex) { return false; }
    }
    /** 异常处置还须是固定流程中配置的实时运维候选，不可直接批准。 */
    public boolean operator(long actor,OnboardingRequestDO row) { if(row.getDefinitionId()==null)return false;try{return directory.configuredCandidate(actor,model(row).operators(),row.getDepartmentId(),null,OnboardingDirectory.CandidateKind.OPERATE);}catch(BizException ex){return false;} }
    /** 幂等键绑定动作、对象和正文，返回先前业务对象而不重做副作用。 */
    public Long previous(long actor,String operation,String key,Object payload,Long requestId) {
        checkKey(key); var row=store.actions.selectOne(Wrappers.<OnboardingActionDO>lambdaQuery().eq(OnboardingActionDO::getActorId,actor).eq(OnboardingActionDO::getOperation,operation).eq(OnboardingActionDO::getIdempotencyKey,key));
        if(row==null) return null;
        if(!Objects.equals(row.getPayloadHash(),json.hash(json.write(payload))) || requestId!=null && !Objects.equals(requestId,row.getRequestId())) throw new BizException(ErrorCode.CONFLICT,"幂等键已用于不同请求，请勿重复复用"); return row.getRequestId();
    }
    /** 成功副作用与幂等结果同事务写入。 */
    public void remember(long actor,String operation,String key,Object payload,long requestId) {
        var row=new OnboardingActionDO(); row.setActorId(actor); row.setOperation(operation); row.setIdempotencyKey(key); row.setPayloadHash(json.hash(json.write(payload))); row.setRequestId(requestId); row.setCreatedAt(LocalDateTime.now()); store.actions.insert(row);
    }
    /** 持久化无凭证的不可变业务历史。 */
    public void log(OnboardingRequestDO request,long actor,String action,String comment) { var row=new OnboardingLogDO(); row.setRequestId(request.getId()); row.setAttemptNo(request.getAttemptNo()); row.setActorId(actor); row.setAction(action); row.setComment(comment); row.setCreatedAt(LocalDateTime.now()); store.logs.insert(row); }
    /** 安全意见，驳回、退回、撤回、补发和运维动作必须说明原因。 */
    public void comment(String value,boolean required) { if(required && (value==null || value.isBlank()) || value!=null && value.length()>1000) throw invalid("请填写原因或意见（最多1000字）"); }
    private void checkKey(String key) { if(key==null || !key.matches("[A-Za-z0-9._:-]{8,128}")) throw invalid("请提供8–128位Idempotency-Key"); }
}
