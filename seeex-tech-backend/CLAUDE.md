# 项目开发规范

> 本文件是仓库唯一的工程规范与项目记忆来源。`AGENTS.md` 与 `CLAUDE.md` 内容保持完全一致，分别供 Codex 与 Claude Code 读取；修改任一文件时必须同步更新另一份。

本项目遵循阿里巴巴《Java 开发手册》“嵩山版”，并在本文件中集中维护工程约束、API 契约、基础设施基线和架构决策。

## 适用范围与模块边界

- 运行时：Java 21、Spring Boot 4；源文件使用 UTF-8、LF 换行，Java 缩进 4 个空格，YAML 缩进 2 个空格。
- `common` 只放跨模块 API、错误模型和无业务依赖的公共类型。
- `web` 负责 HTTP 接口、鉴权、异常边界和编排。
- `file` 负责文件元数据与 OSS 适配。
- `ai` 负责 AI 适配，不反向依赖 Web 控制层。

## 必须遵守

- 类、方法和字段遵循 Java 驼峰命名；常量使用全大写下划线；布尔变量使用 `is`、`has`、`can`、`should` 等语义前缀。
- 覆写方法必须使用 `@Override`；禁止魔法数字，优先提取为有意义的常量。
- 异常使用具体类型；禁止吞掉异常、打印堆栈或将堆栈信息返回调用方。业务异常统一抛 `BizException`，由 `GlobalExceptionHandler` 转换为安全错误响应，禁止抛裸 `IllegalArgumentException`。
- 日志不得记录密码、令牌、密钥、完整身份证号、完整手机号等敏感信息；禁止使用 `System.out`/`System.err`；日志使用 `{}` 占位符，异常日志携带堆栈。
- 公共 API 使用本文件的“API 契约”章节定义的返回结构和稳定错误码。
- 公开类、接口和方法必须写中文 Javadoc；方法参数不超过 5 个，超出时封装为 DTO/Query。
- 数据访问统一使用 MyBatis-Plus（`XxxMapper` + `BaseMapper` / `Lambda` 条件构造器），禁止在业务代码中手写 SQL 字符串。
- 资源、连接和流使用 try-with-resources；集合、字符串比较和空值判断遵循 Java 安全写法。
- 不提交密钥、口令、真实 OSS 配置或本地环境文件；配置通过环境变量或部署平台注入。
- 修改 Java 代码后必须执行 `mvn verify`，检查失败不得合并。
## 编码规范（Alibaba 嵩山版落地版）

> 本文档是《阿里巴巴 Java 开发手册》嵩山版在本项目的落地约束，覆盖数据库、实体类、分层、错误处理、日志、注释、参数等维度。
> 与根目录 `AGENTS.md`（工程级硬约束）配套使用；`AGENTS.md` 优先，本文件细化。

### 0. 总则

1. 语言与运行时：Java 21，Spring Boot 4；编码 UTF-8，换行 LF，缩进 4 空格（YAML 2 空格）。
2. 本项目为 Maven 多模块：`common`（跨模块 API/错误模型/公共类型）、`web`（HTTP 接口、鉴权、异常边界、编排）、`file`（文件元数据与 OSS 适配）、`ai`（AI 适配）。
3. 命名遵循 Java 驼峰规则；所有公开 API 与关键业务代码必须使用**中文 Javadoc / 注释**。
4. 提交前必须 `mvn verify`；违反 Checkstyle 与本文档的强约束（【必须】）不得合并。

---

### 1. 命名规约

- 【必须】类名 `UpperCamelCase`；方法、参数、局部变量 `lowerCamelCase`；常量 `UPPER_SNAKE_CASE`。
- 【必须】布尔变量以 `is` / `has` / `can` / `should` 等语义前缀开头（例：`isDeleted`、`hasPermission`、`canUpload`）。
- 【必须】包名全小写、单数、点分隔（例：`com.seeex.tech.file.service`）。
- 【必须】抽象类以 `Abstract` / `Base` 开头；异常类以 `Exception` 结尾；测试类以 `Test` 结尾。
- 【推荐】接口不添加 `I` 前缀等修饰词（用 `UserService` 而非 `IUserService`）。
- 【必须】持久层统一使用 MyBatis-Plus：`XxxMapper extends BaseMapper`，禁止用 `JdbcTemplate` 在业务层手写 SQL。
- 【推荐】数据传输对象使用后缀区分职责：`XxxDO`（数据库对象）、`XxxDTO`（传输对象）、`XxxVO`（视图对象）、`XxxQuery`（查询条件）。
- 【推荐】返回类型是 `boolean` 的获取方法用 `isXxx`，其余获取方法用 `getXxx`。

