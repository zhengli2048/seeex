package com.seeex.tech.web.onboarding;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

/** 应用接入Definition持久记录；控制器只返回专用视图。 */
@Data
@TableName("onboarding_definition")
public class OnboardingDefinitionDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String definitionKey;
    private String creationKey;
    private String creationHash;
    private String name;
    private Integer version;
    private String status;
    private String modelJson;
    private Long rowVersion;
    private Long createdBy;
    private Long publishedBy;
    private LocalDateTime publishedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
