package com.seeex.tech.web.onboarding;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.seeex.tech.common.api.ErrorCode;
import com.seeex.tech.common.exception.BizException;
import com.seeex.tech.web.organization.entity.*;
import com.seeex.tech.web.organization.mapper.*;
import com.seeex.tech.web.rbac.entity.*;
import com.seeex.tech.web.rbac.mapper.*;
import com.seeex.tech.web.rbac.PermissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import java.util.*;
import static com.seeex.tech.web.onboarding.OnboardingContract.*;
import static com.seeex.tech.web.onboarding.OnboardingJson.invalid;

/** 候选人解析和最小字典；不开放联系方式或任意外部用户身份。 */
@Service @RequiredArgsConstructor
public class OnboardingDirectory {
    private static final int MAX_CANDIDATES=200;
    private final EmployeeMapper employees;
    private final DepartmentMapper departments;
    private final EmployeeDepartmentMapper memberships;
    private final JobGradeMapper grades;
    private final RoleMapper roles;
    private final SubjectRoleAssignmentMapper assignments;
    private final PermissionService permissions;
    /** 会话身份和员工实时状态同时验证。 */
    public long actor(Authentication auth) {
        if(auth==null || !auth.isAuthenticated()) throw new BizException(ErrorCode.UNAUTHORIZED);
        try { long id=Long.parseLong(auth.getName()); active(id); return id; }
        catch(NumberFormatException ex) { throw new BizException(ErrorCode.UNAUTHORIZED); }
    }
    /** 读取有效员工，离职停用身份不能继续处理旧任务。 */
    public EmployeeDO active(long id) {
        var row=employees.selectById(id);
        if(row==null || row.getDeletedAt()!=null || !"ACTIVE".equals(row.getStatus())) throw new BizException(ErrorCode.FORBIDDEN,"当前员工身份不可用");
        return row;
    }
    /** 实时功能权限。 */
    public boolean can(long actor,String code) {
        if(org.springframework.web.context.request.RequestContextHolder.getRequestAttributes() instanceof org.springframework.web.context.request.ServletRequestAttributes attributes) {
            Object existing=attributes.getRequest().getAttribute("seeex.onboarding.permission-memo");
            var memo=existing instanceof PermissionMemo value?value:new PermissionMemo(new HashMap<>());
            attributes.getRequest().setAttribute("seeex.onboarding.permission-memo",memo);
            return memo.codes().computeIfAbsent(actor,id->permissions.snapshot(id).effectiveCodes()).contains(code);
        }
        return permissions.hasPermission(actor,code);
    }
    private record PermissionMemo(Map<Long,Set<String>> codes) { }
    /** 强制功能权限，不能以任务参与关系替代。 */
    public void require(long actor,String code) { active(actor); if(!can(actor,code)) throw new BizException(ErrorCode.FORBIDDEN); }
    /** 活跃人员批量验证，禁止失效管理员。 */
    public void validEmployees(Collection<Long> ids) {
        if(ids==null || ids.stream().anyMatch(Objects::isNull) || ids.size()>MAX_CANDIDATES) throw invalid("人员集合为空、无效或超过200人");
        if(!ids.isEmpty() && employees.selectCount(Wrappers.<EmployeeDO>lambdaQuery().in(EmployeeDO::getId,ids).isNull(EmployeeDO::getDeletedAt).eq(EmployeeDO::getStatus,"ACTIVE"))!=new HashSet<>(ids).size()) throw invalid("人员不存在或已停用");
    }
    /** 校验选择器来源与有效ID。 */
    public void validate(Selector selector) {
        if(selector==null || !Set.of("EMPLOYEE","ROLE","DEPARTMENT_LEADER").contains(Objects.toString(selector.type(),""))) throw invalid("候选来源无效");
        var people=ids(selector.employeeIds()); var roleIds=ids(selector.roleIds()); var fallback=ids(selector.fallbackEmployeeIds());
        validEmployees(people); validEmployees(fallback);
        if("EMPLOYEE".equals(selector.type()) && people.isEmpty() || "ROLE".equals(selector.type()) && roleIds.isEmpty()) throw invalid("请配置审批/办理候选人或角色");
        if(!roleIds.isEmpty() && roles.selectCount(Wrappers.<RoleDO>lambdaQuery().in(RoleDO::getId,roleIds).isNull(RoleDO::getDeletedAt).eq(RoleDO::getStatus,"ACTIVE"))!=roleIds.size()) throw invalid("候选角色无效");
    }
    /** 解析固定、角色（含部门与职级继承）或部门负责人，再按有效身份过滤。 */
    public Set<Long> resolve(Selector selector,Long departmentId,Long excluded) {
        Set<Long> result=new HashSet<>();
        if("EMPLOYEE".equals(selector.type())) result.addAll(ids(selector.employeeIds()));
        if("DEPARTMENT_LEADER".equals(selector.type()) && departmentId!=null) {
            var department=departments.selectById(departmentId);
            if(department!=null && department.getDeletedAt()==null && "ACTIVE".equals(department.getStatus()) && department.getLeaderEmployeeId()!=null) result.add(department.getLeaderEmployeeId());
        }
        if("ROLE".equals(selector.type())) {
            var requested=ids(selector.roleIds());
            var activeRoles=roles.selectList(Wrappers.<RoleDO>lambdaQuery().in(!requested.isEmpty(),RoleDO::getId,requested).eq(RoleDO::getStatus,"ACTIVE").isNull(RoleDO::getDeletedAt)).stream().map(RoleDO::getId).filter(requested::contains).toList();
            if(!activeRoles.isEmpty()) {
                var rows=assignments.selectList(Wrappers.<SubjectRoleAssignmentDO>lambdaQuery().in(SubjectRoleAssignmentDO::getRoleId,activeRoles).isNull(SubjectRoleAssignmentDO::getDeletedAt));
                var ds=new HashSet<Long>();
                rows.forEach(row->{if("EMPLOYEE".equals(row.getSubjectType())) result.add(row.getSubjectId()); else if("DEPARTMENT".equals(row.getSubjectType())) ds.add(row.getSubjectId());});
                var gs=grades.selectList(Wrappers.<JobGradeDO>lambdaQuery().in(JobGradeDO::getDefaultRoleId,activeRoles).eq(JobGradeDO::getStatus,"ACTIVE").isNull(JobGradeDO::getDeletedAt)).stream().map(JobGradeDO::getId).toList();
                if(!ds.isEmpty() || !gs.isEmpty()) {
                    var members=memberships.selectList(Wrappers.<EmployeeDepartmentDO>lambdaQuery().isNull(EmployeeDepartmentDO::getDeletedAt).and(q->{if(!ds.isEmpty())q.in(EmployeeDepartmentDO::getDepartmentId,ds);if(!gs.isEmpty()){if(!ds.isEmpty())q.or();q.in(EmployeeDepartmentDO::getJobGradeId,gs);}}));
                    var departmentIds=members.stream().map(EmployeeDepartmentDO::getDepartmentId).collect(java.util.stream.Collectors.toSet());
                    Set<Long> availableDepartments=new HashSet<>();
                    if(!departmentIds.isEmpty()) departments.selectList(Wrappers.<DepartmentDO>lambdaQuery().in(DepartmentDO::getId,departmentIds).eq(DepartmentDO::getStatus,"ACTIVE").isNull(DepartmentDO::getDeletedAt)).forEach(row->availableDepartments.add(row.getId()));
                    members.stream().filter(row->availableDepartments.contains(row.getDepartmentId())).forEach(row->result.add(row.getEmployeeId()));
                }
            }
        }
        var filtered=filterActive(result,excluded);
        if(filtered.isEmpty()) filtered=filterActive(ids(selector.fallbackEmployeeIds()),excluded);
        if(filtered.size()>MAX_CANDIDATES) throw invalid("候选人超过200人，请缩小配置范围");
        return filtered;
    }
    /** 功能资格也纳入兜底条件，避免仅有员工身份却无处理权限的候选阻塞流程。 */
    public Set<Long> qualified(Selector selector,Long departmentId,Long excluded,CandidateKind kind) {
        var result=resolve(selector,departmentId,excluded);result.removeIf(id->!eligible(id,kind));
        if(result.isEmpty() && selector.fallbackEmployeeIds()!=null && !selector.fallbackEmployeeIds().isEmpty()) { result=resolve(new Selector("EMPLOYEE",selector.fallbackEmployeeIds(),List.of(),List.of()),departmentId,excluded);result.removeIf(id->!eligible(id,kind)); }
        return result;
    }
    /** 已激活备选任务保留明确配置的来源，不因主候选恢复而丧失资格。 */
    public boolean configuredCandidate(long actor,Selector selector,Long departmentId,Long excluded,CandidateKind kind) {
        if(Objects.equals(actor,excluded))return false;
        if(selector.fallbackEmployeeIds()!=null && selector.fallbackEmployeeIds().contains(actor))return eligible(actor,kind);
        return qualified(selector,departmentId,excluded,kind).contains(actor);
    }
    private boolean eligible(long actor,CandidateKind kind) {
        return switch(kind) {
            case REVIEW -> can(actor,"workflow:tasks:view") && can(actor,"workflow:tasks:act");
            case FULFILL -> can(actor,"app-fulfillments:view") && can(actor,"app-fulfillments:act");
            case OPERATE -> can(actor,"app-requests:manage") && can(actor,"notifications:own:view");
        };
    }
    /** 候选资格类别不是浏览器可传入的流程表达式。 */
    public enum CandidateKind { REVIEW,FULFILL,OPERATE }
    private Set<Long> filterActive(Collection<Long> ids,Long excluded) {
        Set<Long> result=new HashSet<>();
        if(!ids.isEmpty()) employees.selectList(Wrappers.<EmployeeDO>lambdaQuery().select(EmployeeDO::getId).in(EmployeeDO::getId,ids).eq(EmployeeDO::getStatus,"ACTIVE").isNull(EmployeeDO::getDeletedAt)).forEach(row->{if(!Objects.equals(row.getId(),excluded)) result.add(row.getId());});
        return result;
    }
    /** 批量名称，不输出员工隐私字段。 */
    public List<Option> names(Collection<Long> ids) {
        if(ids==null || ids.isEmpty()) return List.of();
        return employees.selectList(Wrappers.<EmployeeDO>lambdaQuery().select(EmployeeDO::getId,EmployeeDO::getDisplayName).in(EmployeeDO::getId,ids)).stream().map(row->new Option(row.getId(),row.getDisplayName())).toList();
    }
    /** 人员/角色搜索各最多50个，当前已选项最多200个另外回填。 */
    public List<Option> options(String kind,String keyword,List<Long> selected) {
        if(!Set.of("EMPLOYEE","ROLE").contains(kind)) throw invalid("字典类型无效");
        var selectedIds=ids(selected); if(selectedIds.size()>MAX_CANDIDATES || keyword!=null && keyword.length()>128) throw invalid("字典查询超出限制");
        Map<Long,Option> result=new LinkedHashMap<>();
        if("ROLE".equals(kind)) {
            var q=Wrappers.<RoleDO>lambdaQuery().select(RoleDO::getId,RoleDO::getName).eq(RoleDO::getStatus,"ACTIVE").isNull(RoleDO::getDeletedAt).like(keyword!=null && !keyword.isBlank(),RoleDO::getName,keyword).orderByAsc(RoleDO::getId);
            roles.selectPage(new Page<>(1,50,false),q).getRecords().forEach(row->result.put(row.getId(),new Option(row.getId(),row.getName())));
            if(!selectedIds.isEmpty()) roles.selectList(Wrappers.<RoleDO>lambdaQuery().select(RoleDO::getId,RoleDO::getName).in(RoleDO::getId,selectedIds)).forEach(row->result.put(row.getId(),new Option(row.getId(),row.getName())));
        } else {
            var q=Wrappers.<EmployeeDO>lambdaQuery().select(EmployeeDO::getId,EmployeeDO::getDisplayName).eq(EmployeeDO::getStatus,"ACTIVE").isNull(EmployeeDO::getDeletedAt).like(keyword!=null && !keyword.isBlank(),EmployeeDO::getDisplayName,keyword).orderByAsc(EmployeeDO::getId);
            employees.selectPage(new Page<>(1,50,false),q).getRecords().forEach(row->result.put(row.getId(),new Option(row.getId(),row.getDisplayName())));
            names(selectedIds).forEach(row->result.put(row.id(),row));
        }
        return List.copyOf(result.values());
    }
    /** 获取主部门；禁止已删除/停用部门用于新审批。 */
    public Long department(long actor) {
        Long id=active(actor).getPrimaryDepartmentId(); var row=id==null?null:departments.selectById(id);
        if(row==null || row.getDeletedAt()!=null || !"ACTIVE".equals(row.getStatus())) throw invalid("申请人须配置有效主部门"); return id;
    }
    private Set<Long> ids(List<Long> values) {
        if(values==null) return new HashSet<>();
        if(values.size()>MAX_CANDIDATES || values.stream().anyMatch(Objects::isNull) || new HashSet<>(values).size()!=values.size()) throw invalid("选择项无效、重复或超过200项");
        return new HashSet<>(values);
    }
}
