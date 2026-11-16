package com.seeex.tech.web.audit;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.seeex.tech.common.exception.BizException;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;
/** 导出上限与CSV公式注入回归契约；仅编写，未执行。 */
class AuditExportContractTest {
    @Test void oversizedExportRejectsInsteadOfReturningFirstPage() {
        var mapper=mock(AuditLogMapper.class);
        Page<AuditLogDO> page=new Page<>(); page.setTotal(10001); page.setRecords(List.of());
        when(mapper.selectPage(any(Page.class),any())).thenReturn(page);
        assertThrows(BizException.class,()->new AuditService(mapper).export(new AuditService.AuditQuery(1,20,null,null,null,null,null)));
    }
    @Test void formulaLikeActionIsExportedAsText() {
        var mapper=mock(AuditLogMapper.class); var row=new AuditLogDO(); row.setAction("=1+1");
        Page<AuditLogDO> page=new Page<>(); page.setTotal(1); page.setRecords(List.of(row));
        when(mapper.selectPage(any(Page.class),any())).thenReturn(page);
        String csv=new String(new AuditService(mapper).export(new AuditService.AuditQuery(1,20,null,null,null,null,null)),StandardCharsets.UTF_8);
        assertTrue(csv.startsWith("\ufeff")); assertTrue(csv.contains("\"'=1+1\"")); assertFalse(csv.contains("\"=1+1\""));
    }
}
