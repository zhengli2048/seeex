package com.seeex.tech.web.registry.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("application_route_release")
public class ApplicationRouteReleaseDO {
    @TableId(type = com.baomidou.mybatisplus.annotation.IdType.AUTO) private Long id;
    private String appCode;
    private String version;
    private String gitCommit;
    private String environment;
    private String baseUrl;
    private String metadataJson;
    private String status;
    private LocalDateTime reportedAt;
    /** 读取Id 字段。 */
    public Long getId() { return id; }
    /** 设置Id 字段。 */
    public void setId(Long value) { id = value; }
    /** 读取AppCode 字段。 */
    public String getAppCode() { return appCode; }
    /** 设置AppCode 字段。 */
    public void setAppCode(String value) { appCode = value; }
    /** 读取Version 字段。 */
    public String getVersion() { return version; }
    /** 设置Version 字段。 */
    public void setVersion(String value) { version = value; }
    /** 读取GitCommit 字段。 */
    public String getGitCommit() { return gitCommit; }
    /** 设置GitCommit 字段。 */
    public void setGitCommit(String value) { gitCommit = value; }
    /** 读取Environment 字段。 */
    public String getEnvironment() { return environment; }
    /** 设置Environment 字段。 */
    public void setEnvironment(String value) { environment = value; }
    /** 读取BaseUrl 字段。 */
    public String getBaseUrl() { return baseUrl; }
    /** 设置BaseUrl 字段。 */
    public void setBaseUrl(String value) { baseUrl = value; }
    /** 读取MetadataJson 字段。 */
    public String getMetadataJson() { return metadataJson; }
    /** 设置MetadataJson 字段。 */
    public void setMetadataJson(String value) { metadataJson = value; }
    /** 读取Status 字段。 */
    public String getStatus() { return status; }
    /** 设置Status 字段。 */
    public void setStatus(String value) { status = value; }
    /** 读取ReportedAt 字段。 */
    public LocalDateTime getReportedAt() { return reportedAt; }
    /** 设置ReportedAt 字段。 */
    public void setReportedAt(LocalDateTime value) { reportedAt = value; }
    private String approvalStatus;
    /** 读取ApprovalStatus 字段。 */
    public String getApprovalStatus() { return approvalStatus; }
    /** 设置ApprovalStatus 字段。 */
    public void setApprovalStatus(String value) { approvalStatus = value; }
    private Integer rolloutPercent;
    /** 读取RolloutPercent 字段。 */
    public Integer getRolloutPercent() { return rolloutPercent; }
    /** 设置RolloutPercent 字段。 */
    public void setRolloutPercent(Integer value) { rolloutPercent = value; }
}
