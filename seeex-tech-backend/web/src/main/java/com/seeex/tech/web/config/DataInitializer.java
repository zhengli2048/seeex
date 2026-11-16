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

/** 仅显式启用首次部署引导时创建管理员，无固定初始密码。 */
@Component
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name="bootstrap.admin.enabled",havingValue="true")
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    @org.springframework.beans.factory.annotation.Value("${bootstrap.admin.login:admin}") private String initialLogin;
    @org.springframework.beans.factory.annotation.Value("${bootstrap.admin.password:}") private String initialPassword;

    private final EmployeeMapper employeeMapper;
    private final EmployeeAccountMapper accountMapper;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        if(initialPassword==null || initialPassword.length()<12 || initialPassword.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>72 || "Seeex@2026".equals(initialPassword) || initialLogin==null || initialLogin.isBlank() || initialLogin.length()>32) throw new com.seeex.tech.common.exception.BizException(com.seeex.tech.common.api.ErrorCode.BUSINESS_ERROR,"初始化管理员必须提供有效账号和12字符以上、UTF-8不超过72字节的独立密码");
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
            account.setLoginPhone(initialLogin);
            account.setPasswordHash(passwordEncoder.encode(initialPassword));
            account.setStatus("ACTIVE");
            accountMapper.insert(account);
        }
    }
}
