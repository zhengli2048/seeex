package com.seeex.tech.web.organization.entity;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/** 员工。 */
@TableName("employee")
public class EmployeeDO {
    @TableId(type = com.baomidou.mybatisplus.annotation.IdType.AUTO) private Long id;
    private String employeeNo;
    private String displayName;
    private String status;
    @com.baomidou.mybatisplus.annotation.TableField(updateStrategy = com.baomidou.mybatisplus.annotation.FieldStrategy.ALWAYS)
    private Long primaryDepartmentId;
    @com.baomidou.mybatisplus.annotation.TableField(updateStrategy = com.baomidou.mybatisplus.annotation.FieldStrategy.ALWAYS)
    private Long jobGradeId;
    private java.time.LocalDateTime deletedAt;

    /** 读取Id 字段。 */
    public Long getId() { return id; }
    /** 设置Id 字段。 */
    public void setId(Long v) { id = v; }
    /** 读取EmployeeNo 字段。 */
    public String getEmployeeNo() { return employeeNo; }
    /** 设置EmployeeNo 字段。 */
    public void setEmployeeNo(String v) { employeeNo = v; }
    /** 读取DisplayName 字段。 */
    public String getDisplayName() { return displayName; }
    /** 设置DisplayName 字段。 */
    public void setDisplayName(String v) { displayName = v; }
    /** 读取Status 字段。 */
    public String getStatus() { return status; }
    /** 设置Status 字段。 */
    public void setStatus(String v) { status = v; }
    /** 读取PrimaryDepartmentId 字段。 */
    public Long getPrimaryDepartmentId() { return primaryDepartmentId; }
    /** 设置PrimaryDepartmentId 字段。 */
    public void setPrimaryDepartmentId(Long v) { primaryDepartmentId = v; }
    /** 读取JobGradeId 字段。 */
    public Long getJobGradeId() { return jobGradeId; }
    /** 设置JobGradeId 字段。 */
    public void setJobGradeId(Long v) { jobGradeId = v; }
    /** 读取DeletedAt 字段。 */
    public java.time.LocalDateTime getDeletedAt() { return deletedAt; }
    /** 设置DeletedAt 字段。 */
    public void setDeletedAt(java.time.LocalDateTime v) { deletedAt = v; }
    @com.baomidou.mybatisplus.annotation.TableField(updateStrategy = com.baomidou.mybatisplus.annotation.FieldStrategy.ALWAYS)
    private String internalMobile;
    /** 读取InternalMobile 字段。 */
    public String getInternalMobile() { return internalMobile; }
    /** 设置InternalMobile 字段。 */
    public void setInternalMobile(String value) { internalMobile = value; }
    @com.baomidou.mybatisplus.annotation.TableField(updateStrategy = com.baomidou.mybatisplus.annotation.FieldStrategy.ALWAYS)
    private String internalEmail;
    /** 读取InternalEmail 字段。 */
    public String getInternalEmail() { return internalEmail; }
    /** 设置InternalEmail 字段。 */
    public void setInternalEmail(String value) { internalEmail = value; }
    private String source;
    /** 读取Source 字段。 */
    public String getSource() { return source; }
    /** 设置Source 字段。 */
    public void setSource(String value) { source = value; }
    private java.time.LocalDateTime createdAt;
    /** 读取CreatedAt 字段。 */
    public java.time.LocalDateTime getCreatedAt() { return createdAt; }
    /** 设置CreatedAt 字段。 */
    public void setCreatedAt(java.time.LocalDateTime value) { createdAt = value; }
    @com.baomidou.mybatisplus.annotation.TableField(insertStrategy=com.baomidou.mybatisplus.annotation.FieldStrategy.NEVER, updateStrategy=com.baomidou.mybatisplus.annotation.FieldStrategy.NEVER)
    private java.time.LocalDateTime updatedAt;
    /** 读取UpdatedAt 字段。 */
    public java.time.LocalDateTime getUpdatedAt() { return updatedAt; }
    /** 设置UpdatedAt 字段。 */
    public void setUpdatedAt(java.time.LocalDateTime value) { updatedAt = value; }
    private java.time.LocalDateTime lastSyncedAt;
    /** 读取LastSyncedAt 字段。 */
    public java.time.LocalDateTime getLastSyncedAt() { return lastSyncedAt; }
    /** 设置LastSyncedAt 字段。 */
    public void setLastSyncedAt(java.time.LocalDateTime value) { lastSyncedAt = value; }
    private Long syncVersion;
    /** 读取syncVersion。 */
    public Long getSyncVersion() { return syncVersion; }
    /** 设置syncVersion。 */
    public void setSyncVersion(Long value) { syncVersion = value; }
    private java.time.LocalDateTime lastEventAt;
    /** 读取已应用的通讯录事件源时间。 */
    public java.time.LocalDateTime getLastEventAt() { return lastEventAt; }
    /** 设置事件源时间，不与全量读取时刻混用。 */
    public void setLastEventAt(java.time.LocalDateTime value) { lastEventAt=value; }
}
