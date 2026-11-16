package com.seeex.tech.web.registry.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

/** 持久化的单次 SSO 交换码，仅保存摘要。 */
@Data
@TableName("auth_sso_exchange_code")
public class SsoExchangeCodeDO {
    @TableId(type = com.baomidou.mybatisplus.annotation.IdType.AUTO) private Long id;
    private String codeHash;
    private String appCode;
    private String entryType;
    private String subjectId;
    private String redirectPath;
    private LocalDateTime expiresAt;
    private LocalDateTime usedAt;
}
