package com.seeex.tech.web.wecom;
import com.seeex.tech.web.organization.mapper.*;
import com.seeex.tech.common.exception.BizException;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
/** 预览远端读取不完整时拒绝生成差异，避免将缺失误判为删除；未执行。 */
class WeComDirectoryContractTest {
    @Test void incompleteRemoteUsersNeverProducePreviewOrWriteLocalOrganization() {
        var client=mock(WeComClient.class); var departments=mock(DepartmentMapper.class); var employees=mock(EmployeeMapper.class); var memberships=mock(EmployeeDepartmentMapper.class);
        when(client.getToken()).thenReturn(new WeComClient.WeComToken(0,"ok","opaque",7200));
        when(client.listDepartments("opaque")).thenReturn(new WeComClient.DepartmentList(0,"ok",List.of(new WeComClient.Department(1,"部门",0,0))));
        when(client.listUsers("opaque",1)).thenReturn(new WeComClient.UserList(0,"ok",null));
        var service=new WeComDirectoryService(mock(WeComGradeMappingMapper.class),mock(JobGradeMapper.class),departments,employees,memberships,client);
        assertThrows(BizException.class,service::preview); verifyNoInteractions(departments,employees,memberships);
    }
}
