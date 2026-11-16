# 文件存储数据模型

## file_metadata

文件服务的唯一文件登记表。OSS 对象不是业务事实，数据库记录才是文件的归属、状态和权限事实。

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| `id` | `bigserial` | PK | 内部主键 |
| `idempotency_key` | `varchar(128)` | UNIQUE, NOT NULL | 上传申请幂等键 |
| `tenant_id` | `varchar(64)` | NOT NULL | 租户标识 |
| `owner_id` | `varchar(64)` | NOT NULL | 文件所有者 |
| `biz_type` | `varchar(64)` | NULL | 业务类型，如 `ORDER_ATTACHMENT` |
| `biz_id` | `varchar(128)` | NULL | 业务对象标识 |
| `bucket_name` | `varchar(128)` | NOT NULL | 实际 OSS Bucket |
| `object_key` | `varchar(512)` | UNIQUE, NOT NULL | OSS 对象 Key |
| `file_name` | `varchar(255)` | NOT NULL | 原始文件名，用于下载命名 |
| `content_type` | `varchar(128)` | NOT NULL | MIME 类型 |
| `file_size` | `bigint` | NOT NULL, > 0 | 文件字节数 |
| `etag` | `varchar(128)` | NULL | OSS ETag |
| `sha256` | `char(64)` | NULL | 文件内容摘要 |
| `visibility` | `varchar(32)` | NOT NULL | `PRIVATE/PUBLIC/TEMP/QUARANTINE` |
| `status` | `varchar(32)` | NOT NULL | `PENDING/CONFIRMED/DELETED` |
| `storage_class` | `varchar(32)` | NOT NULL | OSS 存储类型 |
| `created_at` | `timestamptz` | NOT NULL | 创建时间 |
| `confirmed_at` | `timestamptz` | NULL | 确认上传时间 |
| `deleted_at` | `timestamptz` | NULL | 删除时间 |
| `updated_at` | `timestamptz` | NOT NULL | 更新时间 |

建议索引：

```sql
unique (object_key)
unique (idempotency_key)
index (tenant_id, owner_id, status)
index (tenant_id, biz_type, biz_id)
index (status, created_at)
```

## 设计约束

- 客户端不能指定 `bucket_name` 和最终 `object_key`。
- 下载必须同时校验 `tenant_id`、`owner_id` 和 `status = 'CONFIRMED'`。
- `PENDING` 超过 TTL 后由清理任务删除。
- `DELETED` 记录保留，便于审计和幂等。
- 业务表只保存 `file_metadata.id` 或稳定的文件引用，不复制 OSS 凭据。
