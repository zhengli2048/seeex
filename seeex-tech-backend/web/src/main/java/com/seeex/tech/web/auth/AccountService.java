package com.seeex.tech.web.auth;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.seeex.tech.web.auth.entity.EmployeeAccountDO;
import com.seeex.tech.web.auth.mapper.EmployeeAccountMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/** 员工账号服务：登录凭证校验与查询。 */
@Service
@RequiredArgsConstructor
public class AccountService {

    private final EmployeeAccountMapper accountMapper;
    private final com.seeex.tech.web.organization.mapper.EmployeeMapper employeeMapper;
    private final PasswordEncoder passwordEncoder;
    private final AuthTokenSessionMapper sessions;

    /** 校验登录手机号与密码。 */
    public boolean verify(String phone, String rawPassword) {
        EmployeeAccountDO account = findByPhone(phone);
        if (account == null || !"ACTIVE".equals(account.getStatus())) {
            return false;
        }
        var employee = employeeMapper.selectById(account.getEmployeeId());
        return employee != null && employee.getDeletedAt() == null && "ACTIVE".equals(employee.getStatus())
                && rawPassword != null && account.getPasswordHash() != null && passwordEncoder.matches(rawPassword, account.getPasswordHash());
    }

    /** 按登录手机号查询账号。 */
    public EmployeeAccountDO findByPhone(String phone) {
        return accountMapper.selectOne(new LambdaQueryWrapper<EmployeeAccountDO>()
                .eq(EmployeeAccountDO::getLoginPhone, phone));
    }
    /** 查询账号可编辑信息，不返回密码摘要。 */
    public AccountView account(long employeeId) {
        var row = accountMapper.selectOne(new LambdaQueryWrapper<EmployeeAccountDO>().eq(EmployeeAccountDO::getEmployeeId, employeeId));
        return row == null ? new AccountView(false, "", "ACTIVE") : new AccountView(true, row.getLoginPhone(), row.getStatus());
    }

    /** 创建或重置本地登录账号，留空密码保留原摘要。 */
    @org.springframework.transaction.annotation.Transactional
    public AccountView save(long employeeId, AccountCommand command) {
        var employee = employeeMapper.selectById(employeeId);
        if (employee == null || employee.getDeletedAt() != null) { throw new com.seeex.tech.common.exception.BizException(com.seeex.tech.common.api.ErrorCode.RESOURCE_NOT_FOUND, "员工不存在"); }
        if (command.loginPhone() == null || command.loginPhone().isBlank() || command.loginPhone().length() > 32 || !java.util.Set.of("ACTIVE", "DISABLED").contains(command.status() == null ? "" : command.status())) {
            throw new com.seeex.tech.common.exception.BizException(com.seeex.tech.common.api.ErrorCode.VALIDATION_FAILED, "登录账号或状态不合法");
        }
        if (accountMapper.selectCount(new LambdaQueryWrapper<EmployeeAccountDO>().eq(EmployeeAccountDO::getLoginPhone, command.loginPhone()).ne(EmployeeAccountDO::getEmployeeId, employeeId)) > 0) { throw new com.seeex.tech.common.exception.BizException(com.seeex.tech.common.api.ErrorCode.CONFLICT, "登录账号已存在"); }
        var row = accountMapper.selectOne(new LambdaQueryWrapper<EmployeeAccountDO>().eq(EmployeeAccountDO::getEmployeeId, employeeId));
        boolean fresh = row == null;
        boolean revoke = !fresh && (!java.util.Objects.equals(row.getLoginPhone(), command.loginPhone()) || "DISABLED".equals(command.status()) || command.password() != null && !command.password().isBlank());
        if (fresh) { row = new EmployeeAccountDO(); row.setEmployeeId(employeeId); }
        if (command.password() != null && !command.password().isBlank()) {
            if (command.password().length() < 8 || command.password().getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72) { throw new com.seeex.tech.common.exception.BizException(com.seeex.tech.common.api.ErrorCode.VALIDATION_FAILED, "密码至少 8 字符，UTF-8 不超过 72 字节"); }
            row.setPasswordHash(passwordEncoder.encode(command.password()));
        } else if (fresh) { throw new com.seeex.tech.common.exception.BizException(com.seeex.tech.common.api.ErrorCode.VALIDATION_FAILED, "新账号必须设置密码"); }
        row.setLoginPhone(command.loginPhone()); row.setStatus(command.status());
        if (fresh) { accountMapper.insert(row); } else { accountMapper.updateById(row); }
        if (revoke) { sessions.update(null, com.baomidou.mybatisplus.core.toolkit.Wrappers.<AuthTokenSessionDO>lambdaUpdate()
                .eq(AuthTokenSessionDO::getSubjectId, String.valueOf(employeeId)).set(AuthTokenSessionDO::getStatus, "REVOKED").set(AuthTokenSessionDO::getRevokedAt, java.time.LocalDateTime.now(java.time.ZoneOffset.UTC))); }
        return account(employeeId);
    }
    /** 不含凭证的账号视图。 */
    public record AccountView(boolean exists, String loginPhone, String status) { }
    /** 账号维护表单。 */
    public record AccountCommand(String loginPhone, String password, String status) { }
}
