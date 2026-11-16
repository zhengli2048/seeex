package com.seeex.tech.web.rbac.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/** RBAC 角色。 */
@TableName("role")
public class RoleDO {
    @TableId(type = com.baomidou.mybatisplus.annotation.IdType.AUTO)
    private Long id;
    private String code;
    private String name;
    private String status;
    private java.time.LocalDateTime deletedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public java.time.LocalDateTime getDeletedAt() { return deletedAt; }
    public void setDeletedAt(java.time.LocalDateTime v) { deletedAt = v; }
    @com.baomidou.mybatisplus.annotation.TableField(insertStrategy=com.baomidou.mybatisplus.annotation.FieldStrategy.NEVER, updateStrategy=com.baomidou.mybatisplus.annotation.FieldStrategy.NEVER)
    private java.time.LocalDateTime updatedAt;
    /** 读取更新时间。 */
    public java.time.LocalDateTime getUpdatedAt() { return updatedAt; }
    /** 设置更新时间。 */
    public void setUpdatedAt(java.time.LocalDateTime value) { updatedAt = value; }
}
