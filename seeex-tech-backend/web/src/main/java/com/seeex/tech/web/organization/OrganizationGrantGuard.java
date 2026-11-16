package com.seeex.tech.web.organization;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.seeex.tech.common.api.ErrorCode;
import com.seeex.tech.common.exception.BizException;
import com.seeex.tech.web.auth.ManagementAuthorizationService;
import com.seeex.tech.web.organization.entity.*;
import com.seeex.tech.web.organization.mapper.*;
import com.seeex.tech.web.rbac.entity.SubjectRoleAssignmentDO;
import com.seeex.tech.web.rbac.mapper.SubjectRoleAssignmentMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import java.util.*;

/** 组织资料编辑不隐含角色授予；新增或移除继承来源均需独立分配权限。 */
@Service @RequiredArgsConstructor
public class OrganizationGrantGuard {
    private final EmployeeMapper employees;
    private final EmployeeDepartmentMapper memberships;
    private final JobGradeMapper grades;
    private final SubjectRoleAssignmentMapper assignments;
    private final ManagementAuthorizationService authorization;

    /** 比较人员变更前后的继承角色，包括部门角色与职级默认角色。 */
    public void employee(Authentication auth, Long id, OrganizationManagementService.EmployeeCommand command) {
        List<OrganizationManagementService.DepartmentMembership> before = new ArrayList<>();
        if (id != null) {
            memberships.selectList(Wrappers.<EmployeeDepartmentDO>lambdaQuery().eq(EmployeeDepartmentDO::getEmployeeId,id).isNull(EmployeeDepartmentDO::getDeletedAt))
                    .forEach(row -> before.add(new OrganizationManagementService.DepartmentMembership(row.getDepartmentId(),row.getJobGradeId(),row.getIsPrimary())));
            if (before.isEmpty()) { var employee=employees.selectById(id); if(employee!=null && employee.getPrimaryDepartmentId()!=null) before.add(new OrganizationManagementService.DepartmentMembership(employee.getPrimaryDepartmentId(),employee.getJobGradeId(),true)); }
        }
        var after=command.memberships();
        if(after==null) after=command.primaryDepartmentId()==null?List.of():List.of(new OrganizationManagementService.DepartmentMembership(command.primaryDepartmentId(),command.jobGradeId(),true));
        if(!roles(before).equals(roles(after))) authorization.require(auth,"rbac:roles:assign");
    }
    /** 默认角色写入/移除是授权变更，不能只凭职级编辑权限执行。 */
    public void grade(Authentication auth, Long id, Long requestedRole, String status) {
        var row=id==null?null:grades.selectById(id);
        Long previous=row==null?null:row.getDefaultRoleId();
        if(!Objects.equals(previous,requestedRole) || previous!=null && status!=null && !Objects.equals(row.getStatus(),status)) authorization.require(auth,"rbac:roles:assign");
    }
    /** 部门停用也会撤销继承角色，必须按授权变更校验。 */
    public void departmentStatus(Authentication auth, Long id, String status) {
        var rows=assignments.selectList(Wrappers.<SubjectRoleAssignmentDO>lambdaQuery().eq(SubjectRoleAssignmentDO::getSubjectType,"DEPARTMENT").eq(SubjectRoleAssignmentDO::getSubjectId,id).isNull(SubjectRoleAssignmentDO::getDeletedAt));
        if(!rows.isEmpty() && status!=null) authorization.require(auth,"rbac:roles:assign");
    }
    /** 企微映射可改变未来成员授权，配置含角色的映射时也需要角色分配权限。 */
    public void mappedGrades(Authentication auth, Long oldGrade, Long newGrade) {
        for(Long id:new Long[]{oldGrade,newGrade}) { var grade=id==null?null:grades.selectById(id); if(grade!=null && grade.getDefaultRoleId()!=null) { authorization.require(auth,"rbac:roles:assign"); return; } }
    }
    private Set<Long> roles(List<OrganizationManagementService.DepartmentMembership> rows) {
        Set<Long> result=new HashSet<>(), departmentIds=new HashSet<>(), gradeIds=new HashSet<>();
        for(var row:rows) {
            if(row==null || row.departmentId()==null) throw new BizException(ErrorCode.VALIDATION_FAILED,"部门关系不能为空");
            departmentIds.add(row.departmentId()); if(row.jobGradeId()!=null) gradeIds.add(row.jobGradeId());
        }
        if(!departmentIds.isEmpty()) assignments.selectList(Wrappers.<SubjectRoleAssignmentDO>lambdaQuery().eq(SubjectRoleAssignmentDO::getSubjectType,"DEPARTMENT").in(SubjectRoleAssignmentDO::getSubjectId,departmentIds).isNull(SubjectRoleAssignmentDO::getDeletedAt)).forEach(row->result.add(row.getRoleId()));
        if(!gradeIds.isEmpty()) grades.selectList(Wrappers.<JobGradeDO>lambdaQuery().in(JobGradeDO::getId,gradeIds)).forEach(row->{if(row.getDefaultRoleId()!=null)result.add(row.getDefaultRoleId());});
        return result;
    }
}
