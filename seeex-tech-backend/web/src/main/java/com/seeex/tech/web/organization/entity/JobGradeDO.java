package com.seeex.tech.web.organization.entity;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/** 职级。 */
@TableName("job_grade")
public class JobGradeDO {
    @TableId(type = com.baomidou.mybatisplus.annotation.IdType.AUTO) private Long id;
    private String code;
    private String name;
    private Integer levelValue;
    private String status;
    @com.baomidou.mybatisplus.annotation.TableField(updateStrategy = com.baomidou.mybatisplus.annotation.FieldStrategy.ALWAYS)
    private Long defaultRoleId;
    private java.time.LocalDateTime deletedAt;

    /** 读取Id 字段。 */
    public Long getId() { return id; }
    /** 设置Id 字段。 */
    public void setId(Long v) { id = v; }
    /** 读取Code 字段。 */
    public String getCode() { return code; }
    /** 设置Code 字段。 */
    public void setCode(String v) { code = v; }
    /** 读取Name 字段。 */
    public String getName() { return name; }
    /** 设置Name 字段。 */
    public void setName(String v) { name = v; }
    /** 读取LevelValue 字段。 */
    public Integer getLevelValue() { return levelValue; }
    /** 设置LevelValue 字段。 */
    public void setLevelValue(Integer v) { levelValue = v; }
    /** 读取Status 字段。 */
    public String getStatus() { return status; }
    /** 设置Status 字段。 */
    public void setStatus(String v) { status = v; }
    /** 读取DefaultRoleId 字段。 */
    public Long getDefaultRoleId() { return defaultRoleId; }
    /** 设置DefaultRoleId 字段。 */
    public void setDefaultRoleId(Long v) { defaultRoleId = v; }
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
    private String source;
    /** 读取来源。 */
    public String getSource() { return source; }
    /** 设置来源。 */
    public void setSource(String value) { source = value; }
}
