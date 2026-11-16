package com.seeex.tech.web.wecom;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
/** 企业微信配置密文。 */
@Data
@TableName("wecom_connection_settings")
public class WeComSettingsDO {
    @TableId(type = com.baomidou.mybatisplus.annotation.IdType.INPUT) private Long id;
    private String encryptedConfig;
}
