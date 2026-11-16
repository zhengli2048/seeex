package com.seeex.tech.web.auth;

import com.seeex.tech.common.api.ErrorCode;
import com.seeex.tech.common.exception.BizException;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import java.time.LocalDateTime;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** 短期 Access Token、轮换 Refresh Token 和撤销的认证会话模块。 */
@Service
public class TokenSessionService {
    private static final long ACCESS_TTL_SECONDS = 3600;
    private static final long REFRESH_TTL_SECONDS = 30L * 24 * 3600;
    private final Map<String, Session> sessions = new ConcurrentHashMap<>();
    @Autowired private com.seeex.tech.web.auth.mapper.EmployeeAccountMapper accounts;
    private final AuthTokenSessionMapper tokenMapper;
    private final JwtService jwtService;
    private final com.seeex.tech.web.organization.mapper.EmployeeMapper employeeMapper;

    public TokenSessionService() { this(null, null, null); }
    public TokenSessionService(AuthTokenSessionMapper tokenMapper) { this(tokenMapper, null, null); }
    @Autowired public TokenSessionService(AuthTokenSessionMapper tokenMapper, JwtService jwtService, com.seeex.tech.web.organization.mapper.EmployeeMapper employeeMapper) { this.tokenMapper = tokenMapper; this.jwtService = jwtService; this.employeeMapper = employeeMapper; }

    @org.springframework.transaction.annotation.Transactional
    public TokenPair issue(String subjectId, String clientId) { return issueInternal(subjectId,clientId,null,null); }
    /** 本地密码验证的账号/版本固定到会话，重置并发签发也不能逃逸撤销。 */
    @org.springframework.transaction.annotation.Transactional
    public TokenPair issueLocal(AccountService.VerifiedAccount verified) {
        if(verified==null)throw new BizException(ErrorCode.UNAUTHORIZED);
        requireAccount(verified.accountId(),verified.authVersion(),String.valueOf(verified.employeeId()));
        return issueInternal(String.valueOf(verified.employeeId()),"web-console",verified.accountId(),verified.authVersion());
    }
    private TokenPair issueInternal(String subjectId,String clientId,Long accountId,Long accountVersion) {
        requireActive(subjectId);
        String access = jwtService == null ? token() : jwtService.issue(subjectId, clientId, ACCESS_TTL_SECONDS);
        String refresh = token();
        Session session = new Session(subjectId, clientId, access, refresh,
                Instant.now().plusSeconds(ACCESS_TTL_SECONDS), Instant.now().plusSeconds(REFRESH_TTL_SECONDS), false);
        if(tokenMapper==null) sessions.put(refresh, session);
        if (tokenMapper != null) {
            AuthTokenSessionDO row = new AuthTokenSessionDO();
            row.setAccountId(accountId); row.setAccountVersion(accountVersion);
            row.setSubjectId(subjectId); row.setClientId(clientId); row.setAccessTokenHash(hash(access));
            row.setRefreshTokenHash(hash(refresh)); row.setAccessExpiresAt(LocalDateTime.ofInstant(session.accessExpiresAt(), java.time.ZoneOffset.UTC));
            row.setRefreshExpiresAt(LocalDateTime.ofInstant(session.refreshExpiresAt(), java.time.ZoneOffset.UTC)); row.setStatus("ACTIVE");
            tokenMapper.insert(row);
        }
        return new TokenPair(access, refresh, ACCESS_TTL_SECONDS, REFRESH_TTL_SECONDS);
    }

