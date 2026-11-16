package com.seeex.tech.web.organization.entity;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/** 部门。 */
@TableName("department")
public class DepartmentDO {
    @TableId(type = com.baomidou.mybatisplus.annotation.IdType.AUTO) private Long id;
    @com.baomidou.mybatisplus.annotation.TableField(updateStrategy = com.baomidou.mybatisplus.annotation.FieldStrategy.ALWAYS)
    private Long parentId;
    private String code;
    private String name;
    private String status;
    @com.baomidou.mybatisplus.annotation.TableField(updateStrategy = com.baomidou.mybatisplus.annotation.FieldStrategy.ALWAYS)
    private Long leaderEmployeeId;
    private java.time.LocalDateTime deletedAt;

    /** 读取Id 字段。 */
    public Long getId() { return id; }
    /** 设置Id 字段。 */
    public void setId(Long v) { id = v; }
    /** 读取ParentId 字段。 */
    public Long getParentId() { return parentId; }
    /** 设置ParentId 字段。 */
    public void setParentId(Long v) { parentId = v; }
    /** 读取Code 字段。 */
    public String getCode() { return code; }
    /** 设置Code 字段。 */
    public void setCode(String v) { code = v; }
    /** 读取Name 字段。 */
    public String getName() { return name; }
    /** 设置Name 字段。 */
    public void setName(String v) { name = v; }
    /** 读取Status 字段。 */
    public String getStatus() { return status; }
    /** 设置Status 字段。 */
    public void setStatus(String v) { status = v; }
    /** 读取LeaderEmployeeId 字段。 */
    public Long getLeaderEmployeeId() { return leaderEmployeeId; }
    /** 设置LeaderEmployeeId 字段。 */
    public void setLeaderEmployeeId(Long v) { leaderEmployeeId = v; }
    /** 读取DeletedAt 字段。 */
    public java.time.LocalDateTime getDeletedAt() { return deletedAt; }
    /** 设置DeletedAt 字段。 */
    public void setDeletedAt(java.time.LocalDateTime v) { deletedAt = v; }
    @com.baomidou.mybatisplus.annotation.TableField(insertStrategy=com.baomidou.mybatisplus.annotation.FieldStrategy.NEVER, updateStrategy=com.baomidou.mybatisplus.annotation.FieldStrategy.NEVER)
    private java.time.LocalDateTime updatedAt;
    /** 读取更新时间。 */
    public java.time.LocalDateTime getUpdatedAt() { return updatedAt; }
    /** 设置更新时间。 */
    public void setUpdatedAt(java.time.LocalDateTime value) { updatedAt = value; }
    private Long syncVersion;
    /** 读取syncVersion。 */
    public Long getSyncVersion() { return syncVersion; }
    /** 设置syncVersion。 */
    public void setSyncVersion(Long value) { syncVersion = value; }
    private java.time.LocalDateTime lastSyncedAt;
    /** 读取lastSyncedAt。 */
    public java.time.LocalDateTime getLastSyncedAt() { return lastSyncedAt; }
    /** 设置lastSyncedAt。 */
    public void setLastSyncedAt(java.time.LocalDateTime value) { lastSyncedAt = value; }
    private java.time.LocalDateTime lastEventAt;
    /** 读取已应用的通讯录事件源时间。 */
    public java.time.LocalDateTime getLastEventAt() { return lastEventAt; }
    /** 设置事件源时间，不与全量读取时刻混用。 */
    public void setLastEventAt(java.time.LocalDateTime value) { lastEventAt=value; }
}
