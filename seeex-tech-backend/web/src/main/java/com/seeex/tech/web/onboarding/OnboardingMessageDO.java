package com.seeex.tech.web.onboarding;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

/** 应用接入Message持久记录；控制器只返回专用视图。 */
@Data
@TableName("onboarding_message")
public class OnboardingMessageDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long requestId;
    private Long recipientId;
    private String eventKey;
    private String title;
    private String content;
    private LocalDateTime readAt;
    private Long rowVersion;
    private LocalDateTime createdAt;
}
