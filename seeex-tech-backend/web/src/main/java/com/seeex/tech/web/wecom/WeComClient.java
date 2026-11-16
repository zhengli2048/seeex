package com.seeex.tech.web.wecom;

import com.seeex.tech.common.api.ErrorCode;
import com.seeex.tech.common.exception.BizException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** 企业微信只读适配器，连接错误不得把带令牌的 URL 暴露到日志。 */
@Component
public class WeComClient {
    private static final int CONNECT_TIMEOUT_MS = 5000;
    private static final int READ_TIMEOUT_MS = 10000;
    private static final long TOKEN_MARGIN_MS = 30000;
    private final RestClient client;
    private final WeComSettingsService settings;
    @Value("${wecom.base-url:https://qyapi.weixin.qq.com}") private String baseUrl;
    private volatile CachedToken cachedToken;
    /** 创建有明确超时的适配器。 */
    public WeComClient(WeComSettingsService settings) {
        this.settings = settings;
        var factory = new org.springframework.http.client.SimpleClientHttpRequestFactory(); factory.setConnectTimeout(CONNECT_TIMEOUT_MS); factory.setReadTimeout(READ_TIMEOUT_MS);
        client = RestClient.builder().requestFactory(factory).build();
    }
    /** 配置改变后令旧令牌立即失效。 */
    public void clearToken() { cachedToken = null; }
    /** 判断是否已配置企业凭证。 */
    public boolean configured() { var value = settings.current(); return !value.corpId().isBlank() && !value.secret().isBlank(); }
    /** 生成登录授权地址。 */
    public String authorizeUrl(String state) {
        var value = settings.current();
        return "https://open.weixin.qq.com/connect/oauth2/authorize?appid=" + encode(value.corpId()) + "&redirect_uri=" + encode(value.redirectUri())
                + "&response_type=code&scope=snsapi_base&state=" + encode(state) + (value.agentId().isBlank() ? "" : "&agentid=" + encode(value.agentId())) + "#wechat_redirect";
    }
    /** 缓存企业访问令牌。 */
    public WeComToken getToken() {
        if (!configured()) { throw new BizException(ErrorCode.VALIDATION_FAILED, "企业微信尚未配置"); }
        CachedToken current = cachedToken;
        if (current != null && current.expiresAt() > System.currentTimeMillis() + TOKEN_MARGIN_MS) { return current.token(); }
        var value = settings.current();
        WeComToken response = read(client.get().uri(baseUrl + "/cgi-bin/gettoken?corpid={id}&corpsecret={secret}", value.corpId(), value.secret()), WeComToken.class);
        requireSuccess(response.errcode());
        cachedToken = new CachedToken(response, System.currentTimeMillis() + response.expires_in() * 1000L);
        return response;
    }
    /** 用一次性 code 查询成员身份。 */
    public WeComUserInfo getUserInfo(String accessToken, String code) {
        WeComUserInfo response = read(client.get().uri(baseUrl + "/cgi-bin/auth/getuserinfo?access_token={token}&code={code}", accessToken, code), WeComUserInfo.class);
        requireSuccess(response.errcode()); return response;
    }
    /** 获取部门列表。 */
    public DepartmentList listDepartments(String accessToken) {
        DepartmentList response = read(client.get().uri(baseUrl + "/cgi-bin/department/list?access_token={token}", accessToken), DepartmentList.class);
        requireSuccess(response.errcode()); return response;
    }
    /** 获取部门成员。 */
    public UserList listUsers(String accessToken, long departmentId) {
        UserList response = read(client.get().uri(baseUrl + "/cgi-bin/user/list?access_token={token}&department_id={id}", accessToken, departmentId), UserList.class);
        requireSuccess(response.errcode()); return response;
    }
    /** 增量事件回源成员详情。 */
    public User user(String userId) {
        User response = read(client.get().uri(baseUrl + "/cgi-bin/user/get?access_token={token}&userid={id}", getToken().access_token(), userId), User.class);
        if (response.userid() == null) { throw new BizException(ErrorCode.BUSINESS_ERROR, "企业微信成员详情不可用"); } return response;
    }
    /** 事务外发送不含凭证的站内通知摘要；仅服务端Outbox调用。 */
    public void sendApplicationNotice(String userId,String content) {
        var config=settings.current();
        if(userId==null || "@all".equalsIgnoreCase(userId) || !userId.matches("[A-Za-z0-9_.@-]{1,64}") || config.agentId().isBlank()) throw new BizException(ErrorCode.VALIDATION_FAILED,"企业微信收件身份或应用AgentId不可用");
        long agentId;
        try { agentId=Long.parseLong(config.agentId()); if(agentId<=0)throw new NumberFormatException(); }
        catch(NumberFormatException ex) { throw new BizException(ErrorCode.VALIDATION_FAILED,"企业微信AgentId须为有效正整数"); }
        var body=java.util.Map.of("touser",userId,"msgtype","text","agentid",agentId,"text",java.util.Map.of("content",content),"safe",1,"enable_duplicate_check",1,"duplicate_check_interval",1800);
        var response=read(client.post().uri(baseUrl+"/cgi-bin/message/send?access_token={token}",getToken().access_token()).body(body),MessageResult.class); requireSuccess(response.errcode());
        if(response.invaliduser()!=null && !response.invaliduser().isBlank()) throw new BizException(ErrorCode.BUSINESS_ERROR,"企业微信收件人不在应用可见范围");
    }
    /** 外部消息只解析成功标志和无效收件人，不保存远端原文。 */
    public record MessageResult(int errcode,String invaliduser) { }

    private <T> T read(RestClient.RequestHeadersSpec<?> request, Class<T> type) {
        try { T value = request.retrieve().body(type); if (value == null) { throw new BizException(ErrorCode.BUSINESS_ERROR, "企业微信返回空响应"); } return value; }
        catch (org.springframework.web.client.RestClientException ex) { throw new BizException(ErrorCode.BUSINESS_ERROR, "企业微信请求失败，请检查连接和授权范围"); }
    }
    private void requireSuccess(int code) { if (code != 0) { throw new BizException(ErrorCode.BUSINESS_ERROR, "企业微信返回错误码 " + code); } }
    private String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
    private record CachedToken(WeComToken token, long expiresAt) { }
    /** 企业令牌响应。 */
    public record WeComToken(int errcode, String errmsg, String access_token, int expires_in) { }
    /** OAuth 成员身份。 */
    public record WeComUserInfo(int errcode, String errmsg, String UserId, String user_ticket) { }
    /** 部门集合。 */
    public record DepartmentList(int errcode, String errmsg, List<Department> department) { }
    /** 外部部门。 */
    public record Department(long id, String name, long parentid, int order) { }
    /** 成员集合。 */
    public record UserList(int errcode, String errmsg, List<User> userlist) { }
    /** 外部成员；职位不自动作为系统职级。 */
    public record User(String userid, String name, List<Long> department, String position, String mobile, String email, Integer status) { }
}
