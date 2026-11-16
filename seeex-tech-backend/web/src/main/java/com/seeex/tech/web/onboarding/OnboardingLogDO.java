package com.seeex.tech.web.onboarding;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

/** 应用接入Log持久记录；控制器只返回专用视图。 */
@Data
@TableName("onboarding_log")
public class OnboardingLogDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long requestId;
    private Integer attemptNo;
    private Long actorId;
    private String action;
    private String comment;
    private LocalDateTime createdAt;
}
