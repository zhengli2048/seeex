package com.seeex.tech.web.onboarding;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
/** 申请和已注册应用的持久编码所有权，主键唯一防止并发直建抢占。 */
@Data @TableName("onboarding_reservation")
public class OnboardingReservationDO {
    @TableId(type=IdType.INPUT) private String appCode;
    private Long requestId;
}
