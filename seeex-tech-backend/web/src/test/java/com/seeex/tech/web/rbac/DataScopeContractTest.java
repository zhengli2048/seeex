package com.seeex.tech.web.rbac;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.seeex.tech.web.rbac.entity.RoleDataScopeDO;
import com.seeex.tech.web.rbac.mapper.*;
import com.seeex.tech.web.organization.mapper.*;
import com.seeex.tech.web.governance.ApplicationPolicyMapper;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
/** 数据范围默认拒绝扩权、多个自定义集合合并的契约；仅编写，未执行。 */
class DataScopeContractTest {
    @Test void missingRulesOnlyExposeCurrentEmployee() {
        var service = new DataScopeService(mock(RoleDataScopeMapper.class),mock(ApplicationPolicyMapper.class),mock(ResourceMapper.class),mock(DepartmentMapper.class),mock(EmployeeDepartmentMapper.class),new ObjectMapper());
        var scope = service.resolveFor(12,Set.of(),"employees:view");
        assertEquals(Set.of(12L),scope.employeeIds()); assertTrue(scope.departmentIds().isEmpty()); assertEquals(DataScopeService.ScopeType.SELF,scope.type());
    }
    @Test void customRoleRulesUnionActualPeopleRatherThanDiscardingConfiguration() {
        var mapper = mock(RoleDataScopeMapper.class);
        var first = new RoleDataScopeDO(); first.setScopeType("CUSTOM"); first.setScopeConfig("{\"employeeIds\":[21],\"departmentIds\":[]}");
        var second = new RoleDataScopeDO(); second.setScopeType("CUSTOM"); second.setScopeConfig("{\"employeeIds\":[22],\"departmentIds\":[]}");
        when(mapper.selectList(any())).thenReturn(List.of(first,second));
        var service = new DataScopeService(mapper,mock(ApplicationPolicyMapper.class),mock(ResourceMapper.class),mock(DepartmentMapper.class),mock(EmployeeDepartmentMapper.class),new ObjectMapper());
        assertEquals(Set.of(21L,22L),service.resolveFor(12,Set.of(1L,2L),"employees:view").employeeIds());
    }
}
