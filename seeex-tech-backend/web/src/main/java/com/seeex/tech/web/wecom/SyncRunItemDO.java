package com.seeex.tech.web.wecom;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
/** 同步尝试的原子处理结果，不保存联系方式和远端原文。 */
@Data @TableName("sync_run_item")
public class SyncRunItemDO {
    @TableId(type=IdType.AUTO) private Long id;
    private String runNo;
    private Integer attemptNo;
    private String objectType;
    private String externalKey;
    private String status;
    private String errorMessage;
}