---

### 2. 常量与魔法数字

- 【必须】禁止魔法数字，一律提取为有意义的 `private static final` 常量（含默认值、超时、大小上限、分页上限等）。
- 【必须】常量使用 `UPPER_SNAKE_CASE`，并在声明处用中文注释说明含义与单位。
- 【必须】跨类复用的常量放公共常量类或对应枚举（如 `ErrorCode`）；仅本类使用的常量保持 `private`。
- 【推荐】时间值声明为 `long` 并带单位注释，如 `private static final long UPLOAD_URL_EXPIRE_MS = 10 * 60 * 1000L; // 上传链接有效期 10 分钟`。

反例：

```java
// 反例：魔法数字直接写死在 Controller
if (size <= 0 || size > 50 * 1024 * 1024L) throw new IllegalArgumentException("file size exceeds 50MB");
URL url = storage.createUploadUrl(key, 10 * 60 * 1000L);
return ApiResponse.ok(new UploadUrlResponse(key, url.toString(), 600));
```

正例：

```java
private static final long MAX_FILE_SIZE_BYTES = 50L * 1024 * 1024;      // 单文件上限 50MB
private static final long UPLOAD_URL_EXPIRE_MS = 10 * 60 * 1000L;       // 上传链接有效期 10 分钟
private static final long DOWNLOAD_URL_EXPIRE_MS = 5 * 60 * 1000L;      // 下载链接有效期 5 分钟
```

---

### 3. 数据库规约

#### 3.1 建表规约

- 【必须】表名、字段名一律**小写 + 下划线**（snake_case），禁止驼峰与大小写混用；与 Java 字段的驼峰映射由 ORM 处理。
- 【必须】表名不使用数据库关键字；长度不超过 32 个字符；表达是否概念的字段用 `is_xxx`（对应 Java 布尔 `isXxx`）。
- 【必须】`varchar` / `char` 必须显式声明长度（如 `varchar(64)`）；小数用 `decimal`，禁止 `float` / `double` 存钱。
- 【必须】必备字段：主键 `id`（`bigint` 自增，跨库可考虑雪花 id）、`created_at`、`updated_at`（本项目统一 `timestamptz` / `timestamp`）。
- 【必须】表达状态/类型的字段用 `varchar` 存储**可枚举字符串**（本项目为 `status`、`visibility`、`storage_class`），并在 Java 侧用枚举/常量收敛取值，禁止裸魔法字符串散落。
- 【必须】禁止使用存储过程、触发器、视图、外键约束；关联关系在应用层保证。
- 【推荐】逻辑删除：本项目以 `status`（`PENDING` / `CONFIRMED` / `DELETED`）与 `deleted_at` 表达软删除，禁止物理删除。
- 【必须】方言一致性：迁移脚本必须与 `application.yml` 的 JDBC 方言一致（本项目统一 MySQL）。

#### 3.2 索引规约

- 【必须】组合索引遵循最左前缀；区分度高的字段放最左（本项目 `idx_file_metadata_owner (tenant_id, owner_id, status)` 即为多租户过滤的典型形态）。
- 【必须】唯一约束字段（如 `idempotency_key`、`object_key`）必须建唯一索引/约束。
- 【必须】禁止在频繁更新的字段上建索引；`status` 这类低区分度字段不单独建索引。
- 【推荐】每个表索引不超过 5 个；命名 `idx_<表名>_<字段>`、`uk_<表名>_<字段>`。

#### 3.3 SQL 与 ORM 规约

