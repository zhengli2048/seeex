package com.seeex.tech.web.onboarding;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
/** 办理候选参与关系，仅用于本人队列索引，动作仍验证实时资格。 */
@Data @TableName("onboarding_participant")
public class OnboardingParticipantDO {
    @TableId(type=IdType.AUTO) private Long id;
    private Long requestId;
    private Long actorId;
    private Integer attemptNo;
}
