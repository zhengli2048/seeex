package com.seeex.tech.web.onboarding;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.seeex.tech.common.api.ErrorCode;
import com.seeex.tech.common.exception.BizException;
import com.seeex.tech.web.registry.ApplicationRegistryService;
import com.seeex.tech.web.registry.mapper.ApplicationMapper;
import com.seeex.tech.web.registry.entity.ApplicationDO;
import com.seeex.tech.web.governance.ApplicationAdminMapper;
import com.seeex.tech.web.governance.ApplicationAdminDO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.*;
import static com.seeex.tech.web.onboarding.OnboardingContract.*;
import static com.seeex.tech.web.onboarding.OnboardingJson.*;

/** 申请、审批和办理状态机；公开写方法受Spring代理事务保护。 */
@Service @RequiredArgsConstructor
public class OnboardingService {
    private static final int MAX_ATTEMPTS=50;
    private final OnboardingStore store;
    private final OnboardingDirectory directory;
    private final OnboardingAccess access;
    private final OnboardingValidation validation;
    private final OnboardingDefinitionService definitions;
    private final OnboardingNotificationService notifications;
    private final OnboardingDeliveryService deliveries;
    private final OnboardingJson json;
    private final ApplicationRegistryService registry;
    private final ApplicationMapper applications;
    private final ApplicationAdminMapper admins;