- 【必须】禁止在业务层手写 SQL 字符串（`JdbcTemplate` 拼接 SQL），数据访问统一使用 MyBatis-Plus（`XxxMapper extends BaseMapper` + `LambdaQueryWrapper` / `LambdaUpdateWrapper`）；仅极复杂/报表类 SQL 才落 XML Mapper 或注解，且必须预编译参数绑定。
- 【必须】所有 SQL 使用**预编译参数绑定**（`?` / MyBatis `#{}`），禁止字符串拼接，防止 SQL 注入。
- 【必须】禁止 `SELECT *`，明确列出所需字段。
- 【必须】单条 SQL 影响行数可预期；更新/删除必须带条件，禁止无条件的全表 `UPDATE` / `DELETE`。
- 【推荐】慢 SQL 必须记录并优化；大数据量分页使用 `PageRequest` / MyBatis-Plus 分页插件（已配置 `PaginationInnerInterceptor`）。
- 【推荐】ORM 查询结果映射到 `XxxDO` / 专用 POJO，禁止用 `Map` 承载业务数据。

---

### 4. 实体类与 POJO 规约

- 【必须】DO / DTO / VO / Query 分层，禁止一个类混用所有职责，也禁止用 `Map` 在层间传递业务数据。
- 【必须】实体类字段使用**包装类型**（`Long`、`Integer`、`Boolean`）以表达「可空」，避免基本类型默认值掩盖空值语义；仅纯本地位枚举/局部变量可用基本类型。
- 【必须】POJO 命名与表名/职责对应：表 `file_metadata` 对应 `FileMetadataDO`。
- 【推荐】DO 作为持久化对象不建议直接作为接口出入参，Controller 边界使用 DTO/VO 隔离外部契约与内部实现。
- 【推荐】对外 DTO/VO 的序列化字段命名与 `本文件的“API 契约”章节` 一致；分页用 `PageRequest` / `PageResponse<T>`。
- 【推荐】不可变值对象优先使用 Java `record`（本项目 `PageRequest`、`PageResponse`、`UploadUrlResponse` 已采用）；需要可变/继承语义时用普通 POJO。
- 【推荐】公开 API 涉及的 DTO/VO 必须附中文 Javadoc 说明字段含义与约束。

---

### 5. 分层规约 Controller → Service → Mapper

- 【必须】`Controller` 只做：参数接收与校验、调用 `Service`、组装 `ApiResponse`；**禁止**在 Controller 写业务逻辑。
- 【必须】业务逻辑与事务边界放在 `Service` 层；`@Transactional` 只加在 `Service` 方法上，不加在 Controller。
- 【必须】Controller 通过**构造器注入**依赖（本项目已采用），禁止字段 `@Autowired`。
- 【必须】Controller 返回值统一 `ApiResponse<T>`，成功用 `ApiResponse.ok(...)`，失败由异常层统一处理（不手动构造失败响应）。
- 【必须】跨模块调用（如 `web` 调 `file`）走 `client` 层封装（本项目 `FileServiceClient`），不得在业务代码散落原始 `RestClient` / `RestTemplate` 调用。
- 【推荐】Controller 方法职责单一、路径用复数资源名（`/api/files`），HTTP 语义正确：创建 `POST`、查询 `GET`、删除 `DELETE`。
- 【推荐】Service 面向接口编程（接口 + 实现类），便于测试与替换；简单模块可直接用 `@Service` 类。

---

### 6. 参数与校验规约

- 【必须】方法参数不超过 5 个；超过时封装为 DTO/Query 对象。
- 【必须】Controller 入参必须校验，禁止信任前端；优先使用 Bean Validation（`@Valid` + `@NotNull` / `@Size` / `@Min` / `@Max` / `@Pattern`），校验失败统一转 `VALIDATION_FAILED`。
- 【必须】参数校验失败、参数非法必须抛 `BizException` 或触发统一校验异常，**禁止**抛裸 `IllegalArgumentException`（当前 `FileController` 违规）。
- 【必须】分页参数使用 `PageRequest`，内部已做页号/页大小下限与上限收敛（`size` 上限 200）。
- 【必须】跨服务身份参数（`X-User-Id`、`X-Tenant-Id`、`Idempotency-Key`）从请求头获取后，作为显式参数传递，不得从全局线程变量隐式读取。
- 【推荐】幂等写接口必须携带幂等键并配合唯一约束（本项目 `Idempotency-Key` + `file_metadata.idempotency_key unique` 已符合）。

---

### 7. 错误处理规约

