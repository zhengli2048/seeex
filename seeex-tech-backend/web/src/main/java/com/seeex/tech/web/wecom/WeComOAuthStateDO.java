package com.seeex.tech.web.wecom;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;
/** 企业微信登录一次性状态摘要。 */
@Data
@TableName("wecom_oauth_state")
public class WeComOAuthStateDO {
    @TableId(type = com.baomidou.mybatisplus.annotation.IdType.AUTO) private Long id;
    private String stateHash;
    private LocalDateTime expiresAt;
    private LocalDateTime usedAt;
}
