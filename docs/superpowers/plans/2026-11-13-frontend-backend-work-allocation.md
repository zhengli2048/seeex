# Seeex 前后端开发分工与联调计划

对应设计方案：`D:\seeex\code\docs\superpowers\specs\2026-11-13-seeex-technical-architecture.md`

对应实施计划：`D:\seeex\code\docs\superpowers\plans\2026-11-13-seeex-platform-implementation.md`

## 一、分工边界

### 后端负责

代码仓库：`D:\seeex\code\seeex-tech`

- MySQL Flyway 表结构和初始化数据
- 统一 API 响应、错误码和 HTTP 状态码
- 登录、刷新、注销、当前用户接口
- RBAC 角色、资源、授权和数据范围
- 员工、部门、职级管理接口
- 企业微信 OAuth 和回调验签
- 企业微信同步批次、事件、重试和死信
- 独立文件服务和 OSS 签名 URL
- 文件归属、租户权限、原始文件名和清理任务
- 审计日志
- OpenTelemetry、Actuator、服务间调用和安全配置

### 前端负责

代码仓库：`D:\seeex\code\seeex-tech-ui`

- 登录页、OAuth 回调页和登录态管理
- 平台工作台
- 员工管理页面
- 组织管理页面
- 职级管理页面
- RBAC 角色和资源页面
- 企业微信配置页面
- 同步批次和事件页面
- 文件中心页面
- 审计日志页面
- 菜单、路由和权限展示
- TanStack Query 查询缓存和失效策略
- 表单校验、列表状态、分页和操作确认
- 浅色视觉主题和响应式布局

## 二、前后端共同遵守的契约

### API 响应

```json
{
  "is_success": true,
  "code": "OK",
  "message": "success",
  "data": {}
}
```

错误响应：

```json
{
  "is_success": false,
  "code": "RESOURCE_NOT_FOUND",
  "error_type": "NOT_FOUND",
  "message": "资源不存在"
}
```

前端不再使用 `error_message`。

### 认证请求

浏览器访问 `web`：

```http
Authorization: Bearer <access_token>
```

业务服务访问 `file`：

```http
X-Service-Token: <service-token>
X-User-Id: <resolved-user-id>
X-Tenant-Id: <resolved-tenant-id>
```

### 平台基础菜单

```text
/                    平台工作台
/employees           员工管理
/departments         组织管理
/job-grades          职级管理
/rbac/roles          角色权限
/rbac/resources      资源管理
/wecom               企业微信
/wecom/sync          同步中心
/files               文件中心
/audit-logs          审计日志
```

这些是平台基础菜单，不是业务产品最终菜单。业务模块使用 `/business/*` 独立扩展。

## 三、后端开发顺序

### Backend 1：数据库和 Flyway

交付：

- `V1__organization.sql`
- `V2__authentication.sql`
- `V3__rbac.sql`
- `V4__wecom_sync.sql`
- `V5__audit.sql`
- `V6__file_metadata.sql`

约束：MySQL 8、utf8mb4、InnoDB、不使用外键、所有字段有 `COMMENT`。

### Backend 2：认证和用户上下文

交付接口：

```text
POST /api/auth/login
POST /api/auth/refresh
POST /api/auth/logout
GET  /api/me
```

### Backend 3：平台基础管理 API

交付接口：

```text
GET/POST/PATCH /api/employees
GET/POST/PATCH /api/departments
GET/POST/PATCH /api/job-grades
GET/POST/PATCH /api/rbac/roles
GET/POST/PATCH /api/rbac/resources
GET /api/me/permissions
```

### Backend 4：企业微信

交付接口：

```text
GET  /api/auth/wecom/authorize
GET  /api/auth/wecom/callback
POST /api/integrations/wecom/events
POST /api/integrations/wecom/sync/full
GET  /api/integrations/wecom/sync/runs/{runId}
POST /api/integrations/wecom/events/{eventId}/retry
```

### Backend 5：文件服务

交付接口：

```text
POST   /api/files/upload-url
POST   /api/files/confirm
GET    /api/files/download-url
DELETE /api/files
```

## 四、前端开发顺序

### Frontend 1：API 和登录基础层

交付：

- Axios 实例
- Bearer Token 注入
- 401 跳转登录
- 403 无权限页面
- `ApiResponse<T>` 类型
- 错误码类型
- TanStack Query 基础配置

### Frontend 2：平台布局和菜单

交付：

- `AdminLayout`
- `Sidebar`
- 平台基础菜单
- 权限菜单过滤
- 面包屑
- 页面标题
- 浅色主题

### Frontend 3：认证和工作台

交付：

- 登录页
- 企业微信 OAuth 回调页
- 当前用户信息
- 平台工作台

### Frontend 4：组织和权限管理

交付：

- 员工管理
- 部门管理
- 职级管理
- 角色管理
- 资源管理
- 授权矩阵

### Frontend 5：企业微信、文件和审计

交付：

- 企业微信配置
- 同步批次
- 同步事件和重试
- 文件中心
- 文件下载和删除
- 审计日志

## 五、联调顺序

```text
1. 数据库初始化
2. 后端健康检查
3. 登录和 /api/me
4. 前端登录页
5. 员工/部门/职级列表
6. RBAC 权限和菜单
7. 企业微信 OAuth
8. 企业微信同步
9. 文件上传/确认/下载
10. 审计日志和异常场景
```

每完成一个阶段，后端提供可调用接口和 JSON 示例，前端使用真实接口联调，不使用长期 mock 替代。

## 六、双方交付标准

后端交付每个接口时必须提供：

- URL 和 HTTP 方法
- 请求参数
- 请求头
- 成功响应
- 错误响应
- 权限码
- 分页字段
- 幂等要求

前端交付每个页面时必须具备：

- 路由
- 菜单权限
- 加载态
- 空态
- 错误态
- 无权限态
- 分页或筛选
- 操作确认
- 成功和失败提示

## 七、当前可以立即分配的任务

后端可以从“数据库和 Flyway”开始，前端可以从“API 和登录基础层、平台布局和菜单”开始。两边通过 `API_CONTRACT.md` 和本文档中的接口契约同步，不需要等待所有业务页面设计完成。
