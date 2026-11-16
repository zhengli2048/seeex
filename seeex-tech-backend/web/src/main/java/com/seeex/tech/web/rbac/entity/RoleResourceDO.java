package com.seeex.tech.web.rbac.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/** 角色与资源的授权关系。 */
@TableName("role_resource")
public class RoleResourceDO {
    @TableId(type = com.baomidou.mybatisplus.annotation.IdType.AUTO)
    private Long id;
    private Long roleId;
    private Long resourceId;
    private String effect;
    private String assignmentSource;
    private String sourceKey;
    private String syncRunId;
    private java.time.LocalDateTime deletedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getRoleId() { return roleId; }
    public void setRoleId(Long roleId) { this.roleId = roleId; }
    public Long getResourceId() { return resourceId; }
    public void setResourceId(Long resourceId) { this.resourceId = resourceId; }
    public String getEffect() { return effect; }
    public void setEffect(String effect) { this.effect = effect; }
    public String getAssignmentSource() { return assignmentSource; }
    public void setAssignmentSource(String v) { assignmentSource = v; }
    public String getSourceKey() { return sourceKey; }
    public void setSourceKey(String v) { sourceKey = v; }
    public String getSyncRunId() { return syncRunId; }
    public void setSyncRunId(String v) { syncRunId = v; }
    public java.time.LocalDateTime getDeletedAt() { return deletedAt; }
    public void setDeletedAt(java.time.LocalDateTime v) { deletedAt = v; }
}
