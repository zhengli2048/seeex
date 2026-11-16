package com.seeex.tech.web.onboarding;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

/** 应用接入Revision持久记录；控制器只返回专用视图。 */
@Data
@TableName("onboarding_revision")
public class OnboardingRevisionDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long requestId;
    private Integer revision;
    private Long definitionId;
    private String dataJson;
    private LocalDateTime submittedAt;
}
