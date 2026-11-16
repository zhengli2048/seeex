package com.seeex.tech.web.wecom;

import com.seeex.tech.common.api.ErrorCode;
import com.seeex.tech.common.exception.BizException;
import com.seeex.tech.web.organization.entity.DepartmentDO;
import com.seeex.tech.web.organization.entity.EmployeeDO;
import com.seeex.tech.web.organization.entity.EmployeeDepartmentDO;
import com.seeex.tech.web.organization.mapper.DepartmentMapper;
import com.seeex.tech.web.organization.mapper.EmployeeMapper;
import com.seeex.tech.web.organization.mapper.EmployeeDepartmentMapper;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import java.time.LocalDateTime;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** 原子写入与失效租约回归用例源；本次未执行，不代替真实数据库验证。 */
class WeComAtomicWriteContractTest {
    @Test void missingOneDepartmentRejectsBeforeAnyEmployeeOrRelationMutation() {
        var departments=mock(DepartmentMapper.class); var employees=mock(EmployeeMapper.class); var memberships=mock(EmployeeDepartmentMapper.class);
        var local=new DepartmentDO(); local.setId(10L); local.setCode("wecom-1");
        when(departments.selectList(any())).thenReturn(List.of(local));
        var writer=new WeComDirectoryWriter(departments,employees,memberships,mock(WeComDirectoryService.class),mock(SyncRunStateService.class));
        var user=new WeComClient.User("alice","员工",List.of(1L,2L),null,null,null,1);
        assertThrows(BizException.class,()->writer.employee(user,new WeComDirectoryWriter.WriteContext(null,LocalDateTime.now())));
        verify(employees,never()).insert(any(EmployeeDO.class)); verify(employees,never()).update(any(EmployeeDO.class),any()); verifyNoInteractions(memberships);
    }
    @Test void relationFailureRollsBackProxiedEmployeeTransactionAndDoesNotRecordSuccess() {
        var departments=mock(DepartmentMapper.class); var employees=mock(EmployeeMapper.class); var memberships=mock(EmployeeDepartmentMapper.class); var runs=mock(SyncRunStateService.class);
        var local=new DepartmentDO(); local.setId(10L); local.setCode("wecom-1"); when(departments.selectList(any())).thenReturn(List.of(local));
        var employee=new EmployeeDO(); employee.setId(7L); employee.setSyncVersion(0L); when(employees.selectOne(any())).thenReturn(employee); when(employees.update(any(EmployeeDO.class),any())).thenReturn(1);
        when(memberships.selectList(any())).thenReturn(List.of()); when(memberships.insert(any(EmployeeDepartmentDO.class))).thenThrow(new BizException(ErrorCode.CONFLICT,"模拟关系写入失败"));
        var target=new WeComDirectoryWriter(departments,employees,memberships,mock(WeComDirectoryService.class),runs);
        var manager=mock(PlatformTransactionManager.class); var status=mock(TransactionStatus.class); when(manager.getTransaction(any(TransactionDefinition.class))).thenReturn(status);
        var proxy=new ProxyFactory(target); proxy.setProxyTargetClass(true); proxy.addAdvice(new TransactionInterceptor(manager,new AnnotationTransactionAttributeSource()));
        var writer=(WeComDirectoryWriter)proxy.getProxy();
        assertThrows(BizException.class,()->writer.employee(new WeComClient.User("alice","员工",List.of(1L),null,null,null,1),new WeComDirectoryWriter.WriteContext(null,LocalDateTime.now())));
        verify(manager).rollback(status); verify(manager,never()).commit(any()); verify(runs,never()).committed(any(),any(),any(),anyBoolean());
    }
    @Test void oldOwnerTokenCannotReachAnyBatchUpdate() {
        var mapper=mock(SyncRunMapper.class); var row=new SyncRunDO(); row.setStatus("RUNNING"); row.setOwnerToken("new-owner"); row.setLeaseUntil(LocalDateTime.now().plusMinutes(1)); when(mapper.selectById(1L)).thenReturn(row);
        var service=new SyncRunStateService(mapper,mock(SyncRunItemMapper.class));
        assertThrows(SyncRunStateService.LeaseLostException.class,()->service.fence(new SyncRunStateService.Lease(1L,"run","old-owner",1)));
        verify(mapper,never()).update(any(SyncRunDO.class),any());
    }
}
