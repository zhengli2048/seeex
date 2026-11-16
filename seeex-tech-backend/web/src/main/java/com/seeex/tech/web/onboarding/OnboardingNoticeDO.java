package com.seeex.tech.web.onboarding;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

/** 应用接入Notice持久记录；控制器只返回专用视图。 */
@Data
@TableName("onboarding_notice")
public class OnboardingNoticeDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long messageId;
    private Long recipientId;
    private String eventKey;
    private String state;
    private Integer attempts;
    private LocalDateTime nextAttemptAt;
    private LocalDateTime leaseUntil;
    private String ownerToken;
    private String lastError;
    private Long rowVersion;
    private LocalDateTime createdAt;
}
