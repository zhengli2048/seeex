package com.seeex.tech.web.rbac;

import com.seeex.tech.common.exception.BizException;
import com.seeex.tech.web.organization.SequenceGenerator;
import com.seeex.tech.web.rbac.mapper.ResourceMapper;
import com.seeex.tech.web.rbac.mapper.RoleMapper;
import com.seeex.tech.web.rbac.mapper.RoleResourceMapper;
import com.seeex.tech.web.rbac.mapper.SubjectResourceGrantMapper;
import com.seeex.tech.web.rbac.mapper.SubjectRoleAssignmentMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RbacManagementServiceTest {
    private final RoleMapper roleMapper = mock(RoleMapper.class);
    private final ResourceMapper resourceMapper = mock(ResourceMapper.class);
    private final RoleResourceMapper roleResourceMapper = mock(RoleResourceMapper.class);
    private final SubjectResourceGrantMapper subjectGrantMapper = mock(SubjectResourceGrantMapper.class);
    private final SubjectRoleAssignmentMapper subjectRoleAssignmentMapper = mock(SubjectRoleAssignmentMapper.class);
    private final SequenceGenerator sequenceGenerator = mock(SequenceGenerator.class);
    private final RbacManagementService service = new RbacManagementService(
            roleMapper, mock(com.seeex.tech.web.organization.mapper.EmployeeMapper.class), mock(com.seeex.tech.web.organization.mapper.DepartmentMapper.class), mock(com.seeex.tech.web.organization.mapper.JobGradeMapper.class), resourceMapper, roleResourceMapper, subjectGrantMapper, subjectRoleAssignmentMapper, sequenceGenerator, mock(com.seeex.tech.web.registry.mapper.ApplicationMapper.class));

    @Test
    void rejectsDuplicateRoleCode() {
        when(roleMapper.selectCount(any())).thenReturn(1L);

        assertThrows(BizException.class, () -> service.createRole(
                new RbacManagementService.RoleCommand("admin", "Administrator", null)));
    }

    @Test
    void rejectsGrantForMissingRole() {
        when(roleMapper.selectById(42L)).thenReturn(null);

        assertThrows(BizException.class, () -> service.replaceGrants(42L, java.util.List.of(), null));
    }
}