- 【必须】异常使用**具体类型**，禁止捕获后吞掉、`printStackTrace()` 或把堆栈/内部信息直接返回调用方。
- 【必须】业务异常统一抛 `BizException(ErrorCode)`（可带自定义安全 message）；由 `GlobalExceptionHandler` 统一转换为 `ApiResponse.fail(...)`。
- 【必须】错误码使用 `common` 的 `ErrorCode` 枚举（大写下划线、稳定、不可随意变更）；新增错误码同步更新 `common/本文件的“API 契约”章节` 与 `本文件的“项目记忆与架构决策”章节`。
- 【必须】未知异常由 `GlobalExceptionHandler` 兜底，返回 `INTERNAL_ERROR` 且 message 为安全文案，绝不透出异常类名、SQL、堆栈。
- 【必须】HTTP 状态码是传输语义的权威：4xx（参数/鉴权/权限/未找到/冲突/限流）、5xx（内部错误）；`ApiResponse` 的 `error_type` 与 HTTP 状态码保持一致映射。
- 【推荐】`GlobalExceptionHandler` 补充 `MethodArgumentNotValidException` / `ConstraintViolationException` → `VALIDATION_FAILED`、`MissingServletRequestParameterException` → `VALIDATION_FAILED`、`AccessDeniedException` → `FORBIDDEN` 的处理，避免校验异常落入通用兜底。

---

### 8. 日志规约

- 【必须】使用 SLF4J（Lombok `@Slf4j`）输出日志；**禁止** `System.out` / `System.err`。
- 【必须】日志不得记录密码、令牌、密钥、完整身份证号、完整手机号等敏感信息，需脱敏。
- 【必须】日志使用占位符 `{}`，禁止字符串拼接：`log.info("创建上传链接 key={}", objectKey)`。
- 【必须】异常日志必须携带堆栈：`log.error("上传失败 objectKey={}", objectKey, e)`（把异常作为最后一个参数），禁止只打印 `e.getMessage()`。
- 【必须】关键链路（上传/下载/删除、跨服务调用）记 `INFO`；明细与调试记 `DEBUG`；可恢复异常记 `WARN`；不可恢复异常记 `ERROR`。
- 【必须】保留链路追踪：日志 pattern 已带 `traceId` / `spanId`（见 `application.yml`），禁止自定义日志格式覆盖该 pattern。
- 【推荐】每个模块使用统一日志前缀或结构化字段，便于检索。

---

### 9. 注释规约（中文）

- 【必须】类、接口、公开方法必须写**中文 Javadoc**，说明「做什么、参数与返回值、约束/异常」。
- 【必须】公共枚举与错误码项附中文说明（本项目 `ErrorCode` 已附中文默认消息，需保持）。
- 【必须】关键业务逻辑、非显而易见的分支、魔法数字消除后的常量，均需中文注释说明意图。
- 【推荐】方法内复杂逻辑用 `// 中文注释` 分段说明；注释与代码同步更新，禁止注释与实现不符。
- 【推荐】待办项用 `// TODO(责任人): 描述` 标注；不再使用的代码直接删除，禁止注释掉大段代码留档。
- 【推荐】禁止英文注释/无注释的公开 API（当前 `ApiResponse`、`ErrorType`、`FileController` 等为英文注释，需按此条整改为中文）。

---

### 10. 代码格式与工程化

- 【必须】缩进 4 空格，UTF-8，LF，行尾无空格，文件末尾换行（由 `.editorconfig` 与 Checkstyle 保证）。
- 【必须】一行一个声明、一条语句，禁止将字段/方法压缩到同一行（当前 `OssStorageService`、`FileMetadataService` 违规）。
- 【必须】`import` 不冗余、无未使用导入（Checkstyle 已覆盖 `RedundantImport` / `UnusedImports`）。
- 【必须】覆写方法加 `@Override`；关闭资源用 try-with-resources。
- 【必须】集合/字符串/空值判断遵循 Java 安全写法（`Objects.equals`、`StringUtils.isBlank`、`Collections.emptyList()` 等）。
- 【推荐】提交前执行 `mvn verify`；前端执行 `npm run lint` / `npm run build`。

---

### 11. 提交前检查清单

