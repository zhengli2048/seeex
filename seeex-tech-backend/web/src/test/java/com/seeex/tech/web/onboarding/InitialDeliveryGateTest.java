package com.seeex.tech.web.onboarding;
import com.seeex.tech.common.exception.BizException;
import com.seeex.tech.web.registry.ApplicationRegistryService;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
/** 首次交付不能被旧应用管理接口提前轮换或改写；本轮未执行。 */
class InitialDeliveryGateTest {
    @Test void ordinaryManagementCannotInvalidateAnUnacknowledgedDelivery() {
        var registry=new ApplicationRegistryService();var command=new ApplicationRegistryService.RegisterAppCommand("sample","应用","https://app.example.com",null);var created=registry.registerForRequest(command,7L);
        assertTrue(created.app().initialDeliveryPending());assertThrows(BizException.class,()->registry.rotateCiToken("sample"));assertThrows(BizException.class,()->registry.rotateClientSecret("sample"));assertThrows(BizException.class,()->registry.update("sample",command));assertThrows(BizException.class,()->registry.reissueForRequest("sample",8L));
    }
}
