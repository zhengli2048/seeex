package com.seeex.tech.web.wecom;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 企业微信同步事件。 */
@Data
@TableName("sync_event")
public class SyncEventDO {
    @TableId(type = com.baomidou.mybatisplus.annotation.IdType.AUTO) private Long id;
    private String eventNo;
    private String provider;
    private String eventType;
    private String payloadHash;
    private String status;
    private Long syncRunId;
    private LocalDateTime receivedAt;
    private Integer retryCount;
    private LocalDateTime nextRetryAt;
    private LocalDateTime processedAt;
    private String errorMessage;
    private String payload;
}
