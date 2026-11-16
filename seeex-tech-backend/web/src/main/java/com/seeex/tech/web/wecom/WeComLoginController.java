package com.seeex.tech.web.wecom;
import com.seeex.tech.common.api.ApiResponse;
import com.seeex.tech.web.auth.TokenSessionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseCookie;
import org.springframework.web.bind.annotation.*;
/** 企业微信 OAuth 登录，状态 Cookie 仅服务端可读。 */
@RestController
@RequestMapping("/api/auth/wecom")
@RequiredArgsConstructor
public class WeComLoginController {
    private final WeComLoginService login;
    private final WeComSettingsService settings;
    /** 登录页读取不含企业标识和密钥的可用状态。 */
    @GetMapping("/status")
    public ApiResponse<LoginStatus> status() { var value = settings.view(); return ApiResponse.ok(new LoginStatus(value.configured() && value.loginEnabled())); }
    /** 生成授权地址并绑定当前浏览器。 */
    @GetMapping("/authorize")
    public ApiResponse<WeComLoginService.Authorize> authorize(HttpServletRequest request, HttpServletResponse response) {
        var value = login.authorize(); response.addHeader("Set-Cookie", ResponseCookie.from("seeex_wecom_state", value.state()).httpOnly(true).secure(request.isSecure()).sameSite("Lax").path("/api/auth/wecom").maxAge(600).build().toString());
        return ApiResponse.ok(value);
    }
    /** 交换回调身份并清除状态 Cookie。 */
    @GetMapping("/callback")
    @com.seeex.tech.web.audit.AuditLog(action = "WECOM_LOGIN", resourceType = "AUTH")
    public ApiResponse<TokenSessionService.TokenPair> callback(@RequestParam String code, @RequestParam String state, @CookieValue(value = "seeex_wecom_state", required = false) String cookie, HttpServletResponse response) {
        var tokens = login.callback(code, state, cookie); response.addHeader("Set-Cookie", ResponseCookie.from("seeex_wecom_state", "").httpOnly(true).sameSite("Lax").path("/api/auth/wecom").maxAge(0).build().toString()); return ApiResponse.ok(tokens);
    }
    /** 公开登录可用标志。 */
    public record LoginStatus(boolean enabled) { }
}
