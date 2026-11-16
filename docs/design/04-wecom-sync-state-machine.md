# 企业微信同步状态机与重试设计

## 1. 设计原则

- 企业微信事件是触发器，不是完整数据源；事件中的 UserID/部门 ID 必须回源读取详情。
- 首次接入使用全量同步；日常使用事件增量；定时任务执行全量校准。
- 全量和增量共用同一套幂等 upsert 领域服务。
- 重复事件、乱序事件和回调重试不得重复创建员工或角色。
- 同步失败默认保持已有有效数据，不删除、不降权、不自动合并冲突身份。

## 2. 同步批次状态

```text
PENDING
  ↓
RUNNING
  ├── SUCCESS
  ├── PARTIAL
  └── FAILED
```

### 状态含义

| 状态 | 含义 | 可转移 |
| --- | --- | --- |
| `PENDING` | 已创建但未开始 | `RUNNING`、`FAILED` |
| `RUNNING` | 正在读取和写入 | `SUCCESS`、`PARTIAL`、`FAILED` |
| `SUCCESS` | 全部记录成功 | 终态 |
| `PARTIAL` | 部分记录失败，但批次完成 | 可发起 `REPAIR` |
| `FAILED` | 批次级失败 | 可重试或新建批次 |

## 3. 事件状态

```text
RECEIVED
  ↓
PROCESSING
  ├── SUCCESS
  ├── RETRY_WAIT → PROCESSING
  └── DEAD_LETTER
```

事件状态字段位于 `sync_event`：

```text
status
retry_count
next_retry_at
processed_at
error_message
```

## 4. 全量同步流程

```text
创建 sync_run(FULL)
→ 获取 access_token
→ 获取部门树
→ 按部门递归发现成员 UserID
→ 调用成员详情接口补全资料
→ upsert 部门
→ upsert 员工和外部身份
→ upsert 员工部门关系
→ 解析职位/extattr 并映射职级
→ 计算自动角色
→ 标记本次未出现的外部组织记录
→ 完成批次
```

全量同步中的“未出现”只能在确认应用可见范围覆盖完整组织后处理。若企业微信接口权限不足或读取不完整，不得把未返回人员直接标记离职；批次应进入 `PARTIAL` 或 `FAILED`。

## 5. 事件增量流程

```text
后端回调
→ 验签
→ AES 解密
→ 生成幂等键
→ 加密保存 sync_event
→ 快速返回企业微信
→ 异步处理 ChangeType
→ 用 UserID/DepartmentID 回源详情
→ 调用统一 upsert 服务
→ 成功或重试/死信
```

### 事件处理映射

| ChangeType | 回源动作 | 本地动作 |
| --- | --- | --- |
| `create_user` | `user/get(userid)` | 创建/更新员工、身份、部门关系 |
| `update_user` | `user/get(userid)` | 更新外部快照和组织关系 |
| `delete_user` | 无需详情 | 员工置 `LEFT`，账号禁用，身份停用 |
| `create_party` | 部门列表或部门详情 | 创建/更新部门 |
| `update_party` | 部门列表或部门详情 | 更新部门和层级 |
| `delete_party` | 无需详情 | 部门逻辑停用 |

事件中只提供 ID 时，不能直接把缺失字段写成空值覆盖本地外部快照。

## 6. 幂等规则

### 6.1 事件幂等键

优先使用：

```text
provider + event_type + event_id
```

企业微信事件没有稳定 event_id 时使用：

```text
provider + event_type + payload_hash + 时间窗口

落库时必须生成最终 `idempotency_key` 并建立唯一约束；没有 event_id 的事件不能只依赖应用层查询去重。
```

处理前检查已成功事件：

```sql
select id
from sync_event
where provider = :provider
  and event_type = :event_type
  and event_id = :event_id
  and status = 'SUCCESS';
```

重复事件直接记录为已忽略或复用原处理结果，不重复执行领域写入。

### 6.2 外部身份幂等

业务处理以：

```text
provider + external_id
```

匹配员工外部身份：

