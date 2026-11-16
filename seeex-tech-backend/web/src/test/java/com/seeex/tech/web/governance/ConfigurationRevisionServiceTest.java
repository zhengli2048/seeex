package com.seeex.tech.web.governance;

import com.seeex.tech.common.exception.BizException;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** 配置删除再新增仍保留单调版本，旧表单不能覆盖空集合。 */
class ConfigurationRevisionServiceTest {
    @Test void staleRevisionIsRejectedEvenWhenBusinessCollectionIsEmpty() {
        var mapper=mock(ConfigurationRevisionMapper.class); var row=new ConfigurationRevisionDO(); row.setConfigKey("app-admin:demo"); row.setRevision(5L); when(mapper.selectById("app-admin:demo")).thenReturn(row);
        assertThrows(BizException.class,()->new ConfigurationRevisionService(mapper).advance("app-admin:demo",0L));
        verify(mapper,never()).insert(any(ConfigurationRevisionDO.class));
    }
}
