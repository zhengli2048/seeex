package com.seeex.tech.web.governance;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
/** 应用治理持久化记录。 */
@Data
@TableName("application_admin")
public class ApplicationAdminDO {
    @TableId(type = IdType.AUTO) private Long id;
    private String applicationCode;
    private Long employeeId;
}
