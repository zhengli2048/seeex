# 企业微信官方 API 核对记录

更新时间：2026-11-13。以下结论来自企业微信官方开发者中心，未确认的能力明确标注。

## access_token

来源：[获取 access_token](https://developer.work.weixin.qq.com/document/path/91039)

```text
GET https://qyapi.weixin.qq.com/cgi-bin/gettoken?corpid=ID&corpsecret=SECRET
```

参数：`corpid`（必填，企业 ID）、`corpsecret`（必填，应用密钥）。成功返回 `errcode`、`errmsg`、`access_token`（最长 512 字节）、`expires_in`（正常 7200 秒）。官方要求后端缓存并按应用区分 token，不能频繁调用，不能返回前端。

## OAuth 网页授权

来源：[构造网页授权链接](https://developer.work.weixin.qq.com/document/path/91022)、[获取访问用户身份](https://developer.work.weixin.qq.com/document/path/91023)

授权 URL：

```text
https://open.weixin.qq.com/connect/oauth2/authorize?appid=CORPID&redirect_uri=REDIRECT_URI&response_type=code&scope=snsapi_base&state=STATE&agentid=AGENTID#wechat_redirect
```

参数：`appid` 必填 CorpID；`redirect_uri` 必填且 URL Encode；`response_type` 固定 `code`；`scope` 为 `snsapi_base` 或 `snsapi_privateinfo`；`state` 可选且不超过 128 字节；`agentid` 建议填写，`snsapi_privateinfo` 时必填。回调为 `redirect_uri?code=CODE&state=STATE`。

获取身份：

```text
GET https://qyapi.weixin.qq.com/cgi-bin/auth/getuserinfo?access_token=ACCESS_TOKEN&code=CODE
```

参数：`access_token`、`code` 均必填。`code` 只能使用一次，5 分钟过期。企业成员成功返回 `errcode`、`errmsg`、`userid`，可能返回 `user_ticket`；非企业成员可能返回 `openid`、`external_userid`。可信域名必须与应用匹配。

## 读取成员

来源：[读取成员](https://developer.work.weixin.qq.com/document/path/90196)

```text
GET https://qyapi.weixin.qq.com/cgi-bin/user/get?access_token=ACCESS_TOKEN&userid=USERID
```

请求参数：`access_token`、`userid`，其中 UserID 在企业内唯一，1 至 64 字节。

官方示例返回：`errcode`、`errmsg`、`userid`、`name`、`department[]`、`order[]`、`position`、`mobile`、`gender`、`email`、`biz_mail`、`is_leader_in_dept[]`、`direct_leader[]`、`avatar`、`alias`、`status`、`main_department`、`extattr`、`open_userid` 等。

成员状态：`1` 激活、`2` 禁用、`4` 未激活、`5` 退出企业。官方明确提示新建自建应用可能不能直接获得头像、手机、邮箱等敏感字段，需要 OAuth 手动授权或相应通讯录权限；系统数据库不能把这些字段设为必填。

## 获取部门成员

来源：[获取部门成员](https://developer.work.weixin.qq.com/document/path/90200)

```text
GET https://qyapi.weixin.qq.com/cgi-bin/user/simplelist?access_token=ACCESS_TOKEN&department_id=DEPARTMENT_ID
```

参数：`access_token`、`department_id` 均必填。获取部门及子部门成员时，官方要求先获取子部门并逐层递归；接口结果受应用可见范围和通讯录权限影响，不能假定可全量读取。

## 部门接口

来源：[创建部门](https://developer.work.weixin.qq.com/document/path/90205)、[更新部门](https://developer.work.weixin.qq.com/document/path/90206)、[删除部门](https://developer.work.weixin.qq.com/document/path/90207)、[获取部门列表](https://developer.work.weixin.qq.com/document/path/90208)

创建：`POST /cgi-bin/department/create?access_token=ACCESS_TOKEN`，JSON 参数 `name`、`parentid` 必填，`name_en`、`order`、`id` 可选；成功返回 `errcode`、`errmsg`、`id`。

更新：`POST /cgi-bin/department/update?access_token=ACCESS_TOKEN`，`id` 必填，可更新 `name`、`name_en`、`parentid`、`order`；成功返回 `errcode`、`errmsg`。

删除：`GET /cgi-bin/department/delete?access_token=ACCESS_TOKEN&id=ID`，不能删除根部门或仍有子部门/成员的部门；成功返回 `errcode`、`errmsg`。

获取部门列表页面已确认官方 URL，但本次提取未完整读取其参数和返回示例；实现前必须再次打开该官方页面核对具体查询参数和返回字段。

## 回调验签、解密和 URL 验证

来源：[成员变更通知](https://developer.work.weixin.qq.com/document/path/90970)、[部门变更通知](https://developer.work.weixin.qq.com/document/path/90971)及官方回调配置说明。

URL 验证请求带 `msg_signature`、`timestamp`、`nonce`、`echostr`；校验成功后解密 `echostr` 并原样返回。

事件签名使用：

```text
sha1(sort(token, timestamp, nonce, msg_encrypt))
```

解密：`AESKey = Base64Decode(EncodingAESKey + "=")`，使用 AES-256-CBC；明文结构为 16 字节随机串 + 4 字节网络字节序长度 + 消息 + receiveid。后端应使用官方/官方推荐语言库。

## 成员事件

来源：[成员变更通知](https://developer.work.weixin.qq.com/document/path/90970)

通用字段：`ToUserName`、`FromUserName`、`CreateTime`、`MsgType=event`、`Event=change_contact`、`ChangeType`。

`create_user`：新增成员；常见字段包括 `UserID`、`Name`、`Department`、`MainDepartment`。

`update_user`：更新成员；可能包含 `UserID`、`NewUserID`、`Department`、`MainDepartment`、`IsLeaderInDept`、`Status`。官方说明部分回调配置只回调 `UserID/Department/NewUserID`，因此事件处理必须按 UserID 回源读取完整资料。

`delete_user`：删除成员，核心字段 `UserID`。

## 部门事件

来源：[部门变更通知](https://developer.work.weixin.qq.com/document/path/90971)

`create_party`：`Id`、`Name`、`ParentId`、`Order`；`update_party`：`Id`，变更时可能有 `Name`、`ParentId`；`delete_party`：`Id`。官方说明部分配置只回调 `Id/ParentId`，部门事件也必须回源补齐。

## 职级和自定义字段结论

本次核对的企业微信自建应用通讯录官方页面确认有 `position` 职务文本和 `extattr` 扩展属性；没有确认到对所有自建应用统一可用的标准职级 ID/职级列表接口。

因此本系统必须保存 `position` 原文，支持解析管理员在企业微信后台配置的 `extattr`，再映射到本地 `job_grade`。在未确认具体产品权限和官方接口前，不能把 `position` 当稳定职级 ID；如使用独立职级/人事接口，必须另行核对该产品官方文档。

## 对当前设计的修正

- `external_id` 保存企业微信 `userid`；
- `external_name/mobile/email/position/avatar/status` 全部允许为空；
- 部门关系来自 `department[]` 和 `main_department`；
- 事件只作为触发器，完整员工/部门资料通过回源接口获取；
- OAuth code 只能后端消费；
- 职级来源必须是可配置字段映射，不能假定统一接口；
- 回调事件原文保存加密内容，处理失败支持重试和死信。
