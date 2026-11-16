package com.seeex.tech.web.governance;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
/** 跨集合替换操作的持久化版本，删除集合后仍保留单调版本。 */
@Data @TableName("configuration_revision")
public class ConfigurationRevisionDO {
    @TableId(type=IdType.INPUT) private String configKey;
    private Long revision;
}