    @org.springframework.transaction.annotation.Transactional
    public TokenPair rotate(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) { throw new BizException(ErrorCode.UNAUTHORIZED, "刷新令牌缺失"); }
        if (tokenMapper != null) {
            AuthTokenSessionDO row = tokenMapper.selectOne(Wrappers.<AuthTokenSessionDO>lambdaQuery().eq(AuthTokenSessionDO::getRefreshTokenHash, hash(refreshToken)).eq(AuthTokenSessionDO::getStatus, "ACTIVE"));
            if (row == null || row.getRefreshExpiresAt() == null || row.getRefreshExpiresAt().isBefore(LocalDateTime.now(java.time.ZoneOffset.UTC))) {
                throw new BizException(ErrorCode.UNAUTHORIZED, "refresh token invalid or expired");
            }
            int rotated = tokenMapper.update(null, Wrappers.<AuthTokenSessionDO>lambdaUpdate().eq(AuthTokenSessionDO::getId, row.getId()).eq(AuthTokenSessionDO::getStatus, "ACTIVE").set(AuthTokenSessionDO::getStatus, "REVOKED").set(AuthTokenSessionDO::getRevokedAt, LocalDateTime.now(java.time.ZoneOffset.UTC)));
            if (rotated != 1) { throw new BizException(ErrorCode.UNAUTHORIZED, "刷新令牌已被使用"); }
            requireActive(row.getSubjectId());
            requireAccount(row.getAccountId(),row.getAccountVersion(),row.getSubjectId());
            TokenPair pair = issueInternal(row.getSubjectId(), row.getClientId(),row.getAccountId(),row.getAccountVersion());
            return pair;
        }
        Session current = sessions.remove(refreshToken);
        if (current == null || current.revoked() || current.refreshExpiresAt().isBefore(Instant.now())) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "刷新令牌无效或已过期");
        }
        return issue(current.subjectId(), current.clientId());
    }

    public void revoke(String refreshToken) {
        Session current = sessions.get(refreshToken);
        if (current != null) { sessions.put(refreshToken, current.revokedCopy()); }
        if (tokenMapper != null) { tokenMapper.update(null, Wrappers.<AuthTokenSessionDO>lambdaUpdate()
                .eq(AuthTokenSessionDO::getRefreshTokenHash, hash(refreshToken)).set(AuthTokenSessionDO::getStatus, "REVOKED").set(AuthTokenSessionDO::getRevokedAt, LocalDateTime.now(java.time.ZoneOffset.UTC))); }
    }

    public Session introspect(String accessToken) {
        if (jwtService != null) {
            try {
                var claims = jwtService.verify(accessToken);
                requireActive(String.valueOf(claims.get("sub")));
                if(tokenMapper!=null) {
                    var stored=tokenMapper.selectOne(Wrappers.<AuthTokenSessionDO>lambdaQuery().eq(AuthTokenSessionDO::getAccessTokenHash,hash(accessToken)).eq(AuthTokenSessionDO::getStatus,"ACTIVE").gt(AuthTokenSessionDO::getAccessExpiresAt,LocalDateTime.now(java.time.ZoneOffset.UTC)));
                    if(stored==null)throw new BizException(ErrorCode.UNAUTHORIZED,"会话已撤销");
                    requireAccount(stored.getAccountId(),stored.getAccountVersion(),stored.getSubjectId());
                }
                return new Session(String.valueOf(claims.get("sub")), String.valueOf(claims.get("client_id")), accessToken, "",
                        Instant.ofEpochSecond(((Number) claims.get("exp")).longValue()), Instant.MAX, false);
            } catch (RuntimeException ex) { throw new BizException(ErrorCode.UNAUTHORIZED, "访问令牌无效或会话已撤销"); }
        }
        return sessions.values().stream().filter(session -> session.accessToken().equals(accessToken)
                && !session.revoked() && session.accessExpiresAt().isAfter(Instant.now())).findFirst()
                .orElseThrow(() -> new BizException(ErrorCode.UNAUTHORIZED, "访问令牌无效或已过期"));
    }

    private void requireAccount(Long accountId,Long version,String subject) {
        if(accountId==null)return;
        var account=accounts==null?null:accounts.selectById(accountId);
        if(account==null || !"ACTIVE".equals(account.getStatus()) || !java.util.Objects.equals(String.valueOf(account.getEmployeeId()),subject) || !java.util.Objects.equals(account.getAuthVersion(),version))throw new BizException(ErrorCode.UNAUTHORIZED,"账号凭证已变更，请重新登录");
    }
    private void requireActive(String subjectId) {
        if (employeeMapper == null) { return; }
        var employee = employeeMapper.selectById(Long.parseLong(subjectId));
        if (employee == null || employee.getDeletedAt() != null || !"ACTIVE".equals(employee.getStatus())) { throw new BizException(ErrorCode.UNAUTHORIZED, "员工已停用或离职"); }
    }

    private String token() { return UUID.randomUUID().toString().replace("-", ""); }
    private String hash(String value) { try { return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8))); } catch (java.security.NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); } }

    public record TokenPair(String accessToken, String refreshToken, long expiresIn, long refreshExpiresIn) { }
    public record Session(String subjectId, String clientId, String accessToken, String refreshToken,
                          Instant accessExpiresAt, Instant refreshExpiresAt, boolean revoked) {
        Session revokedCopy() { return new Session(subjectId, clientId, accessToken, refreshToken,
                accessExpiresAt, refreshExpiresAt, true); }
    }
}
