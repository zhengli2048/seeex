# 企业微信 Java 适配器与 DTO 设计

本设计承接 [03-wecom-adapter.md](./03-wecom-adapter.md) 和官方核对记录，定义外部协议 DTO、适配器接口和领域转换边界。DTO 不作为数据库实体，企业微信字段变化只能影响 Adapter 层。

## 1. 包结构

```text
com.seeex.tech.integration.wecom
├── WeComAdapter.java
├── WeComTokenClient.java
├── WeComAuthClient.java
├── WeComContactClient.java
├── WeComCallbackCodec.java
├── dto/
│   ├── WeComApiResponse.java
│   ├── WeComTokenResponse.java
│   ├── WeComUserInfoResponse.java
│   ├── WeComUserDetailResponse.java
│   ├── WeComDepartmentResponse.java
│   └── WeComEventMessage.java
├── model/
│   ├── ExternalIdentity.java
│   ├── ExternalEmployeeSnapshot.java
│   └── ExternalDepartmentSnapshot.java
└── WeComException.java
```

## 2. 通用响应 DTO

```java
public record WeComApiResponse<T>(
        int errcode,
        String errmsg,
        T data) {
    public boolean isSuccess() { return errcode == 0; }
}
```

适配器必须保留 `errcode` 和 `errmsg`；不要将所有企业微信错误都转换成 HTTP 500。错误分类由 `WeComErrorClassifier` 完成：Token 失效、临时错误、权限错误和参数错误分别处理。

## 3. Token DTO

```java
public record WeComTokenResponse(
        int errcode,
        String errmsg,
        String access_token,
        Integer expires_in) {}
```

`access_token` 只允许存在后端内存/缓存对象中，不写日志、不序列化到前端。

内部模型：

```java
public record AccessToken(
        String value,
        Instant expiresAt) {}
```

Token Adapter 接口：

```java
public interface WeComTokenClient {
    AccessToken getOrRefresh(WeComCredential credential);
}
```

## 4. OAuth 身份 DTO

官方 `auth/getuserinfo` 响应字段并非全部必返：

```java
public record WeComUserInfoResponse(
        int errcode,
        String errmsg,
        String userid,
        String user_ticket,
        String openid,
        String external_userid) {}
```

`code` 只在请求参数中出现，不写入数据库普通字段；如需审计只保存哈希和结果，不保存明文授权 code。

接口：

```java
public interface WeComAuthClient {
    WeComUserInfoResponse getUserInfo(AccessToken token, String code);
}
```

## 5. 成员详情 DTO

由于企业微信敏感字段受权限影响，所有非 `userid` 字段使用可空类型：

```java
public record WeComUserDetailResponse(
        int errcode,
        String errmsg,
        String userid,
        String name,
        List<Long> department,
        List<Integer> order,
        String position,
        String mobile,
        String gender,
        String email,
        String biz_mail,
        List<Integer> is_leader_in_dept,
        List<String> direct_leader,
        String avatar,
        String alias,
        Integer status,
        Long main_department,
        Map<String, Object> extattr,
        String open_userid) {}
```

约束：

- `userid` 是企业微信员工身份匹配键；
- `department`、`is_leader_in_dept` 通过数组位置对应；
- `main_department` 可能为空；
- `mobile`、`email`、`avatar`、`extattr` 可能为空；
- `position` 只保存为原始职位文本；
- `status` 需要映射为外部状态，不直接覆盖系统员工状态，除非由同步状态机决定。

内部模型：

```java
public record ExternalEmployeeSnapshot(
        String externalId,
        String name,
        List<ExternalDepartmentMembership> departments,
        String position,
        String mobile,
        String email,
        String avatar,
        Integer externalStatus,
        Long mainDepartmentId,
        Map<String, Object> extensionAttributes) {}
```

## 6. 部门和成员列表 DTO

成员发现接口的结果只用于找到 UserID：

