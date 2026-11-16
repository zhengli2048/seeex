package com.seeex.tech.web.rbac.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/** 角色数据范围。 */
@TableName("role_data_scope")
public class RoleDataScopeDO {
    @TableId(type = com.baomidou.mybatisplus.annotation.IdType.AUTO) private Long id;
    private Long roleId;
    private String resourceCode;
    private String scopeType;
    private String scopeConfig;
    private java.time.LocalDateTime createdAt;
    private java.time.LocalDateTime updatedAt;
    private java.time.LocalDateTime deletedAt;

    public Long getId() { return id; }
    public void setId(Long v) { id = v; }
    public Long getRoleId() { return roleId; }
    public void setRoleId(Long v) { roleId = v; }
    public String getResourceCode() { return resourceCode; }
    public void setResourceCode(String v) { resourceCode = v; }
    public String getScopeType() { return scopeType; }
    public void setScopeType(String v) { scopeType = v; }
    public String getScopeConfig() { return scopeConfig; }
    public void setScopeConfig(String v) { scopeConfig = v; }
    public java.time.LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(java.time.LocalDateTime v) { createdAt = v; }
    public java.time.LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(java.time.LocalDateTime v) { updatedAt = v; }
    public java.time.LocalDateTime getDeletedAt() { return deletedAt; }
    public void setDeletedAt(java.time.LocalDateTime v) { deletedAt = v; }
}
