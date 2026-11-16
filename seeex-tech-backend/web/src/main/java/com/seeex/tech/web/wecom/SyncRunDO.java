package com.seeex.tech.web.wecom;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("sync_run")
public class SyncRunDO {
    @TableId(type = com.baomidou.mybatisplus.annotation.IdType.AUTO) private Long id;
    private String runNo;
    private String syncType;
    private String status;
    private Integer userCount;
    private String errorMessage;
    private LocalDateTime createdAt;
    private LocalDateTime finishedAt;
    public Long getId(){return id;} public void setId(Long v){id=v;}
    public String getRunNo(){return runNo;} public void setRunNo(String v){runNo=v;}
    public String getSyncType(){return syncType;} public void setSyncType(String v){syncType=v;}
    public String getStatus(){return status;} public void setStatus(String v){status=v;}
    public Integer getUserCount(){return userCount;} public void setUserCount(Integer v){userCount=v;}
    public String getErrorMessage(){return errorMessage;} public void setErrorMessage(String v){errorMessage=v;}
    public LocalDateTime getCreatedAt(){return createdAt;} public void setCreatedAt(LocalDateTime v){createdAt=v;}
    public LocalDateTime getFinishedAt(){return finishedAt;} public void setFinishedAt(LocalDateTime v){finishedAt=v;}
    private Integer failedCount;
    /** 读取失败数量。 */
    public Integer getFailedCount() { return failedCount; }
    /** 设置失败数量。 */
    public void setFailedCount(Integer value) { failedCount = value; }
    private String ownerToken;
    /** 读取ownerToken。 */
    public String getOwnerToken() { return ownerToken; }
    /** 设置ownerToken。 */
    public void setOwnerToken(String value) { ownerToken = value; }
    private java.time.LocalDateTime leaseUntil;
    /** 读取leaseUntil。 */
    public java.time.LocalDateTime getLeaseUntil() { return leaseUntil; }
    /** 设置leaseUntil。 */
    public void setLeaseUntil(java.time.LocalDateTime value) { leaseUntil = value; }
    private Integer attemptCount;
    /** 读取attemptCount。 */
    public Integer getAttemptCount() { return attemptCount; }
    /** 设置attemptCount。 */
    public void setAttemptCount(Integer value) { attemptCount = value; }
}
