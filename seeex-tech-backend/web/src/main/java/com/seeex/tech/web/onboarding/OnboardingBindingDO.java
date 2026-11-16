package com.seeex.tech.web.onboarding;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
/** 一个环境只能绑定一个新申请流程；发布时同事务替换。 */
@Data @TableName("onboarding_binding")
public class OnboardingBindingDO {
    @TableId(type=IdType.INPUT) private String environment;
    private Long definitionId;
    private Long rowVersion;
}