```text
0 条：创建员工和身份
1 条：更新该员工
多条：写入 external_identity_conflict，不自动合并
```

姓名不参与自动合并。

## 7. 乱序处理

事件可能乱序到达，例如先收到更新再收到创建。处理规则：

- `update_user` 找不到身份时，回源详情并按创建流程 upsert；
- `delete_user` 找不到身份时，记录不可匹配事件，不创建员工；
- 部门更新找不到部门时，先回源部门树或标记待补偿；
- 外部快照带 `last_synced_at`，较旧数据不得覆盖较新数据；
- 角色撤销必须基于 `assignment_source/source_key/sync_run_id`，不能按角色名称删除。

## 8. 重试分类

### 可重试

- 网络连接失败；
- DNS、连接超时、读取超时；
- 企业微信临时系统错误；
- access_token 失效（刷新后最多重试一次）；
- 数据库暂时不可用；
- 消息队列或任务调度暂时不可用。

### 不应盲目重试

- 参数错误；
- 应用无权限；
- UserID/部门 ID 不存在；
- 企业微信返回业务冲突；
- 解密失败且密钥配置未改变；
- 外部身份出现多条候选。

上述情况进入失败记录或冲突处理，不通过无限重试掩盖配置问题。

## 9. 退避策略

默认最多重试 5 次，指数退避并加入随机抖动：

```text
delay = min(15 分钟, 30 秒 × 2^retry_count) + jitter(0~10 秒)
```

示例：

```text
第 1 次：约 30 秒
第 2 次：约 60 秒
第 3 次：约 120 秒
第 4 次：约 240 秒
第 5 次：约 480 秒
```

超过最大次数进入 `DEAD_LETTER`，保留最后错误、官方 `errcode/errmsg`、请求对象和关联批次。

## 10. 并发控制

- 同一企业微信事件只能有一个处理者进入 `PROCESSING`；
- 使用数据库行锁或任务队列 claim 机制；
- claim 必须带超时，处理器崩溃后可重新进入 `RETRY_WAIT`；
- 同一外部 UserID 的同步任务应串行化或使用短事务锁；
- 全量同步与事件增量同时更新同一员工时，以外部数据时间和同步序列决定覆盖，不以任务完成时间决定。

## 11. 自动角色同步

同步角色必须写入：

```text
assignment_source = JOB_GRADE / DEPARTMENT / WE_COM
source_key
sync_run_id
```

下一次同步只撤销当前来源已经失效的自动绑定：

```text
旧的自动绑定 + 本次来源不再产生
→ revoked_at = current_timestamp
```

不得删除 `assignment_source = MANUAL` 的授权，也不得把自动授权转换为手动授权。

## 12. 全量校准删除规则

只有满足以下全部条件时，才允许将外部记录标记为离职/停用：

1. 本次 `sync_run` 读取成功；
2. 应用可见范围已确认覆盖完整组织；
3. 部门递归读取没有权限或分页错误；
4. 目标外部身份在完整结果集中确实不存在；
5. 没有未解决的同步冲突。

否则只记录 `PARTIAL`，不改变员工状态和登录权限。

## 13. 监控指标

至少记录：

```text
sync_run_total
sync_run_failed
sync_run_partial
sync_event_received
sync_event_success
sync_event_retry
sync_event_dead_letter
external_identity_conflict_open
wecom_api_error_by_errcode
wecom_api_latency
```

告警条件：连续批次失败、死信增长、外部身份冲突增长、Token 刷新失败、回调验签失败突增和回源详情超时。

## 14. 验收场景

1. 同一个成员创建事件投递 3 次，只创建一个员工和一条有效外部身份。
2. 更新事件只带 UserID 时，系统回源成员详情，不清空已有手机号和邮箱。
3. 全量同步权限不足时，批次为 `PARTIAL`，不批量停用未返回员工。
4. 网络错误按退避策略重试，超过次数进入死信。
5. Token 失效时刷新一次并重放原请求，仍失败则进入失败记录。
6. 同步职级变化只撤销旧职级自动角色，不影响人工角色。
7. 事件处理器崩溃后，锁超时事件可以重新处理。
