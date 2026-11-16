package com.seeex.tech.web.wecom;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.seeex.tech.common.api.ErrorCode;
import com.seeex.tech.common.exception.BizException;
import com.seeex.tech.web.auth.TokenSessionService;
import com.seeex.tech.web.organization.mapper.EmployeeMapper;
import com.seeex.tech.web.organization.entity.EmployeeDO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.UUID;

/** 浏览器绑定的企业微信单次 OAuth 状态与真实会话签发。 */
@Service
@RequiredArgsConstructor
public class WeComLoginService {
    private final WeComOAuthStateMapper states;
    private final WeComSettingsService settings;
    private final WeComClient client;
    private final EmployeeMapper employees;
    private final TokenSessionService sessions;
    /** 生成已持久化的短期状态。 */
    public Authorize authorize() {
        if (!settings.current().loginEnabled() || !client.configured()) { throw new BizException(ErrorCode.FORBIDDEN, "企业微信登录未启用"); }
        String state = UUID.randomUUID().toString();
        WeComOAuthStateDO row = new WeComOAuthStateDO(); row.setStateHash(hash(state)); row.setExpiresAt(LocalDateTime.now().plusMinutes(10)); states.insert(row);
        return new Authorize(client.authorizeUrl(state), state, 600);
    }
    /** 先以单条CAS提交消费状态，再在事务外回源身份；会话由独立服务事务签发。 */
    public TokenSessionService.TokenPair callback(String code, String state, String cookie) {
        if (state == null || cookie == null || !java.security.MessageDigest.isEqual(state.getBytes(StandardCharsets.UTF_8), cookie.getBytes(StandardCharsets.UTF_8))) { throw new BizException(ErrorCode.FORBIDDEN, "OAuth 状态不匹配，请重新登录"); }
        if (!settings.current().loginEnabled()) { throw new BizException(ErrorCode.FORBIDDEN, "企业微信登录已关闭"); }
        int consumed = states.update(null, Wrappers.<WeComOAuthStateDO>lambdaUpdate().eq(WeComOAuthStateDO::getStateHash, hash(state)).isNull(WeComOAuthStateDO::getUsedAt)
                .gt(WeComOAuthStateDO::getExpiresAt, LocalDateTime.now()).set(WeComOAuthStateDO::getUsedAt, LocalDateTime.now()));
        if (consumed != 1) { throw new BizException(ErrorCode.UNAUTHORIZED, "OAuth 状态已过期或已使用"); }
        var user = client.getUserInfo(client.getToken().access_token(), code);
        if (user.UserId() == null) { throw new BizException(ErrorCode.UNAUTHORIZED, "未取得企业微信成员身份"); }
        EmployeeDO employee = employees.selectOne(Wrappers.<EmployeeDO>lambdaQuery().eq(EmployeeDO::getEmployeeNo, "wc_" + user.UserId())
                .eq(EmployeeDO::getStatus, "ACTIVE").isNull(EmployeeDO::getDeletedAt));
        if (employee == null) { throw new BizException(ErrorCode.FORBIDDEN, "成员尚未同步或已停用"); }
        return sessions.issue(String.valueOf(employee.getId()), "wecom");
    }
    private String hash(String value) { try { return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); } catch (java.security.NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); } }
    /** 授权地址及状态。 */
    public record Authorize(String authorizeUrl, String state, long expiresIn) { }
}
