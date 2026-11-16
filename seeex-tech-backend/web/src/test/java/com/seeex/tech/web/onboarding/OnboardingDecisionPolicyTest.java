package com.seeex.tech.web.onboarding;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static com.seeex.tech.web.onboarding.OnboardingDecisionPolicy.*;
/** 会签一票否决、或签单次通过及全部拒绝的行为契约；本轮未执行。 */
class OnboardingDecisionPolicyTest {
    @Test void allNeedsEveryApprovalButOneRejectionEndsTheNode() { assertEquals(Outcome.WAIT,resolve("ALL","APPROVE",1)); assertEquals(Outcome.ADVANCE,resolve("ALL","APPROVE",0)); assertEquals(Outcome.REJECTED,resolve("ALL","REJECT",3)); }
    @Test void anyCancelsOtherTasksOnlyAfterOneApprovalOrEveryRejection() { assertEquals(Outcome.ADVANCE,resolve("ANY","APPROVE",3)); assertEquals(Outcome.WAIT,resolve("ANY","REJECT",1)); assertEquals(Outcome.REJECTED,resolve("ANY","REJECT",0)); }
    @Test void returnEndsCurrentAttemptInEitherMode() { assertEquals(Outcome.RETURNED,resolve("ALL","RETURN",2)); assertEquals(Outcome.RETURNED,resolve("ANY","RETURN",0)); }
}
