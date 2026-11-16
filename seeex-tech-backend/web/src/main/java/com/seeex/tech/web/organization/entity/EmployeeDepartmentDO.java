package com.seeex.tech.web.organization.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/** 员工-部门关系。 */
@TableName("employee_department")
public class EmployeeDepartmentDO {
    @TableId(type = com.baomidou.mybatisplus.annotation.IdType.AUTO) private Long id;
    private Long employeeId;
    private Long departmentId;
    private Long jobGradeId;
    private Boolean isPrimary;
    private java.time.LocalDateTime createdAt;
    private java.time.LocalDateTime updatedAt;
    private java.time.LocalDateTime deletedAt;

    public Long getId() { return id; }
    public void setId(Long v) { id = v; }
    public Long getEmployeeId() { return employeeId; }
    public void setEmployeeId(Long v) { employeeId = v; }
    public Long getDepartmentId() { return departmentId; }
    public void setDepartmentId(Long v) { departmentId = v; }
    public Long getJobGradeId() { return jobGradeId; }
    public void setJobGradeId(Long v) { jobGradeId = v; }
    public Boolean getIsPrimary() { return isPrimary; }
    public void setIsPrimary(Boolean v) { isPrimary = v; }
    public java.time.LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(java.time.LocalDateTime v) { createdAt = v; }
    public java.time.LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(java.time.LocalDateTime v) { updatedAt = v; }
    public java.time.LocalDateTime getDeletedAt() { return deletedAt; }
    public void setDeletedAt(java.time.LocalDateTime v) { deletedAt = v; }
}
