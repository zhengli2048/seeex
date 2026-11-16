package com.seeex.tech.web.onboarding;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.seeex.tech.common.api.ErrorCode;
import com.seeex.tech.common.exception.BizException;
import com.seeex.tech.web.governance.ConfigurationRevisionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;
import java.time.LocalDateTime;
import java.util.*;
import static com.seeex.tech.web.onboarding.OnboardingContract.*;
import static com.seeex.tech.web.onboarding.OnboardingJson.*;

/** 不变发布版本、单环境唯一绑定和纯只读流程预览。 */
@Service @RequiredArgsConstructor
public class OnboardingDefinitionService {
    private final OnboardingStore store;
    private final OnboardingDirectory directory;
    private final OnboardingValidation validation;
    private final OnboardingJson json;
    private final ConfigurationRevisionService revisions;
    /** 同一快照读取定义与绑定版本。 */
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public DefinitionPage list(long actor,ListQuery filter) {
        directory.require(actor,"workflow:definitions:view"); long version=revisions.current("onboarding-binding");
        var query=Wrappers.<OnboardingDefinitionDO>lambdaQuery().eq(filter.state()!=null && !filter.state().isBlank(),OnboardingDefinitionDO::getStatus,filter.state()).and(filter.keyword()!=null && !filter.keyword().isBlank(),q->q.like(OnboardingDefinitionDO::getName,filter.keyword()).or().like(OnboardingDefinitionDO::getDefinitionKey,filter.keyword())).orderByDesc(OnboardingDefinitionDO::getId);
        var page=store.definitions.selectPage(new Page<>(filter.page(),filter.pageSize()),query);
        return new DefinitionPage(page.getRecords().stream().map(this::view).toList(),page.getTotal(),version,bindings());
    }
    /** 流程模型只读详情，不授予任何实例或密钥读取权。 */
    public DefinitionView detail(long actor,long id) { directory.require(actor,"workflow:definitions:view"); return view(require(id)); }
    /** 创建独立草稿版本，或按草稿版本CAS保存；发布记录不可编辑。 */
    @Transactional
    public DefinitionView save(long actor,Long id,DefinitionCommand command,String key) {
        directory.require(actor,"workflow:definitions:write");
        if(command==null || command.definitionKey()==null || !command.definitionKey().matches("[a-z][a-z0-9-]{1,63}") || command.name()==null || command.name().isBlank() || command.name().length()>128 || command.model()==null || json.write(command.model()).length()>64000) throw invalid("流程编码、名称或模型无效");
        validation.draftModel(command.model());
        var now=LocalDateTime.now();
        if(id==null) {
            if(key==null || !key.matches("[A-Za-z0-9._:-]{8,128}"))throw invalid("创建流程需有效Idempotency-Key");
            var prior=store.definitions.selectOne(Wrappers.<OnboardingDefinitionDO>lambdaQuery().eq(OnboardingDefinitionDO::getCreatedBy,actor).eq(OnboardingDefinitionDO::getCreationKey,key));
            String fingerprint=json.hash(json.write(command));
            if(prior!=null){if(!fingerprint.equals(prior.getCreationHash()))throw new BizException(ErrorCode.CONFLICT,"创建幂等键已用于不同草稿");return view(prior);}
            if(command.sourceId()!=null) { var source=require(command.sourceId()); if(!source.getDefinitionKey().equals(command.definitionKey())) throw invalid("复制版本不能改写流程编码"); }
            var existing=store.definitions.selectPage(new Page<>(1,1,false),Wrappers.<OnboardingDefinitionDO>lambdaQuery().eq(OnboardingDefinitionDO::getDefinitionKey,command.definitionKey()).orderByDesc(OnboardingDefinitionDO::getVersion)).getRecords();
            var row=new OnboardingDefinitionDO(); row.setCreationKey(key); row.setCreationHash(fingerprint); row.setDefinitionKey(command.definitionKey()); row.setName(command.name()); row.setVersion(existing.isEmpty()?1:existing.getFirst().getVersion()+1); row.setStatus("DRAFT"); row.setModelJson(json.write(command.model())); row.setRowVersion(0L); row.setCreatedBy(actor); row.setCreatedAt(now); row.setUpdatedAt(now); store.definitions.insert(row); return view(row);
        }
        var row=require(id); expect(row,command.expectedVersion());
        if(!"DRAFT".equals(row.getStatus()) || !row.getDefinitionKey().equals(command.definitionKey())) throw conflict();
        if(store.definitions.update(null,Wrappers.<OnboardingDefinitionDO>lambdaUpdate().eq(OnboardingDefinitionDO::getId,id).eq(OnboardingDefinitionDO::getRowVersion,command.expectedVersion()).eq(OnboardingDefinitionDO::getStatus,"DRAFT").set(OnboardingDefinitionDO::getName,command.name()).set(OnboardingDefinitionDO::getModelJson,json.write(command.model())).set(OnboardingDefinitionDO::getRowVersion,command.expectedVersion()+1).set(OnboardingDefinitionDO::getUpdatedAt,now))!=1) throw conflict(); return view(require(id));
    }
    /** 校验草稿/版本，不产生实例或通知。 */
    public DefinitionView validate(long actor,long id,ActionCommand command) { directory.require(actor,"workflow:definitions:write"); var row=require(id); expect(row,command.expectedVersion()); validation.model(json.read(row.getModelJson(),FlowModel.class)); return view(row); }
    /** 预览某一申请场景，申请人固定为当前用户，候选身份不由前端传入。 */
    public ValidationView preview(long actor,long id,ApplicationForm form) { directory.require(actor,"workflow:definitions:write"); validation.form(form,actor,true); var row=require(id); validation.model(json.read(row.getModelJson(),FlowModel.class)); return preview(row,form,directory.department(actor),actor,0L); }
    /** 发布后模型不可变，环境绑定CAS替换，只影响新申请。 */
    @Transactional
    public DefinitionView publish(long actor,long id,PublishCommand command) {
        directory.require(actor,"workflow:definitions:publish"); var row=require(id); expect(row,command.expectedVersion());
        if(!"DRAFT".equals(row.getStatus()) || command.environments()==null || command.environments().isEmpty() || command.environments().stream().anyMatch(Objects::isNull) || !Set.of("DEV","TEST","PROD").containsAll(command.environments())) throw invalid("只能发布草稿并选择有效环境");
        if(command.comment()==null || command.comment().isBlank() || command.comment().length()>1000)throw invalid("请填写发布原因（最多1000字）");
        validation.model(json.read(row.getModelJson(),FlowModel.class)); revisions.advance("onboarding-binding",command.expectedBindingVersion());
        if(store.definitions.update(null,Wrappers.<OnboardingDefinitionDO>lambdaUpdate().eq(OnboardingDefinitionDO::getId,id).eq(OnboardingDefinitionDO::getStatus,"DRAFT").eq(OnboardingDefinitionDO::getRowVersion,command.expectedVersion()).set(OnboardingDefinitionDO::getStatus,"PUBLISHED").set(OnboardingDefinitionDO::getRowVersion,command.expectedVersion()+1).set(OnboardingDefinitionDO::getPublishedBy,actor).set(OnboardingDefinitionDO::getPublishedAt,LocalDateTime.now()))!=1) throw conflict();
        for(String env:command.environments()) { store.bindings.deleteById(env); var binding=new OnboardingBindingDO(); binding.setEnvironment(env); binding.setDefinitionId(id); binding.setRowVersion(0L); store.bindings.insert(binding); }
        return view(require(id));
    }
    /** 停用只解除本版本的新实例绑定，保留在途实例。 */
    @Transactional
    public DefinitionView disable(long actor,long id,PublishCommand command) {
        directory.require(actor,"workflow:definitions:publish"); var row=require(id); expect(row,command.expectedVersion()); if(!"PUBLISHED".equals(row.getStatus())) throw conflict();
        if(command.comment()==null || command.comment().isBlank() || command.comment().length()>1000) throw invalid("请填写停用原因");
        revisions.advance("onboarding-binding",command.expectedBindingVersion()); store.bindings.delete(Wrappers.<OnboardingBindingDO>lambdaQuery().eq(OnboardingBindingDO::getDefinitionId,id));
        if(store.definitions.update(null,Wrappers.<OnboardingDefinitionDO>lambdaUpdate().eq(OnboardingDefinitionDO::getId,id).eq(OnboardingDefinitionDO::getRowVersion,command.expectedVersion()).set(OnboardingDefinitionDO::getStatus,"DISABLED").set(OnboardingDefinitionDO::getRowVersion,command.expectedVersion()+1))!=1) throw conflict(); return view(require(id));
    }
    /** 根据有效环境绑定唯一选择流程。 */
    public OnboardingDefinitionDO bound(String environment) { var binding=store.bindings.selectById(environment); var definition=binding==null?null:store.definitions.selectById(binding.getDefinitionId()); if(definition==null || !"PUBLISHED".equals(definition.getStatus())) throw invalid("当前环境没有已发布审批流程，请联系流程管理员"); return definition; }
    /** 在提交事务中锁定环境绑定，发布/停用只能在该提交之后生效。 */
    @Transactional(propagation=org.springframework.transaction.annotation.Propagation.MANDATORY)
    public void fenceBinding(String environment,long expectedDefinition) {
        var row=store.bindings.selectById(environment);
        if(row==null || !java.util.Objects.equals(row.getDefinitionId(),expectedDefinition))throw conflict();
        if(store.bindings.update(null,Wrappers.<OnboardingBindingDO>lambdaUpdate().eq(OnboardingBindingDO::getEnvironment,environment).eq(OnboardingBindingDO::getDefinitionId,expectedDefinition).eq(OnboardingBindingDO::getRowVersion,row.getRowVersion()).set(OnboardingBindingDO::getRowVersion,row.getRowVersion()+1))!=1)throw conflict();
    }
    /** 申请预检，在提交时以版本重新执行。 */
    public ValidationView preview(OnboardingDefinitionDO row,ApplicationForm form,Long department,long applicant,Long version) {
        var model=json.read(row.getModelJson(),FlowModel.class); validation.model(model); var result=new ArrayList<NodePreview>();
        for(var node:model.nodes()) { boolean match=validation.matches(node,form); var candidates=match?directory.qualified(node.reviewers(),department,applicant,OnboardingDirectory.CandidateKind.REVIEW):Set.<Long>of(); if(match)candidates.removeIf(candidate->!directory.can(candidate,"workflow:tasks:act") || !directory.can(candidate,"workflow:tasks:view")); if(match && candidates.isEmpty()) throw invalid("节点“"+node.name()+"”没有可用审批人（已排除申请人），请检查兜底配置"); result.add(new NodePreview(node.key(),node.name(),node.mode(),match,directory.names(candidates))); }
        var fulfillers=directory.qualified(model.fulfillers(),department,applicant,OnboardingDirectory.CandidateKind.FULFILL); fulfillers.removeIf(candidate->!directory.can(candidate,"app-fulfillments:act") || !directory.can(candidate,"app-fulfillments:view")); if(fulfillers.isEmpty()) throw invalid("没有可用平台办理人（已排除申请人）");
        var operators=directory.qualified(model.operators(),department,null,OnboardingDirectory.CandidateKind.OPERATE); operators.removeIf(candidate->!directory.can(candidate,"app-requests:manage") || !directory.can(candidate,"notifications:own:view")); if(operators.isEmpty())throw invalid("没有可接收异常通知的流程运维员，请配置有申请管理和通知权限的人员");
        return new ValidationView(version,row.getId(),row.getName(),row.getVersion(),result,directory.names(fulfillers),directory.names(operators));
    }
    /** 固定版本只读，包括已停用但在途的版本。 */
    public OnboardingDefinitionDO require(long id) { var row=store.definitions.selectById(id); if(row==null) throw new BizException(ErrorCode.RESOURCE_NOT_FOUND,"流程版本不存在"); return row; }
    private List<BindingView> bindings() {
        var bindings=store.bindings.selectList(Wrappers.<OnboardingBindingDO>lambdaQuery()); var ids=bindings.stream().map(OnboardingBindingDO::getDefinitionId).distinct().toList(); var definitions=new HashMap<Long,OnboardingDefinitionDO>(); if(!ids.isEmpty())store.definitions.selectList(Wrappers.<OnboardingDefinitionDO>lambdaQuery().in(OnboardingDefinitionDO::getId,ids)).forEach(row->definitions.put(row.getId(),row));
        return bindings.stream().filter(binding->definitions.containsKey(binding.getDefinitionId())).map(binding->{var definition=definitions.get(binding.getDefinitionId());return new BindingView(binding.getEnvironment(),definition.getId(),definition.getName(),definition.getVersion());}).toList();
    }
    private void expect(OnboardingDefinitionDO row,Long version) { if(version==null || version<0 || version==Long.MAX_VALUE || !version.equals(row.getRowVersion())) throw conflict(); }
    private DefinitionView view(OnboardingDefinitionDO row) { var envs=store.bindings.selectList(Wrappers.<OnboardingBindingDO>lambdaQuery().eq(OnboardingBindingDO::getDefinitionId,row.getId())).stream().map(OnboardingBindingDO::getEnvironment).collect(java.util.stream.Collectors.toSet()); return new DefinitionView(row.getId(),row.getDefinitionKey(),row.getName(),row.getVersion(),row.getStatus(),row.getRowVersion(),json.read(row.getModelJson(),FlowModel.class),envs,row.getPublishedAt(),json.hash(row.getModelJson())); }
}