1. `mvn verify` 通过（含 Checkstyle）。
2. 新增公共 API / 错误码已同步 `common/本文件的“API 契约”章节` 与 `本文件的“项目记忆与架构决策”章节`。
3. 新表/新字段有对应 Flyway 版本迁移脚本，且方言与数据源一致。
4. 公开类/方法有中文 Javadoc；无魔法数字；无裸异常抛出；无敏感信息日志。
5. 未提交密钥、真实 OSS 配置与本地环境文件。


## API 契约

Every JSON response uses the shared `ApiResponse<T>` envelope:

```json
{
  "code": "OK",
  "message": "success",
  "data": {}
}
```

Failure responses omit `data` and include a stable string `code`, an `error_type`, and a safe `message`:

```json
{
  "code": "VALIDATION_FAILED",
  "error_type": "VALIDATION",
  "message": "Please check the highlighted fields"
}
```

`code`, `error_type`, and `message` jointly identify an error. For example, a missing user is `{ "code": "USER_NOT_FOUND", "error_type": "NOT_FOUND", "message": "User not found" }`. `error_type` is one of `VALIDATION`, `AUTHENTICATION`, `AUTHORIZATION`, `NOT_FOUND`, `CONFLICT`, `RATE_LIMITED`, `BUSINESS`, or `INTERNAL`. `code` is a stable machine-readable identifier in upper snake case. `message` is safe for display and must not expose secrets, stack traces, or SQL. HTTP status codes remain authoritative for transport semantics. Backend responses must not expose a `record` field; paginated collections use `items`.

### Application registry and SSO

Applications can be onboarded without source refactoring. A platform administrator creates an application with `POST /api/platform/apps`; the response contains a CI token that is shown only at creation time. A CI pipeline reports route metadata with `POST /api/platform/apps/{appCode}/route-metadata` and `X-CI-Token`, then an administrator publishes a version with `POST /api/platform/apps/{appCode}/route-metadata/{version}/publish`.

Authenticated users read permission-filtered menus from `GET /api/me/menus` and available applications from `GET /api/me/apps`. A menu entry includes `app_code`, `base_url`, `route.path`, `route.open_type`, and `route.permission_code`.

Cross-application navigation uses a short-lived one-time exchange code. The authentication center creates one with `GET /api/sso/authorize?appCode=...&redirectPath=...`; the target application's backend consumes it once with `POST /api/sso/token/exchange`.


## 后端基础设施基线

当前项目是 Spring Boot 4 + Java 21 的多模块服务，建议按下面顺序补齐基础设施：

1. **可观测性**：Actuator 健康检查、Micrometer 指标、Prometheus 抓取、OpenTelemetry Trace 导出；日志统一带 `traceId` 和 `spanId`。
2. **运行时基础**：容器镜像、配置从环境变量注入、优雅停机、存活/就绪探针、资源限制和时区统一。
3. **数据层**：MySQL 连接池、迁移工具（Flyway 或 Liquibase）、慢 SQL 监控、备份和恢复演练。
4. **可靠性**：超时、重试、限流、幂等键、统一错误码；跨服务调用优先使用 Spring 的 HTTP Client，并透传 trace context。
5. **安全**：OAuth2/OIDC 资源服务器、密钥托管、审计日志、敏感字段脱敏和依赖漏洞扫描。
6. **交付**：单元/集成测试、质量门禁、镜像扫描、灰度发布和回滚策略。

### 链路追踪

`web` 模块已接入 Micrometer Tracing 的 OpenTelemetry bridge。默认配置：

- 服务名：`seeex-tech-web`
- 采样率：`MANAGEMENT_TRACING_SAMPLING_PROBABILITY`，默认 `1.0`（开发环境）；生产建议 `0.05` 到 `0.2`
- OTLP HTTP 地址：`OTEL_EXPORTER_OTLP_TRACES_ENDPOINT`，默认 `http://localhost:4318/v1/traces`
- Actuator：`/actuator/health`、`/actuator/prometheus`、`/actuator/metrics`
- 日志字段：`traceId`、`spanId`

生产环境建议部署 OpenTelemetry Collector，再由 Collector 转发到 Tempo、Jaeger、SkyWalking 或云厂商后端。应用只需要指向 Collector，不直接绑定具体厂商。

