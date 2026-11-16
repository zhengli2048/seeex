package com.seeex.tech.web.registry.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("application_registry")
public class ApplicationDO {
    @TableId(type = com.baomidou.mybatisplus.annotation.IdType.AUTO) private Long id;
    private String appCode;
    private String appName;
    private String baseUrl;
    @com.baomidou.mybatisplus.annotation.TableField(updateStrategy = com.baomidou.mybatisplus.annotation.FieldStrategy.ALWAYS)
    private String repositoryUrl;
    private String ciTokenHash;
    private String status;
    @com.baomidou.mybatisplus.annotation.TableField(updateStrategy = com.baomidou.mybatisplus.annotation.FieldStrategy.ALWAYS)
    private Long ownerEmployeeId;
    private String rolloutStrategy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    /** 读取Id 字段。 */
    public Long getId() { return id; }
    /** 设置Id 字段。 */
    public void setId(Long value) { id = value; }
    /** 读取AppCode 字段。 */
    public String getAppCode() { return appCode; }
    /** 设置AppCode 字段。 */
    public void setAppCode(String value) { appCode = value; }
    /** 读取AppName 字段。 */
    public String getAppName() { return appName; }
    /** 设置AppName 字段。 */
    public void setAppName(String value) { appName = value; }
    /** 读取BaseUrl 字段。 */
    public String getBaseUrl() { return baseUrl; }
    /** 设置BaseUrl 字段。 */
    public void setBaseUrl(String value) { baseUrl = value; }
    /** 读取RepositoryUrl 字段。 */
    public String getRepositoryUrl() { return repositoryUrl; }
    /** 设置RepositoryUrl 字段。 */
    public void setRepositoryUrl(String value) { repositoryUrl = value; }
    /** 读取CiTokenHash 字段。 */
    public String getCiTokenHash() { return ciTokenHash; }
    /** 设置CiTokenHash 字段。 */
    public void setCiTokenHash(String value) { ciTokenHash = value; }
    /** 读取Status 字段。 */
    public String getStatus() { return status; }
    /** 设置Status 字段。 */
    public void setStatus(String value) { status = value; }
    /** 读取OwnerEmployeeId 字段。 */
    public Long getOwnerEmployeeId() { return ownerEmployeeId; }
    /** 设置OwnerEmployeeId 字段。 */
    public void setOwnerEmployeeId(Long value) { ownerEmployeeId = value; }
    /** 读取RolloutStrategy 字段。 */
    public String getRolloutStrategy() { return rolloutStrategy; }
    /** 设置RolloutStrategy 字段。 */
    public void setRolloutStrategy(String value) { rolloutStrategy = value; }
    /** 读取CreatedAt 字段。 */
    public LocalDateTime getCreatedAt() { return createdAt; }
    /** 设置CreatedAt 字段。 */
    public void setCreatedAt(LocalDateTime value) { createdAt = value; }
    /** 读取UpdatedAt 字段。 */
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    /** 设置UpdatedAt 字段。 */
    public void setUpdatedAt(LocalDateTime value) { updatedAt = value; }
    @com.baomidou.mybatisplus.annotation.TableField(updateStrategy = com.baomidou.mybatisplus.annotation.FieldStrategy.ALWAYS)
    private String description;
    /** 读取Description 字段。 */
    public String getDescription() { return description; }
    /** 设置Description 字段。 */
    public void setDescription(String value) { description = value; }
    private String appType;
    /** 读取AppType 字段。 */
    public String getAppType() { return appType; }
    /** 设置AppType 字段。 */
    public void setAppType(String value) { appType = value; }
    @com.baomidou.mybatisplus.annotation.TableField(updateStrategy = com.baomidou.mybatisplus.annotation.FieldStrategy.ALWAYS)
    private String logo;
    /** 读取Logo 字段。 */
    public String getLogo() { return logo; }
    /** 设置Logo 字段。 */
    public void setLogo(String value) { logo = value; }
    @com.baomidou.mybatisplus.annotation.TableField(updateStrategy = com.baomidou.mybatisplus.annotation.FieldStrategy.ALWAYS)
    private String adminUrl;
    /** 读取AdminUrl 字段。 */
    public String getAdminUrl() { return adminUrl; }
    /** 设置AdminUrl 字段。 */
    public void setAdminUrl(String value) { adminUrl = value; }
    @com.baomidou.mybatisplus.annotation.TableField(updateStrategy = com.baomidou.mybatisplus.annotation.FieldStrategy.ALWAYS)
    private String apiUrl;
    /** 读取ApiUrl 字段。 */
    public String getApiUrl() { return apiUrl; }
    /** 设置ApiUrl 字段。 */
    public void setApiUrl(String value) { apiUrl = value; }
    private String ssoCallbackPath;
    /** 读取SsoCallbackPath 字段。 */
    public String getSsoCallbackPath() { return ssoCallbackPath; }
    /** 设置SsoCallbackPath 字段。 */
    public void setSsoCallbackPath(String value) { ssoCallbackPath = value; }
    private String activeVersion;
    /** 读取ActiveVersion 字段。 */
    public String getActiveVersion() { return activeVersion; }
    /** 设置ActiveVersion 字段。 */
    public void setActiveVersion(String value) { activeVersion = value; }
    private String clientSecretHash;
    /** 读取应用服务凭证摘要。 */
    public String getClientSecretHash() { return clientSecretHash; }
    /** 设置应用服务凭证摘要。 */
    public void setClientSecretHash(String value) { clientSecretHash = value; }
}
