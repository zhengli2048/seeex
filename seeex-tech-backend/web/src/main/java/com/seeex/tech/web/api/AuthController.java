package com.seeex.tech.web.api;

import com.seeex.tech.common.api.ApiResponse;
import com.seeex.tech.common.api.ErrorCode;
import com.seeex.tech.common.exception.BizException;
import com.seeex.tech.web.auth.AccountService;
import com.seeex.tech.web.auth.TokenSessionService;
import com.seeex.tech.web.auth.entity.EmployeeAccountDO;
import org.springframework.web.bind.annotation.*;

/** 认证接口。 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {
    public record LoginRequest(String phone, String password) {}
    public record RefreshRequest(String refreshToken) { }
    public record AccessRequest(String accessToken) { }
    private final TokenSessionService sessions;
    private final AccountService accountService;
    public AuthController(TokenSessionService sessions, AccountService accountService) { this.sessions = sessions; this.accountService = accountService; }

    /** 本地账号密码登录。 */
    @PostMapping("/login")
    @com.seeex.tech.web.audit.AuditLog(action = "LOGIN", resourceType = "AUTH")
    public ApiResponse<TokenSessionService.TokenPair> login(@RequestBody LoginRequest request) {
        if (request.phone() == null || request.phone().isBlank() || request.phone().length() > 64 || request.password() == null || request.password().isBlank() || request.password().getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72) { throw new BizException(ErrorCode.VALIDATION_FAILED, "请填写有效的账号和密码"); }
        if (!accountService.verify(request.phone(), request.password())) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "账号或密码错误");
        }
        EmployeeAccountDO account = accountService.findByPhone(request.phone());
        return ApiResponse.ok(sessions.issue(String.valueOf(account.getEmployeeId()), "web-console"));
    }

    @PostMapping("/refresh") public ApiResponse<TokenSessionService.TokenPair> refresh(@RequestBody RefreshRequest request) { return ApiResponse.ok(sessions.rotate(request.refreshToken())); }
    @PostMapping("/logout") public ApiResponse<Void> logout(@RequestBody(required = false) RefreshRequest request) { if (request != null && request.refreshToken() != null) sessions.revoke(request.refreshToken()); return ApiResponse.ok(null); }
    @PostMapping("/introspect") public ApiResponse<TokenSessionService.Session> introspect(@RequestBody AccessRequest request) { return ApiResponse.ok(sessions.introspect(request.accessToken())); }
}
