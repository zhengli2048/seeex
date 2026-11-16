package com.seeex.tech.web.onboarding;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

/** 应用接入Task持久记录；控制器只返回专用视图。 */
@Data
@TableName("onboarding_task")
public class OnboardingTaskDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long requestId;
    private Integer attemptNo;
    private Integer nodeIndex;
    private String nodeName;
    private String mode;
    private Long candidateId;
    private Long assigneeId;
    private String state;
    private Long rowVersion;
    private LocalDateTime dueAt;
    private LocalDateTime createdAt;
    private LocalDateTime completedAt;
}
