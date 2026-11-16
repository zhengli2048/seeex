package com.seeex.tech.web.wecom;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
/** 企业微信职位到本地职级的显式映射。 */
@Data @TableName("wecom_grade_mapping")
public class WeComGradeMappingDO {
    @TableId(type=IdType.AUTO) private Long id;
    private String externalPosition;
    private Long jobGradeId;
    private String status;
}
