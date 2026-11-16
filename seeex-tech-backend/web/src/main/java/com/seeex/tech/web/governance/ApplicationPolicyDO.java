package com.seeex.tech.web.governance;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
/** 应用治理持久化记录。 */
@Data
@TableName("application_policy")
public class ApplicationPolicyDO {
    @TableId(type = IdType.AUTO) private Long id;
    private String applicationCode;
    private String name;
    private String subjectType;
    private Long subjectId;
    private Long resourceId;
    private String effect;
    private String scopeType;
    private String scopeConfig;
    private String status;
    private Long rowVersion;
    /** 读取rowVersion。 */
    public Long getRowVersion() { return rowVersion; }
    /** 设置rowVersion。 */
    public void setRowVersion(Long value) { rowVersion = value; }
}
