package com.seeex.tech.web.wecom;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.seeex.tech.common.api.*;
import com.seeex.tech.common.exception.BizException;
import com.seeex.tech.web.organization.entity.*;
import com.seeex.tech.web.organization.mapper.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.*;
/** 显式职级映射与只读通讯录差异，不把可见范围缺失推断为离职。 */
@Service @RequiredArgsConstructor
public class WeComDirectoryService {
    private static final int MAX_USERS = 10000;
    private static final int MAX_DEPARTMENTS = 2000;
    private final WeComGradeMappingMapper mappings;
    private final JobGradeMapper grades;
    private final DepartmentMapper departments;
    private final EmployeeMapper employees;
    private final EmployeeDepartmentMapper memberships;
    private final WeComClient client;
    /** 返回映射及可选有效职级，供表单使用。 */
    public Map<String,Object> mappings() { return Map.of("items",mappings.selectList(Wrappers.<WeComGradeMappingDO>lambdaQuery().orderByAsc(WeComGradeMappingDO::getExternalPosition)),"grades",grades.selectList(Wrappers.<JobGradeDO>lambdaQuery().isNull(JobGradeDO::getDeletedAt).eq(JobGradeDO::getStatus,"ACTIVE"))); }
    /** 创建或更新映射，不能关联已删除/停用职级。 */
    public WeComGradeMappingDO save(Long id,MappingCommand command) {
        String position = command.externalPosition() == null ? "" : command.externalPosition().trim();
        var grade = command.jobGradeId() == null ? null : grades.selectById(command.jobGradeId());
        if (position.isBlank() || position.length()>128 || grade == null || grade.getDeletedAt()!=null || !"ACTIVE".equals(grade.getStatus()) || !List.of("ACTIVE","INACTIVE").contains(command.status()==null ? "" : command.status())) throw new BizException(ErrorCode.VALIDATION_FAILED,"职位、有效职级和状态必填");
        if (mappings.selectCount(Wrappers.<WeComGradeMappingDO>lambdaQuery().eq(WeComGradeMappingDO::getExternalPosition,position).ne(id!=null,WeComGradeMappingDO::getId,id))>0) throw new BizException(ErrorCode.CONFLICT,"职位已配置映射");
        var row = id == null ? new WeComGradeMappingDO() : mappings.selectById(id);
        if (row==null) throw new BizException(ErrorCode.RESOURCE_NOT_FOUND,"映射不存在");
        row.setExternalPosition(position); row.setJobGradeId(command.jobGradeId()); row.setStatus(command.status());
        if(id==null) mappings.insert(row); else mappings.updateById(row); return row;
    }
    /** 读取映射供服务端授权判断，不通过浏览器信任旧值。 */
    public Long previousGrade(long id) { var row=mappings.selectById(id); if(row==null) throw new BizException(ErrorCode.RESOURCE_NOT_FOUND,"映射不存在"); return row.getJobGradeId(); }
    /** 删除映射不改写已有人工或历史职级。 */
    public void delete(long id) { if(mappings.deleteById(id)==0) throw new BizException(ErrorCode.RESOURCE_NOT_FOUND,"映射不存在"); }
    /** 同步和预览使用相同映射规则，未映射时保留原职级。 */
    public Long mappedGrade(String position) {
        if(position==null || position.isBlank()) return null;
        var row = mappings.selectOne(Wrappers.<WeComGradeMappingDO>lambdaQuery().eq(WeComGradeMappingDO::getExternalPosition,position.trim()).eq(WeComGradeMappingDO::getStatus,"ACTIVE"));
        if(row==null) return null;
        var grade=grades.selectById(row.getJobGradeId()); return grade!=null && grade.getDeletedAt()==null && "ACTIVE".equals(grade.getStatus()) ? grade.getId() : null;
    }
    /** 所有远端读取成功才返回预览，防止部分失败被误报为大量缺失。 */
    public Preview preview() {
        String token=client.getToken().access_token();
        var remoteDepartments=client.listDepartments(token).department();
        if(remoteDepartments==null || remoteDepartments.size()>MAX_DEPARTMENTS) throw new BizException(ErrorCode.BUSINESS_ERROR,"部门范围为空或超过预览上限");
        Map<String,WeComClient.User> remoteUsers=new LinkedHashMap<>();
        for(var department:remoteDepartments) {
            var users=client.listUsers(token,department.id()).userlist();
            if(users==null) throw new BizException(ErrorCode.BUSINESS_ERROR,"成员读取不完整，未生成预览");
            for(var user:users) { if(user.userid()==null || user.department()==null || user.department().isEmpty()) throw new BizException(ErrorCode.BUSINESS_ERROR,"成员数据不完整"); remoteUsers.putIfAbsent(user.userid(),user); }
            if(remoteUsers.size()>MAX_USERS) throw new BizException(ErrorCode.VALIDATION_FAILED,"成员超过10000人预览上限，请缩小通讯录范围");
        }
        List<Diff> result=new ArrayList<>();
        Map<Long,DepartmentDO> localById=new HashMap<>();
        var localDepartments=departments.selectList(Wrappers.<DepartmentDO>lambdaQuery()); localDepartments.forEach(row->localById.put(row.getId(),row));
        Map<String,DepartmentDO> localByCode=new HashMap<>(); localDepartments.forEach(row->localByCode.put(row.getCode(),row));
        Set<String> seenDepartments=new HashSet<>();
        for(var remote:remoteDepartments) {
            String code="wecom-"+remote.id(); seenDepartments.add(code); var local=localByCode.get(code); List<Change> changes=new ArrayList<>();
            if(local!=null && local.getDeletedAt()!=null) { result.add(new Diff("DEPARTMENT",code,remote.name(),"CONFLICT",List.of(),"本地身份已软删除，需处理冲突后同步")); continue; }
            compare(changes,"名称",local==null?null:local.getName(),remote.name());
            String parent=remote.parentid()==0?null:"wecom-"+remote.parentid();
            compare(changes,"上级",local==null?null:Optional.ofNullable(localById.get(local.getParentId())).map(DepartmentDO::getCode).orElse(null),parent);
            compare(changes,"状态",local==null?null:local.getStatus(),"ACTIVE");
            if(local==null || !changes.isEmpty()) result.add(new Diff("DEPARTMENT",code,remote.name(),local==null?"ADD":"UPDATE",changes,null));
        }
        for(var local:localDepartments) if(local.getDeletedAt()==null && local.getCode().startsWith("wecom-") && !seenDepartments.contains(local.getCode())) result.add(new Diff("DEPARTMENT",local.getCode(),local.getName(),"OUT_OF_SCOPE",List.of(),"不在远端可见范围，不自动删除或停用"));
        var locals=employees.selectList(Wrappers.<EmployeeDO>lambdaQuery().eq(EmployeeDO::getSource,"WECOM"));
        Map<String,EmployeeDO> localUsers=new HashMap<>(); locals.forEach(row->localUsers.put(row.getEmployeeNo(),row));
        for(var remote:remoteUsers.values()) {
            String code="wc_"+remote.userid(); var local=localUsers.remove(code); List<Change> changes=new ArrayList<>();
            if(code.length()>32 || remote.name()==null || remote.name().isBlank() || remote.name().length()>128) { result.add(new Diff("EMPLOYEE",code,remote.name(),"CONFLICT",List.of(),"标识或姓名不符合本地字段限制，无法同步")); continue; }
            if(local!=null && local.getDeletedAt()!=null) { result.add(new Diff("EMPLOYEE",code,remote.name(),"CONFLICT",List.of(),"本地身份已软删除，需处理冲突后同步")); continue; }
            compare(changes,"姓名",local==null?null:local.getDisplayName(),remote.name());
            compare(changes,"状态",local==null?null:local.getStatus(),remote.status()!=null && remote.status()==2?"DISABLED":"ACTIVE");
            String remoteCodes=remote.department().stream().map(id->"wecom-"+id).sorted().collect(java.util.stream.Collectors.joining(","));
            var localMemberships=local==null?List.<EmployeeDepartmentDO>of():memberships.selectList(Wrappers.<EmployeeDepartmentDO>lambdaQuery().eq(EmployeeDepartmentDO::getEmployeeId,local.getId()).isNull(EmployeeDepartmentDO::getDeletedAt));
            String localCodes=local==null?null:localMemberships.stream().map(row->Optional.ofNullable(localById.get(row.getDepartmentId())).map(DepartmentDO::getCode).orElse("未知部门")).sorted().collect(java.util.stream.Collectors.joining(","));
            compare(changes,"部门",localCodes,remoteCodes);
            compare(changes,"主部门",local==null?null:Optional.ofNullable(localById.get(local.getPrimaryDepartmentId())).map(DepartmentDO::getCode).orElse(null),"wecom-"+remote.department().get(0));
            Long mapped=mappedGrade(remote.position());
            if(mapped!=null) {
                compare(changes,"职级",local==null?null:gradeName(local.getJobGradeId()),gradeName(mapped));
                if(localMemberships.stream().anyMatch(row -> !Objects.equals(row.getJobGradeId(),mapped))) changes.add(new Change("各部门职级",localMemberships.stream().map(row -> Optional.ofNullable(localById.get(row.getDepartmentId())).map(DepartmentDO::getName).orElse("未知部门")+":"+Optional.ofNullable(gradeName(row.getJobGradeId())).orElse("无职级")).collect(java.util.stream.Collectors.joining("、")),gradeName(mapped)));
            }
            if(local!=null && (!Objects.equals(local.getInternalMobile(),remote.mobile()) || !Objects.equals(local.getInternalEmail(),remote.email()))) changes.add(new Change("联系方式","已隐藏","将更新（值已隐藏）"));
            if(local==null || !changes.isEmpty()) result.add(new Diff("EMPLOYEE",code,remote.name(),local==null?"ADD":"UPDATE",changes,mapped==null?"职位未映射或目标不可用，保留本地职级":"显式映射："+remote.position()));
        }
        localUsers.values().stream().filter(local -> local.getDeletedAt()==null).forEach(local->result.add(new Diff("EMPLOYEE",local.getEmployeeNo(),local.getDisplayName(),"OUT_OF_SCOPE",List.of(),"不在可见范围，不推断离职")));
        return new Preview(java.time.Instant.now(),remoteUsers.size(),remoteDepartments.size(),result,"预览不写组织数据；确认同步会重新读取最新通讯录，结果可能变化。");
    }
    private String gradeName(Long id) { var row=id==null?null:grades.selectById(id); return row==null?null:row.getName(); }
    private void compare(List<Change> changes,String field,String before,String after) { if(!Objects.equals(before,after)) changes.add(new Change(field,before,after)); }
    /** 映射表单。 */
    public record MappingCommand(String externalPosition,Long jobGradeId,String status) { }
    /** 脱敏字段变更。 */
    public record Change(String field,String before,String after) { }
    /** 差异条目。 */
    public record Diff(String kind,String key,String name,String operation,List<Change> changes,String notice) { }
    /** 当前预览快照，不作为写入命令。 */
    public record Preview(java.time.Instant capturedAt,int employeeCount,int departmentCount,List<Diff> items,String notice) { }
}