```java
public record WeComUserSummary(
        String userid,
        String name,
        List<Long> department,
        Integer openUserid) {}
```

部门列表 DTO 必须以官方当前页面最终字段为准；在字段未核对前，不将分页字段写进公共领域接口。适配器应提供：

```java
public interface WeComContactClient {
    List<ExternalDepartmentSnapshot> listDepartments(AccessToken token);

    List<WeComUserSummary> listDepartmentUsers(AccessToken token, long departmentId);

    WeComUserDetailResponse getUser(AccessToken token, String userId);
}
```

列表接口受应用可见范围影响，不能把空列表直接解释为公司没有成员。

## 7. 回调事件 DTO

解密后的事件先使用协议 DTO 保存：

```java
public record WeComEventMessage(
        String toUserName,
        String fromUserName,
        Long createTime,
        String msgType,
        String event,
        String changeType,
        String userId,
        String newUserId,
        String name,
        List<Long> department,
        Long mainDepartment,
        List<Integer> isLeaderInDept,
        Integer status,
        Long partyId,
        String rawXmlHash) {}
```

字段名称统一转换为 Java camelCase；原始 XML 加密保存到 `sync_event`，不把所有扩展 XML 字段强行塞进 DTO。

Codec 接口：

```java
public interface WeComCallbackCodec {
    String verifyUrl(CallbackVerifyRequest request);

    WeComEventMessage decrypt(String msgSignature, String timestamp, String nonce, String encryptedBody);
}
```

## 8. 领域转换边界

```java
public interface WeComSnapshotMapper {
    ExternalEmployeeSnapshot toEmployeeSnapshot(WeComUserDetailResponse response);

    ExternalDepartmentSnapshot toDepartmentSnapshot(WeComDepartmentResponse response);

    ExternalEvent toExternalEvent(WeComEventMessage message);
}
```

Mapper 只做字段和状态转换，不做：

- 员工查找；
- 外部身份合并；
- 角色计算；
- 数据库写入；
- 离职判断；
- 权限授权。

## 9. 凭证配置

```java
public record WeComCredential(
        String corpId,
        String agentId,
        SecretReference secret,
        SecretReference callbackToken,
        SecretReference encodingAesKey) {}
```

`SecretReference` 只保存密钥管理系统的引用。适配器通过 `SecretResolver` 获取明文密钥，使用完成后不得写日志或返回 DTO。

## 10. 错误接口

```java
public interface WeComErrorClassifier {
    WeComErrorKind classify(int errcode, String errmsg);
}

public enum WeComErrorKind {
    TOKEN_EXPIRED,
    RETRYABLE_UPSTREAM,
    PERMISSION_DENIED,
    INVALID_ARGUMENT,
    NOT_FOUND,
    RATE_LIMITED,
    UNKNOWN
}
```

同步层根据分类决定刷新 Token、退避重试、进入冲突或直接失败。

## 11. 测试适配器接口

领域服务只依赖 `WeComAdapter` 或更窄的客户端接口，测试使用内存 Adapter：

```java
public final class InMemoryWeComAdapter implements WeComAdapter {
    // 通过固定快照模拟创建、更新、删除、权限缺失和重复事件
}
```

最少测试场景：

1. 成员详情缺少手机号、邮箱和头像仍可映射；
2. 事件只含 UserID 时同步层回源详情；
3. Token 过期只刷新并重试一次；
4. 权限不足不把空列表当成全量空组织；
5. `position` 不直接生成稳定职级 ID；
6. 重复事件不会重复调用领域 upsert。

## 12. 实施顺序

1. 定义 DTO 和 `WeComErrorClassifier`；
2. 实现 Token 缓存 Adapter；
3. 实现 OAuth 身份和成员详情客户端；
4. 按官方当前字段补齐部门列表客户端；
5. 实现回调验签和 AES Codec；
6. 接入同步状态机；
7. 最后接入员工、部门、职级和 RBAC 领域服务。
