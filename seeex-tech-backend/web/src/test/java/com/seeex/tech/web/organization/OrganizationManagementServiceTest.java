package com.seeex.tech.web.organization;

import com.seeex.tech.common.exception.BizException;
import com.seeex.tech.web.organization.entity.EmployeeDO;
import com.seeex.tech.web.organization.mapper.DepartmentMapper;
import com.seeex.tech.web.organization.mapper.EmployeeDepartmentMapper;
import com.seeex.tech.web.organization.mapper.EmployeeMapper;
import com.seeex.tech.web.organization.mapper.JobGradeMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrganizationManagementServiceTest {
    private final EmployeeMapper employeeMapper = mock(EmployeeMapper.class);
    private final DepartmentMapper departmentMapper = mock(DepartmentMapper.class);
    private final JobGradeMapper jobGradeMapper = mock(JobGradeMapper.class);
    private final EmployeeDepartmentMapper employeeDepartmentMapper = mock(EmployeeDepartmentMapper.class);
    private final SequenceGenerator sequenceGenerator = mock(SequenceGenerator.class);
    private final OrganizationManagementService service = new OrganizationManagementService(
            employeeMapper, departmentMapper, jobGradeMapper, employeeDepartmentMapper, sequenceGenerator);

    @Test
    void createsEmployeeWithDefaultActiveStatus() {
        when(employeeMapper.selectCount(any())).thenReturn(0L);
        OrganizationManagementService.EmployeeItem result = service.createEmployee(
                new OrganizationManagementService.EmployeeCommand("E100", "Alice", null, null, null, null));

        assertEquals("E100", result.employeeNo());
        assertEquals("ACTIVE", result.status());
        verify(employeeMapper).insert(any(EmployeeDO.class));
    }

    @Test
    void rejectsDuplicateEmployeeNumber() {
        when(employeeMapper.selectCount(any())).thenReturn(1L);

        assertThrows(BizException.class, () -> service.createEmployee(
                new OrganizationManagementService.EmployeeCommand("E100", "Alice", null, null, null, null)));
    }
}
