package com.seeex.tech.web.auth;
import com.seeex.tech.common.exception.BizException;
import com.seeex.tech.web.auth.entity.EmployeeAccountDO;
import com.seeex.tech.web.auth.mapper.EmployeeAccountMapper;
import com.seeex.tech.web.organization.mapper.EmployeeMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;
/** 旧密码校验结果不能在密码已变更后被当作新凭证签发；本轮未执行。 */
class CredentialEpochTest {
    @Test void verificationFromOldCredentialVersionCannotIssueLocalSession() {
        var sessions=mock(AuthTokenSessionMapper.class);var accounts=mock(EmployeeAccountMapper.class);var service=new TokenSessionService(sessions,mock(JwtService.class),mock(EmployeeMapper.class));ReflectionTestUtils.setField(service,"accounts",accounts);
        var account=new EmployeeAccountDO();account.setId(3L);account.setEmployeeId(7L);account.setStatus("ACTIVE");account.setAuthVersion(2L);when(accounts.selectById(3L)).thenReturn(account);
        assertThrows(BizException.class,()->service.issueLocal(new AccountService.VerifiedAccount(3L,7L,1L)));verify(sessions,never()).insert(any(AuthTokenSessionDO.class));
    }
}
