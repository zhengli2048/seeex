package com.seeex.tech.web.onboarding;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.seeex.tech.common.api.ErrorCode;
import com.seeex.tech.common.exception.BizException;
import com.seeex.tech.web.client.FileServiceClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.*;
import static com.seeex.tech.web.onboarding.OnboardingContract.*;
import static com.seeex.tech.web.onboarding.OnboardingJson.*;
/** 附件HTTP读取在业务事务外；通过申请ACL后只读取确实绑定的原所有者文件。 */
@Service @RequiredArgsConstructor
public class OnboardingAttachmentService {
    private static final long MAX_TOTAL_BYTES=100L*1024*1024;
    private final OnboardingStore store;
    private final OnboardingAccess access;
    private final OnboardingDirectory directory;
    private final OnboardingJson json;
    private final FileServiceClient files;
    /** 保存前确认当前用户选择的是本人的已确认文件。 */
    public void validateNew(long actor,ApplicationForm form) {
        if(form==null || form.attachmentKeys()==null || form.attachmentKeys().isEmpty()) return;
        directory.require(actor,"files:view"); check(actor,form);
    }
    /** 提交前读取精确表单版本并检查绑定附件，后续CAS防止校验时更换材料。 */
    public void validateSubmit(long actor,long requestId,Long version) { var request=access.own(actor,requestId); if(!Objects.equals(version,request.getRowVersion())) throw conflict(); check(request.getApplicantId(),json.read(request.getDataJson(),ApplicationForm.class)); }
    /** 审批通过/完成办理前检查原始附件仍可用，不要求查看者成为文件所有者。 */
    public void validateBound(long actor,long requestId) { var request=access.view(actor,requestId); check(request.getApplicantId(),json.read(request.getDataJson(),ApplicationForm.class)); }
    /** 从任务关系找到申请，不能借任务ID访问其他附件。 */
    public long taskRequest(long actor,long taskId) { var task=store.tasks.selectById(taskId); if(task==null || !Objects.equals(task.getCandidateId(),actor)) throw new BizException(ErrorCode.FORBIDDEN); access.view(actor,task.getRequestId()); return task.getRequestId(); }
    /** 当前附件最小信息，失败由页面单独提示和重试。 */
    public List<Attachment> list(long actor,long requestId) { var request=access.view(actor,requestId); var form=json.read(request.getDataJson(),ApplicationForm.class); var result=new ArrayList<Attachment>(); for(String key:form.attachmentKeys()) result.add(reference(request.getApplicantId(),key)); return result; }
    /** 已提交历史附件也按相同参与者ACL读取，不能下载未绑定文件。 */
    public String download(long actor,long requestId,String key) {
        var request=access.view(actor,requestId); boolean bound=json.read(request.getDataJson(),ApplicationForm.class).attachmentKeys().contains(key);
        if(!bound) bound=store.revisions.selectList(Wrappers.<OnboardingRevisionDO>lambdaQuery().eq(OnboardingRevisionDO::getRequestId,requestId)).stream().anyMatch(row->json.read(row.getDataJson(),ApplicationForm.class).attachmentKeys().contains(key));
        if(!bound) throw new BizException(ErrorCode.FORBIDDEN,"文件未绑定该申请");
        var response=files.onboardingDownload(key,String.valueOf(request.getApplicantId())); Object data=response.get("data"); if(!(data instanceof String url)) throw new BizException(ErrorCode.BUSINESS_ERROR,"附件下载地址不可用"); return url;
    }
    private void check(long owner,ApplicationForm form) { if(form.attachmentKeys()==null || form.attachmentKeys().size()>10)throw invalid("附件最多10个"); long total=0; for(String key:form.attachmentKeys()) { var row=reference(owner,key); total+=row.fileSize(); } if(total>MAX_TOTAL_BYTES)throw invalid("申请附件总量不得超过100MB"); }
    private Attachment reference(long owner,String key) {
        if(key==null || key.isBlank() || key.length()>512)throw invalid("附件键无效"); var response=files.onboardingReference(key,String.valueOf(owner)); Object raw=response.get("data");
        if(!(raw instanceof Map<?,?> data) || !(data.get("fileName") instanceof String name) || !(data.get("fileSize") instanceof Number size)) throw new BizException(ErrorCode.BUSINESS_ERROR,"附件引用不可用");
        return new Attachment(key,name,size.longValue());
    }
    /** 受控文件信息。 */
    public record Attachment(String objectKey,String fileName,long fileSize) { }
}
