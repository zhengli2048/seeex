# Plan 3：企业微信同步 / 事件状态机 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 实现完整的企业微信同步与事件状态机：幂等 upsert、批次状态机、事件状态机（退避/死信）、原文落库、乱序容忍、重试与监控。

**Architecture:** 在现有 `WeComSyncService`/`WeComController`/`WeComCallbackCodec` 基础上补齐状态机与异步处理。同步与事件共用同一幂等 upsert 领域服务；事件原文明文落库（不加密），但普通前端不可见、仅审计权限可查看。

**Tech Stack:** Java 21、Spring Boot 4、MyBatis-Plus、JUnit 5 + Mockito。

**Spec:** `docs/superpowers/specs/2026-11-16-seeex-console-repair-design.md`

## Global Constraints

- 依赖 Plan 1 的 `sync_run`/`sync_event` 扩展列与 Plan 2 的软删除、授权来源追踪。
- 企业微信不做真实联调；代码/接口/单测完整即可。
- 事件原文明文落库（不加密），但普通前端不可见、仅审计权限可查看；日志禁止输出明文/Secret；业务编号格式不变（sync_run 用 UUID 既有约定保留）。
- 提交前 `mvn verify`。

---

## File Structure

**Modify:**
- `.../wecom/WeComSyncService.java`
- `.../wecom/WeComController.java`

**Create:**
- `.../wecom/SyncEventProcessor.java`（异步事件处理器）
- `.../wecom/RetryScheduler.java`（退避调度）
- `.../wecom/SyncMetrics.java`（Micrometer 指标）

---

### Task 1: 同步批次状态机

**Files:**
- Modify: `.../wecom/WeComSyncService.java`
- Test: `.../wecom/WeComSyncServiceTest.java`

**Interfaces:**
- Consumes: `sync_run` 扩展列（Plan 1 Task 4）。
- Produces: `startFullSync()` 状态流转 `PENDING → RUNNING → SUCCESS/PARTIAL/FAILED`；单条失败不中断，记 `PARTIAL`。

- [ ] **Step 1: 写失败测试（单条失败记 PARTIAL）**

