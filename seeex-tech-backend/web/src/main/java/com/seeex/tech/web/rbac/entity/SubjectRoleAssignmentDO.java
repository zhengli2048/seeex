package com.seeex.tech.web.rbac.entity;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/** 主体角色绑定。 */
@TableName("subject_role_assignment")
public class SubjectRoleAssignmentDO {
    @TableId(type = com.baomidou.mybatisplus.annotation.IdType.AUTO) private Long id;
    private String subjectType;
    private Long subjectId;
    private Long roleId;
    private String assignmentSource;
    private String sourceKey;
    private String syncRunId;
    private java.time.LocalDateTime deletedAt;

    public Long getId() { return id; }
    public void setId(Long v) { id = v; }
    public String getSubjectType() { return subjectType; }
    public void setSubjectType(String v) { subjectType = v; }
    public Long getSubjectId() { return subjectId; }
    public void setSubjectId(Long v) { subjectId = v; }
    public Long getRoleId() { return roleId; }
    public void setRoleId(Long v) { roleId = v; }
    public String getAssignmentSource() { return assignmentSource; }
    public void setAssignmentSource(String v) { assignmentSource = v; }
    public String getSourceKey() { return sourceKey; }
    public void setSourceKey(String v) { sourceKey = v; }
    public String getSyncRunId() { return syncRunId; }
    public void setSyncRunId(String v) { syncRunId = v; }
    public java.time.LocalDateTime getDeletedAt() { return deletedAt; }
    public void setDeletedAt(java.time.LocalDateTime v) { deletedAt = v; }
}
