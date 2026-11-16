# 文件服务设计

文件服务独立部署，业务服务不直接依赖阿里云 OSS SDK，也不持有 OSS AccessKey。

## 调用边界

- 前端通过业务服务的用户 JWT 访问业务接口。
- 业务服务通过 `X-Service-Token` 调用文件服务。
- 业务服务同时传递可信的 `X-User-Id`、`X-Tenant-Id`。
- 文件服务校验文件归属后生成短期 OSS 签名 URL。
- OSS Bucket 默认私有，前端只使用短期 URL 上传或下载。

## 接口

```text
POST /api/files/upload-url
POST /api/files/confirm
GET  /api/files/download-url
DELETE /api/files
```

所有文件接口要求：

```http
X-Service-Token: <service-token>
X-User-Id: <user-id>
X-Tenant-Id: <tenant-id>
Idempotency-Key: <unique-key>  # 仅申请上传地址时要求
```

## 文件生命周期

```text
PENDING -> CONFIRMED -> DELETED
```

申请上传地址时写入 `PENDING`；OSS 直传成功后调用 confirm；删除操作只做逻辑删除并异步清理 OSS 对象。

## 存储规则

Bucket 由文件服务根据 `visibility` 决定，客户端不能传入 Bucket。租户和业务信息放在对象 Key 和元数据表中：

```text
tenants/{tenantId}/{bizType}/{ownerId}/{yyyy}/{MM}/{uuid}/{normalizedFileName}
```

建议 Bucket：

- `private`：私有业务文件
- `public`：明确公开的资源
- `temp`：短期临时文件
- `quarantine`：待审核文件

## 错误响应

```json
{
  "code": "RESOURCE_NOT_FOUND",
  "error_type": "NOT_FOUND",
  "message": "文件不存在或无权访问"
}
```