```java
@Test
void 单条失败批次为PARTIAL() {
    when(client.listDepartments(any())).thenReturn(new DepartmentList(0, "ok", List.of(dep(1L, "研发", 0))));
    doThrow(new RuntimeException("boom")).when(client).listUsers(any(), eq(1L));
    var status = service.startFullSync();
    assertThat(status.status()).isEqualTo("PARTIAL");
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `mvn -pl web -am test -Dtest=WeComSyncServiceTest -q`
Expected: FAIL（当前 catch 后记 FAILED）。

- [ ] **Step 3: 实现**

`startFullSync`：`run.setStatus("PENDING")` 插入 → 处理前 `RUNNING` → 逐部门 try/catch，单条失败累计错误但不中断 → 结束按「全部成功 SUCCESS / 有失败 PARTIAL / 批次级异常 FAILED」写状态与 `error_message`。

- [ ] **Step 4: 运行测试通过**

Run: 同 Step 2。
Expected: PASS。

- [ ] **Step 5: Commit**

```bash
git add seeex-tech-backend/web/src/main/java/com/seeex/tech/web/wecom/WeComSyncService.java
git commit -m "feat: sync run state machine with PARTIAL on partial failure"
```

---

### Task 2: 事件接收 + 幂等 + 原文落库

**Files:**
- Modify: `.../wecom/WeComController.java`
- Test: `.../wecom/WeComControllerTest.java`

**Interfaces:**
- Produces: `event()` 接收时计算幂等键（provider+event_type+event_id 或 payload_hash），原文存 `payload`，重复事件幂等返回。

- [ ] **Step 1: 写失败测试（重复事件幂等返回）**

```java
@Test
void 重复事件幂等返回() {
    SyncEventDO existing = new SyncEventDO();
    existing.setEventNo("abc"); existing.setStatus("RECEIVED");
    when(eventMapper.selectOne(any())).thenReturn(existing);
    var resp = controller.event(new EventRequest("create_user", "evt-1", "{}"));
    assertThat(resp.data().status()).isEqualTo("RECEIVED");
    verify(eventMapper, never()).insert(any());
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `mvn -pl web -am test -Dtest=WeComControllerTest -q`
Expected: FAIL。

- [ ] **Step 3: 实现**

`WeComController.event`：幂等键 = `provider + event_type + event_id`（无 event_id 用 payload_hash）；已有成功事件直接返回；否则 `payload` 存原文，`status=RECEIVED` 落库，投递异步处理。

- [ ] **Step 4: 运行测试通过**

Run: 同 Step 2。
Expected: PASS。

- [ ] **Step 5: Commit**

```bash
git add seeex-tech-backend/web/src/main/java/com/seeex/tech/web/wecom/WeComController.java
git commit -m "feat: store event payload with idempotency key"
```

---

### Task 3: 事件异步处理 + 状态机 + 指数退避

**Files:**
- Create: `.../wecom/SyncEventProcessor.java`
- Create: `.../wecom/RetryScheduler.java`
- Test: `.../wecom/SyncEventProcessorTest.java`

**Interfaces:**
- Consumes: `sync_event` 状态机字段（Plan 1 Task 4）。
- Produces: `SyncEventProcessor.process(long eventId)`：`RECEIVED → PROCESSING → SUCCESS / RETRY_WAIT / DEAD_LETTER`；`RetryScheduler.nextRetryAt(int retryCount)` = `min(15min, 30s × 2^n) + jitter(0~10s)`，最多 5 次后 DEAD_LETTER。

- [ ] **Step 1: 写失败测试（超过次数进死信）**

```java
@Test
void 超过最大重试进死信() {
    SyncEventDO e = new SyncEventDO(); e.setRetryCount(5); e.setStatus("RETRY_WAIT");
    when(eventMapper.selectById(any())).thenReturn(e);
    processor.process(1L);
    assertThat(e.getStatus()).isEqualTo("DEAD_LETTER");
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `mvn -pl web -am test -Dtest=SyncEventProcessorTest -q`
Expected: FAIL。

- [ ] **Step 3: 实现**

`SyncEventProcessor.process`：置 `PROCESSING` → 读取 `payload` 原文 → 按 ChangeType 回源详情 → 调用统一 upsert → 成功置 `SUCCESS`；失败时 `retry_count++`，若 `retry_count < 5` 置 `RETRY_WAIT` + `next_retry_at`，否则 `DEAD_LETTER`。`RetryScheduler.nextRetryAt` 实现退避公式（常量提取）。

- [ ] **Step 4: 运行测试通过**

Run: 同 Step 2。
Expected: PASS。

- [ ] **Step 5: Commit**

```bash
git add seeex-tech-backend/web/src/main/java/com/seeex/tech/web/wecom/SyncEventProcessor.java seeex-tech-backend/web/src/main/java/com/seeex/tech/web/wecom/RetryScheduler.java
git commit -m "feat: async event state machine with exponential backoff and dead letter"
```

---

### Task 4: 回源详情 + 统一幂等 upsert + 乱序容忍

**Files:**
- Modify: `.../wecom/WeComSyncService.java`
- Test: `.../wecom/SyncUpsertTest.java`

**Interfaces:**
- Produces: `WeComSyncService.upsertUser(String userid)` / `upsertDepartment(long deptId)`：回源详情后统一 upsert（全量/增量复用）；乱序规则——`update_user` 找不到身份时按创建流程，`delete_user` 找不到时记不可匹配事件；旧 `last_synced_at` 不覆盖新数据。

- [ ] **Step 1: 写失败测试（update 找不到身份时按创建）**

```java
@Test
void update找不到身份时按创建() {
    when(employeeMapper.selectOne(any())).thenReturn(null);
    when(client.getUser(any(), eq("u1"))).thenReturn(user("u1", "张三", "P7"));
    service.upsertUser("u1");
    verify(employeeMapper).insert(any(EmployeeDO.class));
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `mvn -pl web -am test -Dtest=SyncUpsertTest -q`
Expected: FAIL（当前 upsertEmployee 直接按 employeeNo 查，无回源）。

- [ ] **Step 3: 实现**

抽取 `upsertUser(String userid)`/`upsertDepartment(long deptId)`：回源详情 → 按 `employeeNo = "wc_" + userid` 查 → 0 条 insert / 1 条 update / 多条不合并；`delete_user` 置 `LEFT` + 账号禁用 + 身份停用；写 `last_synced_at`，旧数据不覆盖新数据。全量同步复用同一方法。

- [ ] **Step 4: 运行测试通过**

Run: 同 Step 2。
Expected: PASS。

- [ ] **Step 5: Commit**

```bash
git add seeex-tech-backend/web/src/main/java/com/seeex/tech/web/wecom/WeComSyncService.java
git commit -m "feat: source-backed idempotent upsert with out-of-order tolerance"
```

---

### Task 5: 重试接口 + 受控查看原始事件接口（审计）

**Files:**
- Modify: `.../wecom/WeComController.java`

**Interfaces:**
- Produces: `POST /api/integrations/wecom/events/{id}/retry`（仅 RETRY_WAIT/DEAD_LETTER 可重试，重试记审计）；`GET /api/integrations/wecom/events/{id}/raw`（受控查看原始事件，调用记审计，仅授权员工可访问，不涉及解密）。

- [ ] **Step 1: 写失败测试（非失败状态重试被拒）**

```java
@Test
void 成功事件重试被拒() {
    SyncEventDO e = new SyncEventDO(); e.setStatus("SUCCESS");
    when(eventMapper.selectById(1L)).thenReturn(e);
    assertThatThrownBy(() -> controller.retryEvent(1L, auth))
        .isInstanceOf(BizException.class);
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `mvn -pl web -am test -Dtest=WeComControllerTest -q`
Expected: FAIL。

- [ ] **Step 3: 实现**

`WeComController` 新增两个端点：retry 校验状态 ∈ {RETRY_WAIT, DEAD_LETTER}，否则 `BizException(CONFLICT)`，成功后记审计（复用 Plan 2 `AuditService`）；查看原始事件端点校验 `wecom:events:view` 权限，记审计后返回原文（仅审计权限可见，普通前端无入口）。

- [ ] **Step 4: 运行测试通过**

Run: 同 Step 2。
Expected: PASS。

- [ ] **Step 5: Commit**

```bash
git add seeex-tech-backend/web/src/main/java/com/seeex/tech/web/wecom/WeComController.java
git commit -m "feat: add event retry and controlled raw-event view endpoints"
```

---

### Task 6: 监控指标

**Files:**
- Create: `.../wecom/SyncMetrics.java`

**Interfaces:**
- Produces: `SyncMetrics` 用 Micrometer Counter/Gauge 记录 `sync_run_total/failed/partial`、`sync_event_received/success/retry/dead_letter`、`wecom_api_error_by_errcode`、`wecom_api_latency`。

- [ ] **Step 1: 写失败测试（指标计数器递增）**

```java
@Test
void 记录死信指标() {
    SyncMetrics metrics = new SyncMetrics(meterRegistry);
    metrics.recordDeadLetter();
    assertThat(meterRegistry.counter("sync_event_dead_letter").count()).isEqualTo(1.0);
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `mvn -pl web -am test -Dtest=SyncMetricsTest -q`
Expected: FAIL。

- [ ] **Step 3: 实现**

`SyncMetrics` 注入 `MeterRegistry`，暴露计数/耗时方法；在 `SyncEventProcessor`、`WeComSyncService` 的关键分支调用。

- [ ] **Step 4: 运行测试通过**

Run: 同 Step 2。
Expected: PASS。

- [ ] **Step 5: Commit**

```bash
git add seeex-tech-backend/web/src/main/java/com/seeex/tech/web/wecom/SyncMetrics.java
git commit -m "feat: add sync and event metrics"
```

---

## Self-Review

- **Spec 覆盖**：§8.1-8.2→Task 1；§8.3→Task 2；§8.4→Task 3；§8.5→Task 4；§8.6→Task 5；§8.7→Task 6。全部覆盖，无缺口。
- **占位符扫描**：无 TBD；测试与实现骨架完整。
- **类型一致性**：`upsertUser(String)` Task 4 定义并被 Task 3/5 引用；`SyncMetrics` 方法名 Task 6 自洽；`payload` 列名与 Plan 1 Task 4 迁移一致。
