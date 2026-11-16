package com.seeex.tech.web.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.seeex.tech.web.auth.entity.EmployeeAccountDO;
import com.seeex.tech.web.auth.mapper.EmployeeAccountMapper;
import com.seeex.tech.web.organization.entity.EmployeeDO;
import com.seeex.tech.web.organization.mapper.EmployeeMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/** 启动时初始化默认管理员账号。 */
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    /** 默认登录手机号。 */
    private static final String DEFAULT_LOGIN_PHONE = "admin";

    /** 默认初始密码（首次部署后应修改）。 */
    private static final String DEFAULT_PASSWORD = "Seeex@2026";

    private final EmployeeMapper employeeMapper;
    private final EmployeeAccountMapper accountMapper;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        EmployeeDO employee = employeeMapper.selectOne(new LambdaQueryWrapper<EmployeeDO>()
                .eq(EmployeeDO::getEmployeeNo, "E0001"));
        if (employee == null) {
            return;
        }
        Long count = accountMapper.selectCount(new LambdaQueryWrapper<EmployeeAccountDO>()
                .eq(EmployeeAccountDO::getEmployeeId, employee.getId()));
        if (count == null || count == 0) {
            EmployeeAccountDO account = new EmployeeAccountDO();
            account.setEmployeeId(employee.getId());
            account.setLoginPhone(DEFAULT_LOGIN_PHONE);
            account.setPasswordHash(passwordEncoder.encode(DEFAULT_PASSWORD));
            account.setStatus("ACTIVE");
            accountMapper.insert(account);
        }
    }
}
