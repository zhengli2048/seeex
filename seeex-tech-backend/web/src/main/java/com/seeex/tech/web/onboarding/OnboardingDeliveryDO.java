package com.seeex.tech.web.onboarding;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

/** 应用接入Delivery持久记录；控制器只返回专用视图。 */
@Data
@TableName("onboarding_delivery")
public class OnboardingDeliveryDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long requestId;
    private Integer deliveryVersion;
    private Long recipientId;
    private String infoJson;
    @com.fasterxml.jackson.annotation.JsonIgnore
    @com.baomidou.mybatisplus.annotation.TableField(updateStrategy=com.baomidou.mybatisplus.annotation.FieldStrategy.ALWAYS)
    private String secretCipher;
    private String keyId;
    private LocalDateTime expiresAt;
    @com.baomidou.mybatisplus.annotation.TableField(updateStrategy=com.baomidou.mybatisplus.annotation.FieldStrategy.ALWAYS)
    private LocalDateTime redeemedAt;
    @com.baomidou.mybatisplus.annotation.TableField(updateStrategy=com.baomidou.mybatisplus.annotation.FieldStrategy.ALWAYS)
    private LocalDateTime acknowledgedAt;
    private Long rowVersion;
    private LocalDateTime createdAt;
}