本地验证可以启动任意支持 OTLP HTTP 的 Collector，然后访问一个接口，确认日志出现 trace/span ID，并在追踪后端检索服务名 `seeex-tech-web`。


## 项目记忆与架构决策

### 规范基线

项目采用阿里巴巴《Java 开发手册》“嵩山版”作为 Java 代码规范基线。阿里云资料页标注该版本为当前公开版本，并提供 P3C 配套检查工具：

- [阿里云 Java 手册专题](https://developer.aliyun.com/special/tech-java)
- [阿里云 Java 资源库](https://developer.aliyun.com/group/tech_library/ebook/)
- [Alibaba P3C](https://github.com/alibaba/p3c)

本文件同时作为 `AGENTS.md` 与 `CLAUDE.md` 的统一规范来源；两个文件必须保持完全一致。API 契约、编码规范、基础设施基线和架构记忆均已在本文件中集中维护。

### 工程基础设施

- 根目录 `.editorconfig` 统一编码、换行、缩进和尾随空格。
- 根 `pom.xml` 在 `verify` 阶段执行 Checkstyle，阻止明显的命名、导入顺序、空代码块、`System.out`/`System.err` 和行尾空格问题。
- 数据库连接配置与 Flyway 迁移脚本当前统一使用 MySQL 方言。
- 生产环境配置通过环境变量或部署平台注入，不提交密钥、口令、真实 OSS 配置或本地环境文件。

### 关键架构决策

- 数据访问统一使用 MyBatis-Plus（`XxxMapper extends BaseMapper` + `Lambda` 条件构造器），禁止在业务层手写 SQL（包括 `JdbcTemplate` 拼接）。
- `BizException` 位于 `common`，供跨模块复用；`web` 与 `file` 各自维护 `GlobalExceptionHandler`。
- `common` 只放跨模块 API、错误模型和无业务依赖的公共类型；`web` 负责 HTTP 接口、鉴权、异常边界与编排；`file` 负责文件元数据与 OSS 适配；`ai` 负责 AI 适配且不反向依赖 Web 控制层。
- 应用接入统一通过 `ApplicationRegistryService`：CI 使用 `X-CI-Token` 上报版本化路由元数据，用户通过 `/api/me/menus` 获取权限过滤后的菜单，跨应用 SSO 使用一次性 exchange code。

### 维护规则

新增模块、公共接口或错误码时，先更新本文件中的对应章节，再实现代码；`AGENTS.md` 与 `CLAUDE.md` 必须同步更新。规范检查失败时优先修复代码，不通过忽略规则掩盖问题。

### 2026-11-16 页面开发契约

- 按钮、字段和参数以 `../docs/plans/seeex-console-button-contract.md` 为准。用户明确要求不运行，覆盖本轮 mvn verify / 前端构建测试要求；仅记录静态核对，运行验收保持未完成。
- 应用注册支持 Web 入口 baseUrl、后台入口 adminUrl、API 地址 apiUrl、SSO 回调路径；增加应用编辑/详情、版本列表、手工导入接口；CI 上报仅使用应用 CI 凭证。所有应用管理动作独立鉴权。
- 角色资源配置使用 rbac:roles:grant，主体角色绑定使用 rbac:roles:assign，员工直接授权使用 employees:grant。只替换人工绑定，应用范围外授权保留。
- 文件网关身份从 Authentication 派生；先校验整批归属再删除存储对象。

- 本轮新增员工登录账号 `GET/PUT /api/employees/{id}/account`，body 为 loginPhone/password/status，密码只写；变更凭证或停用账号撤销会话。
- 企业微信配置使用 WECOM_SETTINGS_KEY 加密后存储；新增公开 login status、带 Cookie 绑定的 OAuth、后台批次/事件消费与上海时间每日校准。V19/V20 只新增文件，未执行。
- 主键显式使用数据库 AUTO；连接配置的固定主键使用 INPUT；与 MySQL 自增建表约定一致。

### 2026-11-16 认证基础平台与应用导航

- 导航以 `/api/me/navigation` 返回的工作台、基础平台、逐应用分组为准，应用路由增加 entry（WEB/ADMIN）、category、parentRouteCode，按端和层级独立授权。资源增加 entryType，入口类型不可在已有资源上改写。
- 应用服务凭证与 CI 凭证分离；SSO 交换须携带 X-App-Secret 和 entry，消费时再次检查当前权限；`/api/app-auth/permissions` 与 `/check` 为可信应用后端提供本应用实时权限，不能由浏览器提供任意 subjectId 代替本地会话。
- 前端失败统一归一与可见反馈，查询错误不能伪装空数据或无权限。所有开发继续遵守本轮不运行、不构建、不测试、不迁移。

### 应用治理扩展
- 应用管理员以 application_admin 独立存储，只有平台管理员可以维护；应用管理员仅维护自己的应用，不获得平台管理权限。
- application_policy 按应用、主体、资源定义 ALLOW/DENY 及数据范围；实时权限计算消费策略，DENY优先。数据范围必须合并实际集合并由查询/详情和外部接口消费。
- 文件增加独立 application_code，服务间以 X-App-Code 传递已验证应用边界；对象查询、确认、下载、删除必须校验该边界。旧文件归入 platform。继续禁止运行。

### 组织、企微和审计补齐
- 组织响应提供更新时间、来源、上级名称、范围内在职员工数和角色直接关联统计，不把统计作为权限。
- 企业微信职位文本通过显式映射关联本地职级，全量/增量复用；同步预览只读取远端数据，不修改组织，确认同步重新读取最新通讯录。
- 审计增加 applicationCode/resourceKey、结果和时间筛选，CSV导出单独 audit:export 鉴权、固定上限并防公式注入。导出使用与列表相同查询条件。继续仅静态检查。

### 缺陷修复约束
- 员工部门/职级及默认角色变更若改变继承角色（含移除DENY来源），必须额外校验rbac:roles:assign；企微映射配置涉及角色也采用此门禁。
- 企微HTTP读取在事务外，独立Bean以REQUIRES_NEW原子写部门树/单员工；批次租约和执行令牌在每次写入事务内校验，结果与员工写入同事务提交，失败另记。
- 应用策略使用rowVersion CAS；管理员集合和角色数据范围以独立配置版本CAS串行修改，前端必须传expectedVersion，409保留草稿并提供重载。
- 审计名称及组织/角色统计批量读取；大型员工/角色字典按关键字限制返回并保留已选项。继续禁止运行项目、测试、构建和迁移。

### 新应用申请审批实施契约
- 首期使用平台自有受限顺序审批器及Arco节点/属性配置；不引入未经运行验证的Flowable/BPMN依赖，不提供任意BPMN、脚本、表达式或HTTP节点。支持固定/角色/部门负责人候选、ALL/ANY、受控条件和兜底；办理与定向交付不可从模型中删除。
- 所有状态变更以版本CAS和服务事务保护；申请人、任务资格、办理候选与交付收件人均由会话及服务端记录判定。直接注册改为platform:apps:override加理由。
- 交付使用独立APP_DELIVERY_KEY（Base64编码32字节）和APP_DELIVERY_KEY_ID加密；只有原申请人POST领取一次，过期返回DELIVERY_EXPIRED（CONFLICT/409）；审计、通知和普通响应禁止包含密钥。
- 新迁移V25及后续只编写不执行；继续禁止构建、类型检查、测试执行、启动及真实企微/OSS调用。

### 2026-11-17 全平台复查补充
- V25首次交付前普通应用编辑和CI/服务凭证轮换拒绝，受控补发仍交原申请人，确认收件与解除门禁同事务。注册后编码所有权保留，提交对环境绑定加锁。所有新接入路径保留安全审批。
- V26独立employees:accounts；角色启停额外要求rbac:roles:grant。V27账号authVersion及会话accountId/accountVersion约束重置并发签发/刷新，旧本地会话升级需重新登录。
- AUTH_JWT_SECRET与FILE_SERVICE_TOKEN取消开发默认值；管理员引导默认关闭，显式引导密码至少12字符且UTF-8不超过72字节。生产持久会话不积累明文刷新令牌内存表。
- Spring Boot4使用显式spring-boot-jackson2/JavaTime与jackson2 MVC偏好保留现有JSON契约；源码AST及组件声明核对不等于编译、类型检查、运行或迁移通过。当前限制仍是不运行。
