package com.seeex.tech.web.organization;

import com.seeex.tech.common.api.ErrorCode;
import com.seeex.tech.common.exception.BizException;
import com.seeex.tech.web.auth.ManagementAuthorizationService;
import com.seeex.tech.web.organization.entity.EmployeeDepartmentDO;
import com.seeex.tech.web.organization.mapper.*;
import com.seeex.tech.web.rbac.entity.SubjectRoleAssignmentDO;
import com.seeex.tech.web.rbac.mapper.SubjectRoleAssignmentMapper;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** 即使移除的是含拒绝授权的继承角色，也必须经过分配权限检查。 */
class OrganizationGrantGuardTest {
    @Test void removingInheritedRoleRequiresAssignmentPermission() {
        var members=mock(EmployeeDepartmentMapper.class); var assignments=mock(SubjectRoleAssignmentMapper.class); var authorization=mock(ManagementAuthorizationService.class); var auth=mock(Authentication.class);
        var member=new EmployeeDepartmentDO(); member.setDepartmentId(10L); member.setIsPrimary(true); when(members.selectList(any())).thenReturn(List.of(member));
        var assignment=new SubjectRoleAssignmentDO(); assignment.setRoleId(20L); when(assignments.selectList(any())).thenReturn(List.of(assignment));
        doThrow(new BizException(ErrorCode.FORBIDDEN,"缺少角色分配权限")).when(authorization).require(auth,"rbac:roles:assign");
        var guard=new OrganizationGrantGuard(mock(EmployeeMapper.class),members,mock(JobGradeMapper.class),assignments,authorization);
        var command=new OrganizationManagementService.EmployeeCommand("EMP1","员工","ACTIVE",null,null,List.of());
        assertThrows(BizException.class,()->guard.employee(auth,1L,command)); verify(authorization).require(auth,"rbac:roles:assign");
    }
}
