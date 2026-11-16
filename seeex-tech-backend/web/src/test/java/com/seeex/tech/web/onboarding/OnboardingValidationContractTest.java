package com.seeex.tech.web.onboarding;
import com.seeex.tech.common.exception.BizException;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static com.seeex.tech.web.onboarding.OnboardingContract.*;
/** 域名与模型不能绕过审批范围；本轮未执行。 */
class OnboardingValidationContractTest {
    private final OnboardingValidation validation=new OnboardingValidation(mock(OnboardingDirectory.class));
    @Test void fulfillmentCannotSwitchToAnUnapprovedHostOrAddAnAdminEntry() {
        var form=new ApplicationForm("应用","sample","描述","用途","WEB",Set.of("WEB"),"PROD",List.of("app.example.com"),List.of(1L),"INTERNAL",List.of());
        assertThrows(BizException.class,()->validation.fulfillment(new FulfillmentForm("https://other.example.com",null,null,null,null,"/sso/callback",null),form));
        assertThrows(BizException.class,()->validation.fulfillment(new FulfillmentForm("https://app.example.com","https://app.example.com/admin",null,null,null,"/sso/callback",null),form));
        assertThrows(BizException.class,()->validation.fulfillment(new FulfillmentForm("http://app.example.com",null,null,null,null,"/sso/callback",null),form));
    }
    @Test void everyFirstOnboardingPathMustHaveSecurityApproval() {
        var people=new Selector("EMPLOYEE",List.of(2L),List.of(),List.of()); var ordinary=new ApprovalNode("approval","普通审批","ALL",people,Set.of(),Set.of(),Set.of(),24,false);
        assertThrows(BizException.class,()->validation.model(new FlowModel(List.of(ordinary),people,people)));
    }
}
