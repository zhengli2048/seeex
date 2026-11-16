package com.seeex.tech.web.onboarding;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

/** 应用接入Action持久记录；控制器只返回专用视图。 */
@Data
@TableName("onboarding_action")
public class OnboardingActionDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long requestId;
    private Long actorId;
    private String operation;
    private String idempotencyKey;
    private String payloadHash;
    private LocalDateTime createdAt;
}