    /** 本人、平台管理和本人办理队列，权限在数据库分页前过滤。 */
    public PageView<RequestItem> list(long actor,ListQuery filter) {
        var query=Wrappers.<OnboardingRequestDO>lambdaQuery().isNull(OnboardingRequestDO::getDeletedAt);
        if("MANAGE".equals(filter.scope())) directory.require(actor,"app-requests:manage");
        else if("FULFILL".equals(filter.scope())) {
            directory.require(actor,"app-fulfillments:view");
            var ids=store.participants.selectList(Wrappers.<OnboardingParticipantDO>lambdaQuery().eq(OnboardingParticipantDO::getActorId,actor)).stream().map(OnboardingParticipantDO::getRequestId).distinct().toList();
            if(ids.isEmpty()) return new PageView<>(List.of(),0); query.in(OnboardingRequestDO::getId,ids);
        } else { directory.require(actor,"app-requests:own:view"); query.eq(OnboardingRequestDO::getApplicantId,actor); }
        query.eq(filter.state()!=null && !filter.state().isBlank(),OnboardingRequestDO::getState,filter.state()).and(filter.keyword()!=null && !filter.keyword().isBlank(),q->q.like(OnboardingRequestDO::getRequestNo,filter.keyword()).or().like(OnboardingRequestDO::getAppCode,filter.keyword()).or().like(OnboardingRequestDO::getDataJson,filter.keyword()));
        dates(query,filter); query.orderByDesc(OnboardingRequestDO::getId); var page=store.requests.selectPage(new Page<>(filter.page(),filter.pageSize()),query);
        var names=names(page.getRecords().stream().map(OnboardingRequestDO::getApplicantId).toList()); return new PageView<>(page.getRecords().stream().map(row->item(row,names)).toList(),page.getTotal());
    }
    /** 本人任务按候选快照索引，处理时再核对实时角色/部门资格。 */
    public PageView<TaskView> tasks(long actor,ListQuery filter) {
        directory.require(actor,"workflow:tasks:view"); var query=Wrappers.<OnboardingTaskDO>lambdaQuery().eq(OnboardingTaskDO::getCandidateId,actor);
        if("DONE".equals(filter.category())) query.notIn(OnboardingTaskDO::getState,List.of("PENDING","CLAIMED")); else query.in(OnboardingTaskDO::getState,List.of("PENDING","CLAIMED"));
        var page=store.tasks.selectPage(new Page<>(filter.page(),filter.pageSize()),query.orderByDesc(OnboardingTaskDO::getId)); var ids=page.getRecords().stream().map(OnboardingTaskDO::getRequestId).distinct().toList(); var requests=new HashMap<Long,OnboardingRequestDO>(); if(!ids.isEmpty())store.requests.selectList(Wrappers.<OnboardingRequestDO>lambdaQuery().in(OnboardingRequestDO::getId,ids)).forEach(row->requests.put(row.getId(),row));
        var names=names(List.of(actor)); return new PageView<>(page.getRecords().stream().map(row->task(row,requests.get(row.getRequestId()),actor,names)).toList(),page.getTotal());
    }
    /** 只创建本人草稿，初次保存幂等。 */
    @Transactional
    public RequestView create(long actor,ApplicationForm form,String key) {
        directory.require(actor,"app-requests:create"); directory.require(actor,"app-requests:own:view"); directory.require(actor,"app-deliveries:own:view"); directory.require(actor,"notifications:own:view"); validation.form(form,actor,false);
        Long previous=access.previous(actor,"CREATE",key,form,null); if(previous!=null) return detail(actor,previous);
        var row=new OnboardingRequestDO(); row.setRequestNo("APP"+UUID.randomUUID().toString().replace("-","").substring(0,24)); row.setApplicantId(actor); row.setState("DRAFT"); row.setRowVersion(0L); row.setRevision(0); row.setAttemptNo(0); row.setNodeIndex(0); row.setAppCode(form.proposedAppCode()); row.setDataJson(json.write(form)); row.setCreatedAt(LocalDateTime.now()); row.setUpdatedAt(row.getCreatedAt()); store.requests.insert(row);
        access.remember(actor,"CREATE",key,form,row.getId()); access.log(row,actor,"CREATE_DRAFT",null); return detail(actor,row.getId());
    }
    /** 本人草稿/退回材料可编辑，历史提交修订保持不变。 */
    @Transactional
    public RequestView draft(long actor,long id,DraftCommand command) {
        var row=access.own(actor,id); state(row,"DRAFT","RETURNED"); validation.form(command.form(),actor,false); access.lock(row,command.expectedVersion()); row.setDataJson(json.write(command.form())); row.setAppCode(command.form().proposedAppCode()); access.save(row); access.log(row,actor,"SAVE_DRAFT",null); return detail(actor,id);
    }
    /** 仅从未提交的本人草稿允许软删除。 */
    @Transactional
    public void deleteDraft(long actor,long id,Long version) { var row=access.own(actor,id); state(row,"DRAFT"); access.lock(row,version); row.setDeletedAt(LocalDateTime.now()); row.setState("DELETED"); access.save(row); access.log(row,actor,"DELETE_DRAFT",null); }
    /** 编码及完整提交预检；不预留编码，不启动实例。 */
    public ValidationView validate(long actor,long id,ActionCommand command) {
        var row=access.own(actor,id); directory.require(actor,"app-deliveries:own:view"); directory.require(actor,"notifications:own:view"); state(row,"DRAFT","RETURNED"); if(!Objects.equals(command.expectedVersion(),row.getRowVersion())) throw conflict();
        var form=form(row); validation.form(form,actor,true); available(row); var definition=definitions.bound(form.environment()); return definitions.preview(definition,form,directory.department(actor),actor,row.getRowVersion());
    }
    /** 事务外附件检查的准备：幂等重放不重新校验已经完成的副作用。 */
    public boolean needsSubmitValidation(long actor,long id,ActionCommand command,String key) { access.own(actor,id); return access.previous(actor,"SUBMIT",key,command,id)==null; }
    /** 已完成办理的相同重放直接返回原业务对象，不再读取外部附件。 */
    public boolean needsCompletionValidation(long actor,long id,ActionCommand command,String key) { access.view(actor,id); return access.previous(actor,"COMPLETE",key,command,id)==null; }
    /** 提交锁定不可变修订和流程版本，编码预留与首个任务同事务创建。 */
    @Transactional
    public RequestView submit(long actor,long id,ActionCommand command,String key) {
        var row=access.own(actor,id); if(access.previous(actor,"SUBMIT",key,command,id)!=null) return detail(actor,id); state(row,"DRAFT","RETURNED");
        var preview=validate(actor,id,command); if(!Objects.equals(command.definitionId(),preview.definitionId())) throw new BizException(ErrorCode.CONFLICT,"审批流程已变化，请重新预检并确认");
        if(row.getAttemptNo()>=MAX_ATTEMPTS) throw invalid("该申请修订次数已达上限，请新建申请"); access.lock(row,command.expectedVersion());
        definitions.fenceBinding(form(row).environment(),preview.definitionId());
        var reservation=store.reservations.selectById(row.getAppCode()); if(reservation!=null && !Objects.equals(reservation.getRequestId(),id)) throw new BizException(ErrorCode.CONFLICT,"应用编码已被其他申请预留");
        if(reservation==null) { reservation=new OnboardingReservationDO(); reservation.setAppCode(row.getAppCode()); reservation.setRequestId(id); store.reservations.insert(reservation); }
        row.setDepartmentId(directory.department(actor)); row.setDefinitionId(preview.definitionId()); row.setAttemptNo(row.getAttemptNo()+1); row.setRevision(row.getRevision()+1); row.setDecision(null); row.setExceptionMessage(null); row.setFulfillmentJson(null); row.setFulfillerId(null); row.setFulfillmentCandidates(null); row.setSubmittedAt(LocalDateTime.now());
        revision(row); access.log(row,actor,"SUBMIT",null); advance(row,0); access.save(row); access.remember(actor,"SUBMIT",key,command,id); return detail(actor,id);
    }
    /** 撤回与最终通过在同一申请版本上竞争，不能撤回已批准办理。 */
    @Transactional
    public RequestView withdraw(long actor,long id,ActionCommand command) {
        var row=access.own(actor,id); state(row,"IN_APPROVAL"); access.comment(command.comment(),true); access.lock(row,command.expectedVersion()); cancelTasks(row); row.setState("WITHDRAWN"); row.setDecision("WITHDRAWN"); release(row); access.save(row); access.log(row,actor,"WITHDRAW",command.comment()); return detail(actor,id);
    }
    /** 领取本人候选任务，不能抢占其他人的任务。 */
    @Transactional
    public RequestView claimTask(long actor,long taskId,ActionCommand command) {
        var task=requireTask(taskId); var row=acting(actor,task); if(!"PENDING".equals(task.getState())) throw conflict(); access.lock(row,row.getRowVersion());
        if(store.tasks.update(null,Wrappers.<OnboardingTaskDO>lambdaUpdate().eq(OnboardingTaskDO::getId,taskId).eq(OnboardingTaskDO::getCandidateId,actor).eq(OnboardingTaskDO::getState,"PENDING").eq(OnboardingTaskDO::getRowVersion,command.expectedVersion()).set(OnboardingTaskDO::getAssigneeId,actor).set(OnboardingTaskDO::getState,"CLAIMED").set(OnboardingTaskDO::getRowVersion,task.getRowVersion()+1))!=1) throw conflict(); access.log(row,actor,"CLAIM_TASK",task.getNodeName()); return detail(actor,row.getId());
    }
    /** ALL一票否决；ANY首个通过使其余失效，全部拒绝才驳回。退回结束当前尝试。 */
    @Transactional
    public RequestView decision(long actor,long taskId,ActionCommand command) {
        var task=requireTask(taskId); var row=acting(actor,task); String decision=command.decision(); if(decision==null || !Set.of("APPROVE","REJECT","RETURN").contains(decision)) throw invalid("审批动作无效");
        if(!"CLAIMED".equals(task.getState()) || !Objects.equals(task.getAssigneeId(),actor)) throw conflict(); access.comment(command.comment(),!"APPROVE".equals(decision)); access.lock(row,row.getRowVersion());
        String outcome="APPROVE".equals(decision)?"APPROVED":"REJECT".equals(decision)?"REJECTED":"RETURNED";
        if(store.tasks.update(null,Wrappers.<OnboardingTaskDO>lambdaUpdate().eq(OnboardingTaskDO::getId,taskId).eq(OnboardingTaskDO::getRowVersion,command.expectedVersion()).eq(OnboardingTaskDO::getState,"CLAIMED").eq(OnboardingTaskDO::getAssigneeId,actor).set(OnboardingTaskDO::getState,outcome).set(OnboardingTaskDO::getCompletedAt,LocalDateTime.now()).set(OnboardingTaskDO::getRowVersion,task.getRowVersion()+1))!=1) throw conflict();
        access.log(row,actor,decision,task.getNodeName()+"："+Objects.toString(command.comment(),""));
        var pending=store.tasks.selectCount(Wrappers.<OnboardingTaskDO>lambdaQuery().eq(OnboardingTaskDO::getRequestId,row.getId()).eq(OnboardingTaskDO::getAttemptNo,row.getAttemptNo()).eq(OnboardingTaskDO::getNodeIndex,row.getNodeIndex()).in(OnboardingTaskDO::getState,List.of("PENDING","CLAIMED")));
        var next=OnboardingDecisionPolicy.resolve(task.getMode(),decision,pending);
        if(next==OnboardingDecisionPolicy.Outcome.RETURNED || next==OnboardingDecisionPolicy.Outcome.REJECTED) {
            cancelTasks(row); row.setState(outcome); row.setDecision(outcome); release(row); notifications.enqueue(row,row.getApplicantId(),outcome,"RETURNED".equals(outcome)?"申请需补充材料":"应用接入申请已驳回");
        } else if(next==OnboardingDecisionPolicy.Outcome.ADVANCE) { cancelTasks(row); advance(row,row.getNodeIndex()+1); }
        access.save(row); return detail(actor,row.getId());
    }
    /** 领取已审批通过的办理，候选和实时功能权限缺一不可。 */
    @Transactional
    public RequestView claimFulfillment(long actor,long id,ActionCommand command) {
        var row=access.view(actor,id); directory.require(actor,"app-fulfillments:act"); state(row,"PENDING_FULFILLMENT"); if(!access.fulfillmentCandidate(actor,row)) throw new BizException(ErrorCode.FORBIDDEN); directory.active(row.getApplicantId()); access.lock(row,command.expectedVersion()); row.setState("FULFILLING"); row.setFulfillerId(actor); access.save(row); access.log(row,actor,"CLAIM_FULFILLMENT",null); return detail(actor,id);
    }
    /** 配置草稿可以不完整，完成前另行执行批准范围校验。 */
    @Transactional
    public RequestView config(long actor,long id,FulfillmentCommand command) {
        var row=fulfilling(actor,id); validation.fulfillmentDraft(command.form()); if(command.form()==null || json.write(command.form()).length()>10000) throw invalid("配置草稿无效或过长"); access.lock(row,command.expectedVersion()); row.setFulfillmentJson(json.write(command.form())); access.save(row); access.log(row,actor,"SAVE_FULFILLMENT",null); return detail(actor,id);
    }
    /** 校验保存后的配置，不能以本地未保存草稿假冒办理结果。 */
    public RequestView validateFulfillment(long actor,long id,ActionCommand command) { var row=fulfilling(actor,id); if(!Objects.equals(command.expectedVersion(),row.getRowVersion())) throw conflict(); validateCompletion(row); return detail(actor,id); }
    /** 注册、批准管理员、加密交付、站内消息和幂等结果在同一事务提交。 */
    @Transactional
    public RequestView complete(long actor,long id,ActionCommand command,String key) {
        if(access.previous(actor,"COMPLETE",key,command,id)!=null) return detail(actor,id); var row=fulfilling(actor,id); validateCompletion(row); access.lock(row,command.expectedVersion());
        var form=form(row); var config=json.read(row.getFulfillmentJson(),FulfillmentForm.class);
        var registration=registry.registerForRequest(new ApplicationRegistryService.RegisterAppCommand(row.getAppCode(),form.name(),config.baseUrl(),config.repositoryUrl(),form.description(),form.type(),config.logo(),row.getApplicantId(),"ACTIVE",config.adminUrl(),config.apiUrl(),config.ssoCallbackPath()),id);
        for(Long employeeId:form.requestedAdminEmployeeIds()) { var admin=new ApplicationAdminDO(); admin.setApplicationCode(row.getAppCode()); admin.setEmployeeId(employeeId); admins.insert(admin); }
        deliveries.create(row,registration); row.setState("PENDING_RECEIPT"); access.save(row); access.log(row,actor,"COMPLETE_FULFILLMENT",null); access.remember(actor,"COMPLETE",key,command,id); return detail(actor,id);
    }
    /** 异常恢复只重新解析候选或重新发起完整审批，不能直接改成通过。 */
    @Transactional
    public RequestView recover(long actor,long id,ActionCommand command) {
        directory.require(actor,"app-requests:manage"); var row=access.view(actor,id); state(row,"EXCEPTION"); if(!access.operator(actor,row))throw new BizException(ErrorCode.FORBIDDEN,"不是该流程配置的运维候选"); access.comment(command.comment(),true); directory.active(row.getApplicantId()); access.lock(row,command.expectedVersion());
        row.setExceptionMessage(null); cancelTasks(row);
        if("APPROVED".equals(row.getDecision())) readyFulfillment(row);
        else { if(row.getAttemptNo()>=MAX_ATTEMPTS) throw invalid("恢复次数已达上限"); row.setAttemptNo(row.getAttemptNo()+1); row.setRevision(row.getRevision()+1); revision(row); advance(row,0); }
        access.save(row); access.log(row,actor,"RECOVER",command.comment()); return detail(actor,id);
    }
    /** 未注册前的受控取消释放编码，不删除已创建应用，不改写既有审批结论。 */
    @Transactional
    public RequestView cancel(long actor,long id,ActionCommand command) {
        directory.require(actor,"app-requests:manage");var row=access.view(actor,id);state(row,"PENDING_FULFILLMENT","FULFILLING","EXCEPTION");if(!access.operator(actor,row))throw new BizException(ErrorCode.FORBIDDEN);access.comment(command.comment(),true);access.lock(row,command.expectedVersion());
        if(applications.selectCount(Wrappers.<ApplicationDO>lambdaQuery().eq(ApplicationDO::getAppCode,row.getAppCode()))>0)throw new BizException(ErrorCode.CONFLICT,"已经创建应用，不能通过取消申请删除或释放编码");
        cancelTasks(row);row.setState("CANCELLED");row.setExceptionMessage(null);release(row);access.save(row);access.log(row,actor,"CANCEL_FULFILLMENT",command.comment());notifications.enqueue(row,row.getApplicantId(),"cancelled","未交付的接入申请已取消，详情含办理原因");return detail(actor,id);
    }
    /** 详情、历史版本、只读流程和按钮均使用相同对象ACL。 */
    public RequestView detail(long actor,long id) {
        var row=access.view(actor,id); var tasks=store.tasks.selectList(Wrappers.<OnboardingTaskDO>lambdaQuery().eq(OnboardingTaskDO::getRequestId,id).eq(OnboardingTaskDO::getAttemptNo,row.getAttemptNo()).orderByAsc(OnboardingTaskDO::getId)); var logs=store.logs.selectPage(new Page<>(1,1000,false),Wrappers.<OnboardingLogDO>lambdaQuery().eq(OnboardingLogDO::getRequestId,id).orderByDesc(OnboardingLogDO::getId)).getRecords();
        var ids=new HashSet<Long>(); ids.add(row.getApplicantId()); ids.addAll(form(row).requestedAdminEmployeeIds()); if(row.getFulfillerId()!=null)ids.add(row.getFulfillerId()); tasks.forEach(task->ids.add(task.getCandidateId())); logs.forEach(log->ids.add(log.getActorId())); var names=names(ids); var revisions=store.revisions.selectList(Wrappers.<OnboardingRevisionDO>lambdaQuery().eq(OnboardingRevisionDO::getRequestId,id).orderByDesc(OnboardingRevisionDO::getRevision));
        Set<String> actions=new HashSet<>(); boolean own=Objects.equals(actor,row.getApplicantId()), edit=directory.can(actor,"app-requests:create");
        if(own && edit && Set.of("DRAFT","RETURNED").contains(row.getState())) { actions.add("EDIT"); if(directory.can(actor,"app-deliveries:own:view") && directory.can(actor,"notifications:own:view"))actions.addAll(Set.of("VALIDATE","SUBMIT")); }
        if(own && edit && "DRAFT".equals(row.getState()))actions.add("DELETE"); if(own && edit && "IN_APPROVAL".equals(row.getState()))actions.add("WITHDRAW"); if(own && edit && Set.of("REJECTED","WITHDRAWN","COMPLETED","CANCELLED").contains(row.getState()))actions.add("COPY");
        boolean fulfill=directory.can(actor,"app-fulfillments:act") && access.fulfillmentCandidate(actor,row);
        if(fulfill && "PENDING_FULFILLMENT".equals(row.getState()))actions.add("CLAIM_FULFILLMENT");
        if(fulfill && Objects.equals(actor,row.getFulfillerId()) && "FULFILLING".equals(row.getState()))actions.addAll(Set.of("SAVE_CONFIG","VALIDATE_CONFIG","COMPLETE"));
        if(Set.of("PENDING_FULFILLMENT","FULFILLING","EXCEPTION").contains(row.getState()) && directory.can(actor,"app-requests:manage") && access.operator(actor,row))actions.add("CANCEL");
        if("EXCEPTION".equals(row.getState()) && directory.can(actor,"app-requests:manage") && access.operator(actor,row))actions.add("RECOVER");
        if(own && directory.can(actor,"app-deliveries:own:view") && Set.of("PENDING_RECEIPT","COMPLETED").contains(row.getState()))actions.add("DELIVERY");
        if("PENDING_RECEIPT".equals(row.getState()) && directory.can(actor,"app-deliveries:reissue") && fulfill)actions.add("REISSUE");
        return new RequestView(item(row,names),form(row),form(row).requestedAdminEmployeeIds().stream().map(idValue->new Option(idValue,names.getOrDefault(idValue,"已失效人员"))).toList(),json.read(row.getFulfillmentJson(),FulfillmentForm.class),row.getFulfillerId(),names.get(row.getFulfillerId()),row.getExceptionMessage(),row.getDefinitionId(),row.getDefinitionId()==null?null:access.model(row),row.getNodeIndex(),tasks.stream().map(task->task(task,row,actor,names)).toList(),logs.stream().map(log->new HistoryView(log.getId(),log.getActorId(),names.getOrDefault(log.getActorId(),"系统"),log.getAction(),log.getComment(),log.getAttemptNo(),log.getCreatedAt())).toList(),revisions.stream().map(rev->new RevisionView(rev.getRevision(),rev.getDefinitionId(),json.read(rev.getDataJson(),ApplicationForm.class),rev.getSubmittedAt())).toList(),actions);
    }
    /** 读取固定修订的完整审批路径与意见，沿用申请参与者ACL。 */
    public AttemptView attempt(long actor,long id,int attempt) {
        var request=access.view(actor,id); var revision=store.revisions.selectOne(Wrappers.<OnboardingRevisionDO>lambdaQuery().eq(OnboardingRevisionDO::getRequestId,id).eq(OnboardingRevisionDO::getRevision,attempt));
        if(revision==null)throw new BizException(ErrorCode.RESOURCE_NOT_FOUND,"审批尝试不存在");
        var tasks=store.tasks.selectList(Wrappers.<OnboardingTaskDO>lambdaQuery().eq(OnboardingTaskDO::getRequestId,id).eq(OnboardingTaskDO::getAttemptNo,attempt).orderByAsc(OnboardingTaskDO::getId)); var logs=store.logs.selectList(Wrappers.<OnboardingLogDO>lambdaQuery().eq(OnboardingLogDO::getRequestId,id).eq(OnboardingLogDO::getAttemptNo,attempt).orderByAsc(OnboardingLogDO::getId)); var ids=new HashSet<Long>(); tasks.forEach(task->ids.add(task.getCandidateId()));logs.forEach(log->ids.add(log.getActorId()));var names=names(ids);var form=json.read(revision.getDataJson(),ApplicationForm.class);var flow=json.read(definitions.require(revision.getDefinitionId()).getModelJson(),FlowModel.class);
        return new AttemptView(attempt,form,flow,tasks.stream().map(task->new TaskView(task.getId(),id,request.getRequestNo(),form.name(),task.getAttemptNo(),task.getNodeIndex(),task.getNodeName(),task.getMode(),task.getState(),task.getRowVersion(),task.getCandidateId(),names.get(task.getCandidateId()),task.getDueAt(),Set.of())).toList(),logs.stream().map(log->new HistoryView(log.getId(),log.getActorId(),names.getOrDefault(log.getActorId(),"系统"),log.getAction(),log.getComment(),log.getAttemptNo(),log.getCreatedAt())).toList());
    }
    private OnboardingRequestDO fulfilling(long actor,long id) { var row=access.view(actor,id); directory.require(actor,"app-fulfillments:act"); state(row,"FULFILLING"); if(!Objects.equals(row.getFulfillerId(),actor) || !access.fulfillmentCandidate(actor,row)) throw new BizException(ErrorCode.FORBIDDEN); directory.active(row.getApplicantId()); return row; }
    private OnboardingRequestDO acting(long actor,OnboardingTaskDO task) { directory.require(actor,"workflow:tasks:act"); var row=access.view(actor,task.getRequestId()); state(row,"IN_APPROVAL"); directory.active(row.getApplicantId()); if(!Objects.equals(row.getAttemptNo(),task.getAttemptNo()) || !Objects.equals(row.getNodeIndex(),task.getNodeIndex()) || !access.reviewer(actor,row,task)) throw new BizException(ErrorCode.FORBIDDEN,"当前身份不再具备该任务资格"); return row; }
    private OnboardingTaskDO requireTask(long id) { var row=store.tasks.selectById(id); if(row==null)throw new BizException(ErrorCode.RESOURCE_NOT_FOUND,"任务不存在"); return row; }
    private void advance(OnboardingRequestDO row,int start) {
        var model=access.model(row); var form=form(row);
        for(int index=start;index<model.nodes().size();index++) {
            var node=model.nodes().get(index); row.setNodeIndex(index); if(!validation.matches(node,form)) { access.log(row,0L,"SKIP_CONDITION",node.name()); continue; }
            var candidates=directory.qualified(node.reviewers(),row.getDepartmentId(),row.getApplicantId(),OnboardingDirectory.CandidateKind.REVIEW); candidates.removeIf(candidate->!directory.can(candidate,"workflow:tasks:act") || !directory.can(candidate,"workflow:tasks:view")); if(candidates.isEmpty()) { exception(row,"节点“"+node.name()+"”无有效审批人，请流程运维处理"); return; }
            row.setState("IN_APPROVAL");
            for(Long candidate:candidates) { var task=new OnboardingTaskDO(); task.setRequestId(row.getId()); task.setAttemptNo(row.getAttemptNo()); task.setNodeIndex(index); task.setNodeName(node.name()); task.setMode(node.mode()); task.setCandidateId(candidate); task.setState("PENDING"); task.setRowVersion(0L); task.setDueAt(LocalDateTime.now().plusHours(node.dueHours())); task.setCreatedAt(LocalDateTime.now()); store.tasks.insert(task); notifications.enqueue(row,candidate,"task:"+index,"有新的应用接入审批待办"); }
            return;
        }
        row.setNodeIndex(model.nodes().size()); row.setDecision("APPROVED"); readyFulfillment(row);
    }
    private void readyFulfillment(OnboardingRequestDO row) {
        var candidates=directory.qualified(access.model(row).fulfillers(),row.getDepartmentId(),row.getApplicantId(),OnboardingDirectory.CandidateKind.FULFILL); candidates.removeIf(candidate->!directory.can(candidate,"app-fulfillments:act") || !directory.can(candidate,"app-fulfillments:view")); if(candidates.isEmpty()) { exception(row,"审批已通过，但无有效平台办理人"); return; }
        row.setState("PENDING_FULFILLMENT"); row.setFulfillmentCandidates(json.write(candidates)); row.setFulfillerId(null);
        for(Long candidate:candidates) { if(store.participants.selectCount(Wrappers.<OnboardingParticipantDO>lambdaQuery().eq(OnboardingParticipantDO::getRequestId,row.getId()).eq(OnboardingParticipantDO::getActorId,candidate).eq(OnboardingParticipantDO::getAttemptNo,row.getAttemptNo()))==0) { var participant=new OnboardingParticipantDO(); participant.setRequestId(row.getId()); participant.setActorId(candidate); participant.setAttemptNo(row.getAttemptNo()); store.participants.insert(participant); } notifications.enqueue(row,candidate,"fulfill","有新的应用接入待办理"); }
        notifications.enqueue(row,row.getApplicantId(),"approved","应用接入审批通过，等待平台办理");
    }
    private void exception(OnboardingRequestDO row,String reason) { row.setState("EXCEPTION"); row.setExceptionMessage(reason); notifications.exception(row); }
    private void validateCompletion(OnboardingRequestDO row) { directory.active(row.getApplicantId()); validation.form(form(row),row.getApplicantId(),true); validation.fulfillment(json.read(row.getFulfillmentJson(),FulfillmentForm.class),form(row)); deliveries.requireConfigured(); var reservation=store.reservations.selectById(row.getAppCode()); if(reservation==null || !Objects.equals(reservation.getRequestId(),row.getId())) throw new BizException(ErrorCode.CONFLICT,"应用编码预留已失效"); available(row); }
    private void available(OnboardingRequestDO row) { if(applications.selectCount(Wrappers.<ApplicationDO>lambdaQuery().eq(ApplicationDO::getAppCode,row.getAppCode()))>0) throw new BizException(ErrorCode.CONFLICT,"应用编码已注册"); var reservation=store.reservations.selectById(row.getAppCode()); if(reservation!=null && !Objects.equals(reservation.getRequestId(),row.getId()))throw new BizException(ErrorCode.CONFLICT,"应用编码已被申请预留"); }
    private void release(OnboardingRequestDO row) { store.reservations.delete(Wrappers.<OnboardingReservationDO>lambdaQuery().eq(OnboardingReservationDO::getAppCode,row.getAppCode()).eq(OnboardingReservationDO::getRequestId,row.getId())); }
    private void revision(OnboardingRequestDO row) { var revision=new OnboardingRevisionDO(); revision.setRequestId(row.getId()); revision.setRevision(row.getRevision()); revision.setDefinitionId(row.getDefinitionId()); revision.setDataJson(row.getDataJson()); revision.setSubmittedAt(LocalDateTime.now()); store.revisions.insert(revision); }
    private void cancelTasks(OnboardingRequestDO row) { store.tasks.update(null,Wrappers.<OnboardingTaskDO>lambdaUpdate().eq(OnboardingTaskDO::getRequestId,row.getId()).eq(OnboardingTaskDO::getAttemptNo,row.getAttemptNo()).in(OnboardingTaskDO::getState,List.of("PENDING","CLAIMED")).set(OnboardingTaskDO::getState,"CANCELLED").set(OnboardingTaskDO::getCompletedAt,LocalDateTime.now())); }
    private ApplicationForm form(OnboardingRequestDO row) { return json.read(row.getDataJson(),ApplicationForm.class); }
    private Map<Long,String> names(Collection<Long> ids) { var names=new HashMap<Long,String>(); directory.names(ids).forEach(option->names.put(option.id(),option.name())); return names; }
    private RequestItem item(OnboardingRequestDO row,Map<Long,String> names) { return new RequestItem(row.getId(),row.getRequestNo(),form(row).name(),row.getAppCode(),row.getApplicantId(),names.get(row.getApplicantId()),row.getState(),row.getDecision(),row.getRowVersion(),row.getAttemptNo(),row.getCreatedAt(),row.getSubmittedAt()); }
    private TaskView task(OnboardingTaskDO task,OnboardingRequestDO row,long actor,Map<Long,String> names) { Set<String> actions=new HashSet<>(); if(row!=null && "IN_APPROVAL".equals(row.getState()) && Objects.equals(row.getAttemptNo(),task.getAttemptNo()) && Objects.equals(row.getNodeIndex(),task.getNodeIndex()) && Objects.equals(task.getCandidateId(),actor) && directory.can(actor,"workflow:tasks:act") && access.reviewer(actor,row,task)) { if("PENDING".equals(task.getState()))actions.add("CLAIM"); if("CLAIMED".equals(task.getState()) && Objects.equals(actor,task.getAssigneeId()))actions.addAll(Set.of("APPROVE","REJECT","RETURN")); } return new TaskView(task.getId(),task.getRequestId(),row==null?"":row.getRequestNo(),row==null?"":form(row).name(),task.getAttemptNo(),task.getNodeIndex(),task.getNodeName(),task.getMode(),task.getState(),task.getRowVersion(),task.getCandidateId(),names.get(task.getCandidateId()),task.getDueAt(),actions); }
    private void state(OnboardingRequestDO row,String... allowed) { if(!Arrays.asList(allowed).contains(row.getState())) throw conflict(); }
    private void dates(com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<OnboardingRequestDO> query,ListQuery filter) { try { var from=filter.from()==null || filter.from().isBlank()?null:LocalDateTime.parse(filter.from()); var to=filter.to()==null || filter.to().isBlank()?null:LocalDateTime.parse(filter.to()); if(from!=null && to!=null && from.isAfter(to)) throw invalid("开始时间不能晚于结束时间"); if(from!=null)query.ge(OnboardingRequestDO::getCreatedAt,from);if(to!=null)query.le(OnboardingRequestDO::getCreatedAt,to); } catch(java.time.format.DateTimeParseException ex) { throw invalid("日期格式无效"); } }
}
