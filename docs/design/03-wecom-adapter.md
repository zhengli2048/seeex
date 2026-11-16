# 企业微信适配器接口设计（官方 API 核对版）

依据：[企业微信官方 API 核对记录](../research/wecom-official-api.md)。实现以官方当前推荐版本为准；未确认的字段不得设为必填。

## 1. 适配器接口

```java
public interface WeComAdapter {
    AccessToken getAccessToken();
    String buildAuthorizeUrl(AuthorizeRequest request);
    WeComIdentity getUserInfo(String accessToken, String code);
    WeComUser getUser(String accessToken, String userId);
    List<WeComDepartment> listDepartments(String accessToken);
    List<WeComUserSummary> listDepartmentUsers(String accessToken, long departmentId);
    CallbackChallenge verifyCallback(CallbackVerifyRequest request);
    DecryptedEvent decryptEvent(EncryptedCallback callback);
}
```

适配器只处理 HTTP、官方协议、签名、解密和外部数据映射；员工匹配、冲突、权限和数据库写入由领域服务处理。

## 2. 获取 access_token

官方来源：[获取 access_token](https://developer.work.weixin.qq.com/document/path/91039)

```http
GET https://qyapi.weixin.qq.com/cgi-bin/gettoken?corpid=ID&corpsecret=SECRET
```

请求参数：`corpid`、`corpsecret`，均必填。成功响应：`errcode`、`errmsg`、`access_token`、`expires_in`（通常 7200 秒）。

要求：按应用缓存 Token，过期前刷新，不能返回前端，不能在每次请求前重新获取；日志不得输出 Token 或 Secret。

## 3. OAuth 网页授权

官方来源：[构造网页授权链接](https://developer.work.weixin.qq.com/document/path/91022)、[获取访问用户身份](https://developer.work.weixin.qq.com/document/path/91023)

授权 URL：

```text
https://open.weixin.qq.com/connect/oauth2/authorize
?appid=CORPID&redirect_uri=ENCODED_URI&response_type=code
&scope=snsapi_base|snsapi_privateinfo&state=STATE&agentid=AGENTID#wechat_redirect
```

回调参数：`code`、`state`。用 code 换身份：

```http
GET https://qyapi.weixin.qq.com/cgi-bin/auth/getuserinfo
```

参数：`access_token`、`code`。code 只能使用一次，约 5 分钟有效。企业成员成功返回 `errcode`、`errmsg`、`userid`，可能返回 `user_ticket`；非企业成员可能返回 `openid` 或 `external_userid`。

安全规则：state 服务端生成且一次性使用；redirect_uri 使用后端允许列表；code 只能后端消费；OAuth 成功后再调用成员详情补齐资料。

## 4. 成员详情

官方来源：[读取成员](https://developer.work.weixin.qq.com/document/path/90196)

```http
GET https://qyapi.weixin.qq.com/cgi-bin/user/get?access_token=ACCESS_TOKEN&userid=USERID
```

请求参数：`access_token`、`userid`。响应可能包含：`userid`、`name`、`department[]`、`order[]`、`position`、`mobile`、`gender`、`email`、`biz_mail`、`is_leader_in_dept[]`、`direct_leader[]`、`avatar`、`alias`、`status`、`main_department`、`extattr`、`open_userid`，以及 `errcode`、`errmsg`。

字段规则：`userid` 映射 `external_id`；`department` 映射部门关系；`main_department` 映射主部门；`is_leader_in_dept` 映射部门负责人；`position` 只保存原文；`mobile/email/avatar/extattr` 允许为空。官方提示敏感字段受应用权限或 OAuth scope 影响，不能设为数据库必填。

成员状态：`1` 激活、`2` 禁用、`4` 未激活、`5` 退出企业。

## 5. 部门成员

官方来源：[获取部门成员](https://developer.work.weixin.qq.com/document/path/90200)

```http
GET https://qyapi.weixin.qq.com/cgi-bin/user/simplelist?access_token=ACCESS_TOKEN&department_id=DEPARTMENT_ID
```

参数：`access_token`、`department_id`。接口结果受应用可见范围和通讯录权限影响；获取子部门人员时先读取部门树并递归调用。列表接口用于发现 UserID，完整资料必须回源调用成员详情。

## 6. 部门接口

官方来源：[创建部门](https://developer.work.weixin.qq.com/document/path/90205)、[更新部门](https://developer.work.weixin.qq.com/document/path/90206)、[删除部门](https://developer.work.weixin.qq.com/document/path/90207)、[获取部门列表](https://developer.work.weixin.qq.com/document/path/90208)

当前系统只读同步部门，不反向写企业微信。已核对的写接口保留在适配器扩展位：

```text
POST /cgi-bin/department/create?access_token=ACCESS_TOKEN
POST /cgi-bin/department/update?access_token=ACCESS_TOKEN
GET  /cgi-bin/department/delete?access_token=ACCESS_TOKEN&id=ID
```

创建至少需要 `name`、`parentid`；更新需要 `id`；删除不能删除根部门或仍有子部门/成员的部门。部门列表接口的分页和返回字段在实现前必须再次按官方当前页面确认。

## 7. 回调验签与解密

官方来源：[成员变更通知](https://developer.work.weixin.qq.com/document/path/90970)、[部门变更通知](https://developer.work.weixin.qq.com/document/path/90971)

URL 验证参数：`msg_signature`、`timestamp`、`nonce`、`echostr`。签名：

```text
sha1(sort(token, timestamp, nonce, msg_encrypt))
```

解密：`AESKey = Base64Decode(EncodingAESKey + "=")`，AES-256-CBC；明文结构为 16 字节随机串 + 4 字节网络序长度 + 消息 + receiveid。验证成功后解密 echostr 并原样返回。事件接口必须快速验签、解密、加密落库，再异步处理。

## 8. 通讯录事件

成员事件通用字段：`ToUserName`、`FromUserName`、`CreateTime`、`MsgType=event`、`Event=change_contact`、`ChangeType`。

事件类型：

| 事件 | 核心字段 | 处理 |
| --- | --- | --- |
| `create_user` | `UserID`、`Name`、`Department`、`MainDepartment` | 用 UserID 回源完整详情 |
| `update_user` | `UserID`、`NewUserID`、`Department`、`MainDepartment`、`IsLeaderInDept`、`Status` | 用 UserID 回源完整详情 |
| `delete_user` | `UserID` | 停用外部身份和系统账号 |
| `create_party` | `Id`、`Name`、`ParentId`、`Order` | 用部门 ID 回源 |
| `update_party` | `Id`、可能的 `Name`/`ParentId` | 用部门 ID 回源 |
| `delete_party` | `Id` | 标记部门失效，不物理删除 |

官方说明部分事件只回调 ID，因此事件是同步触发器，不是完整数据源。

## 9. 职级与自定义字段

官方自建应用通讯录资料确认有 `position` 和 `extattr`，本次未确认所有自建应用均可使用统一标准职级 ID/职级列表接口。系统必须保存 position 原文和 extattr 快照，由管理员配置 extattr 到本地 `job_grade` 的映射；不能把 position 直接作为稳定职级 ID。若接入人事/职级专用产品，必须另行核对其官方接口和权限。

## 10. 错误与重试

适配器应保留官方 `errcode`、`errmsg`：网络超时和临时错误可重试；Token 失效刷新后重试一次；参数错误、权限不足、UserID 不存在进入失败记录，不盲目重试。Secret、Token、AESKey 和完整敏感员工字段不得写普通日志。

## 11. 实现前检查清单

1. 再次核对部门列表接口当前版本的分页和返回字段；
2. 确认应用可见范围覆盖公司组织；
3. 确认手机号、邮箱、头像和 extattr 所需权限；
4. 确认企业是否启用自定义职级字段；
5. 确认回调 Token、AESKey 和事件类型；
6. 检查官方是否已标记旧接口废弃或提供替代接口。
