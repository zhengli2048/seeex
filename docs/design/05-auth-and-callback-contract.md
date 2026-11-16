# Seeex 认证与企业微信回调接口契约

## 通用约定

- Base path：`/api`；JSON UTF-8；时间使用 ISO-8601 UTC。
- 业务响应使用 `ApiResponse`，错误必须包含 `code`、`error_type`、`message`。
- 企业微信 Secret、Token、AESKey、Access Token 不返回前端。

## 企业微信 OAuth

官方接口：[构造网页授权链接](https://developer.work.weixin.qq.com/document/path/91022)、[获取访问用户身份](https://developer.work.weixin.qq.com/document/path/91023)。

授权入口：

```text
GET /api/auth/wecom/authorize
```

响应数据：`authorize_url`、`state_expires_at`。后端生成一次性 state，并限制 redirect_uri 为白名单。

企业微信回调：

```text
GET /api/auth/wecom/callback?code=CODE&state=STATE
```

后端使用官方接口：

```text
GET https://qyapi.weixin.qq.com/cgi-bin/auth/getuserinfo
Query: access_token, code
```

`code` 只能使用一次，约 5 分钟有效。企业成员成功返回 `userid`，可能返回 `user_ticket`；非企业成员可能返回 `openid` 或 `external_userid`。当前系统只接受能映射到企业成员 `userid` 的身份。

回调处理顺序：校验 state → 调用 `auth/getuserinfo` → 用 userid 匹配外部身份 → 调用 `user/get` 补全资料 → 创建或加载员工账号 → 创建本系统会话。

错误：

```text
AUTH_CODE_INVALID
EXTERNAL_IDENTITY_CONFLICT
EXTERNAL_IDENTITY_UNSUPPORTED
ACCOUNT_DISABLED
ACCOUNT_LOCKED
```

员工无角色时仍可建立会话，访问业务 API 时返回授权错误。

## Token 与本地登录

本地登录：

```text
POST /api/auth/login
Body: { phone, password, captcha_token? }
```

成功返回：`access_token`、`token_type=Bearer`、`expires_in`、`refresh_token`、`refresh_expires_in`、员工摘要和 `has_permission`。

刷新：

```text
POST /api/auth/refresh
Body: { refresh_token }
```

Refresh Token 必须轮换，旧 Token 立即失效。注销：

```text
POST /api/auth/logout
Authorization: Bearer ACCESS_TOKEN
```

数据库只保存 Access/Refresh Token 哈希。

## 当前会话和权限

```text
GET /api/me
GET /api/me/roles
GET /api/me/resources
GET /api/me/menus
GET /api/me/data-scopes?resource_code=users
```

无权限员工可以返回空角色/资源/菜单集合；受保护 API 必须返回 `AUTHORIZATION_DENIED`。

## 企业微信回调

URL 验证：

```text
GET /api/integrations/wecom/events
Query: msg_signature, timestamp, nonce, echostr
```

服务端验签、解密 `echostr`，成功返回纯文本，失败 HTTP 403。

事件接收：

```text
POST /api/integrations/wecom/events
Content-Type: application/xml
```

处理顺序：验签 → AES 解密 → 幂等识别 → 加密保存原文 → 投递异步任务 → 3 秒内返回企业微信成功确认。

签名算法：`sha1(sort(token, timestamp, nonce, msg_encrypt))`。解密使用 `Base64Decode(EncodingAESKey + "=")` 和 AES-256-CBC。成员/部门事件只作为触发器，异步处理必须回源读取完整数据。

## 同步接口

```text
POST /api/integrations/wecom/sync/full
Header: Idempotency-Key
Response: HTTP 202, { run_id, run_no, status: PENDING }

GET /api/integrations/wecom/sync/runs/{runId}
POST /api/integrations/wecom/events/{eventId}/retry
```

只能重试失败或死信事件；重试操作必须记录员工和审计。

## 错误映射

| HTTP | code | error_type |
| --- | --- | --- |
| 400 | `INVALID_REQUEST` | `VALIDATION` |
| 401 | `AUTHENTICATION_FAILED` | `AUTHENTICATION` |
| 403 | `AUTHORIZATION_DENIED` | `AUTHORIZATION` |
| 404 | `USER_NOT_FOUND` | `NOT_FOUND` |
| 409 | `EXTERNAL_IDENTITY_CONFLICT` | `CONFLICT` |
| 409 | `SYNC_ALREADY_RUNNING` | `CONFLICT` |
| 429 | `RATE_LIMITED` | `RATE_LIMITED` |
| 502 | `WECOM_UPSTREAM_ERROR` | `BUSINESS` |
| 500 | `INTERNAL_ERROR` | `INTERNAL` |

## 安全验收

1. OAuth code 重复使用返回 `AUTH_CODE_INVALID`。
2. state 不匹配或过期时拒绝登录。
3. 多条外部身份匹配进入冲突，不自动合并。
4. 无角色员工可登录但不能访问受保护 API。
5. Refresh Token 轮换后旧 Token 失效。
6. 回调签名错误返回 403，正确签名返回企业微信要求的纯文本确认。
7. 回调接口不等待完整同步，目标 3 秒内返回。
8. Secret、Token、AESKey 不出现在前端构建产物和响应中。
