package com.seeex.tech.web.auth.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/** 员工登录账号。 */
@TableName("employee_account")
public class EmployeeAccountDO {
    @TableId(type = com.baomidou.mybatisplus.annotation.IdType.AUTO) private Long id;
    private Long employeeId;
    private String loginPhone;
    private String passwordHash;
    private String status;

    public Long getId() { return id; }
    public void setId(Long v) { id = v; }
    public Long getEmployeeId() { return employeeId; }
    public void setEmployeeId(Long v) { employeeId = v; }
    public String getLoginPhone() { return loginPhone; }
    public void setLoginPhone(String v) { loginPhone = v; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String v) { passwordHash = v; }
    public String getStatus() { return status; }
    public void setStatus(String v) { status = v; }
}
