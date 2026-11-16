package com.seeex.tech.web.onboarding;
import static com.seeex.tech.web.onboarding.OnboardingJson.invalid;
/** 会签/或签唯一规则，避免控制器和任务队列各自推断结果。 */
public final class OnboardingDecisionPolicy {
    private OnboardingDecisionPolicy() { }
    /** 任务已保存本次意见后，根据剩余未决数量计算节点流转。 */
    public static Outcome resolve(String mode,String decision,long pending) {
        if(pending<0 || !("ALL".equals(mode)||"ANY".equals(mode)))throw invalid("审批规则无效");
        if("RETURN".equals(decision))return Outcome.RETURNED;
        if("REJECT".equals(decision))return "ALL".equals(mode)||pending==0?Outcome.REJECTED:Outcome.WAIT;
        if("APPROVE".equals(decision))return "ANY".equals(mode)||pending==0?Outcome.ADVANCE:Outcome.WAIT;
        throw invalid("审批动作无效");
    }
    /** 节点的合法下一步，只有ADVANCE允许进入后续节点。 */
    public enum Outcome { RETURNED,REJECTED,ADVANCE,WAIT }
}
