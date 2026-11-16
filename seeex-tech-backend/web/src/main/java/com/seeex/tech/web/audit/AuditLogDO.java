package com.seeex.tech.web.audit;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 审计日志。 */
@Data
@TableName("audit_log")
public class AuditLogDO {
    @TableId(type = com.baomidou.mybatisplus.annotation.IdType.AUTO) private Long id;
    private String logNo;
    private String applicationCode;
    private String resourceKey;
    @com.baomidou.mybatisplus.annotation.TableField(exist=false) private String operatorName;
    private String operatorEmployeeId;
    private String action;
    private String resourceType;
    private Long resourceId;
    private String requestId;
    private String sourceIp;
    private String requestSummary;
    private String beforeJson;
    private String afterJson;
    private String result;
    private LocalDateTime createdAt;
}
