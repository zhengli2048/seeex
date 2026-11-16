# SEEEX Console 统一方案与实施计划

## 1. 目标

将当前 Seeex Console 从接口样例改造成可持续扩展的企业管理平台：

- 使用 SEEEX 品牌 Logo 和统一视觉系统
- 使用成熟 Arco Design 组件完成布局、菜单、树、表格、表单、抽屉和权限交互
- 以“工作台、系统平台、应用中心”为顶级导航
- 用树形结构表达应用、模块、页面、菜单、操作和 API 资源
- 支持人员、部门、职级、角色、应用等多来源权限组合
- 每个页面具备完整业务闭环，避免假数据、JSON 详情和 ID 手填

## 2. 顶级信息架构

```
工作台
系统平台
├── 组织管理
│   ├── 员工
│   ├── 部门树
│   └── 职级
├── 权限中心
│   ├── 角色
│   ├── 资源树
│   ├── 人员授权
│   ├── 部门授权
│   ├── 职级授权
│   └── 权限分析
├── 应用管理
│   ├── 应用注册
│   ├── 应用资源
│   ├── 应用管理员
│   └── 应用认证
├── 企业微信
│   ├── 连接配置
│   ├── 通讯录同步
│   ├── 事件回调
│   └── 登录配置
├── 文件中心
└── 审计日志
应用中心
├── 系统平台
├── AI 应用
└── 后续业务应用
```

菜单数据不再写死在页面中。后端返回应用、模块、资源树和用户可见权限，前端只负责渲染。

## 3. 树形模型

### 应用树

```
Application
└── Module
    └── Page
        ├── Menu
        ├── Action
        ├── API
        └── DataScope
```

### 组织树

```
公司
└── 部门
    └── 子部门
        └── 员工
```

### 权限树

每个节点包含：

- id
- parentId
- name
- code
- type
- applicationId
- route
- icon
- sortOrder
- visible
- status
- permissionCode

前端使用 Arco 的 Tree、TreeSelect、Cascader、Table、Drawer 和 Descriptions，不允许手写树形交互。

## 4. 权限模型

### 权限主体

- EMPLOYEE
- DEPARTMENT
- JOB_GRADE
- ROLE
- APPLICATION

### 授权来源

- EMPLOYEE_DIRECT
- EMPLOYEE_ROLE
- DEPARTMENT_ROLE
- JOB_GRADE_ROLE
- SYSTEM_ROLE
- APPLICATION_ROLE

### 授权效果

- ALLOW
- DENY

### 计算规则

```
人员直接权限
+ 人员角色
+ 部门角色
+ 职级角色
+ 系统默认角色
+ 应用角色
→ 合并
→ DENY 覆盖 ALLOW
→ 得到最终权限
```

权限分析页面必须展示：

- 最终有效权限
- 允许权限
- 拒绝权限
- 来源类型
- 来源名称
- 命中的角色
- 被 DENY 覆盖的原因

## 5. 页面功能闭环

### 登录

- 品牌 Logo
- 本地账号登录
- 企业微信登录
- 错误、停用、无权限状态
- 登录态恢复和退出

### 工作台

- 当前用户
- 常用应用
- 最近访问
- 待处理事项
- 系统公告
- 同步和系统状态

### 员工

- 分页、筛选、排序
- 员工详情
- 部门和职级使用选择器
- 角色绑定
- 直接授权
- 最终权限分析
- 权限来源查看
- 启用、停用、软删除
- 操作审计

### 部门树

- 树形展示
- 新增子部门
- 移动部门
- 修改负责人
- 查看员工
- 绑定角色
- 查看继承权限
- 企业微信同步状态

### 职级

- 职级列表
- 默认角色
- 企业微信职位映射
- 查看关联人员
- 查看最终权限

### 角色

- 角色列表
- 应用范围
- 权限资源树
- 绑定人员、部门、职级
- ALLOW/DENY 配置
- 复制、停用、删除
- 角色变更审计

### 资源树

- 应用、模块、页面、菜单、操作、API 分层
- 树形拖拽排序
- 新增子资源
- 资源详情
- 路由和权限编码
- 菜单显示控制
- 状态管理

### 应用中心

- 应用卡片
- 应用分组
- 应用状态
- 最近访问
- 授权状态
- 应用管理员
- 应用版本
- 应用健康状态

### 企业微信

- 配置状态
- OAuth 登录配置
- 通讯录同步
- 同步批次
- 事件回调
- 失败重试
- 同步差异

### 文件中心

- 上传
- 分页
- 类型和状态筛选
- 下载
- 删除
- 批量删除
- 文件权限来源

### 审计日志

- 登录
- 授权变更
- 组织变更
- 应用变更
- 企业微信同步
- 文件操作
- 操作者、时间、来源、结果和详情

## 6. 统一前端组件参数

### PageShell

- title
- description
- breadcrumb
- actions
- permission
- loading
- error

### DataTable

- columns
- query
- filters
- rowKey
- selection
- pagination
- actions
- emptyState
- permission

### EntityDrawer

- mode
- title
- fields
- initialValues
- loading
- readonly
- onSubmit
- onCancel

### RelationSelector

- relationType
- value
- multiple
- tree
- searchable
- loadOptions
- labelField
- valueField
- disabled

### PermissionTree

- applicationId
- checkedKeys
- indeterminateKeys
- effect
- showInherited
- showSource
- onChange

### PermissionSourcePanel

- employeeId
- effectivePermissions
- allowPermissions
- denyPermissions
- sources
- loading

## 7. 接口返回约定

关联对象不只返回 ID：

```json
{
  "id": 12,
  "name": "研发部",
  "code": "RD"
}
```

分页统一：

```json
{
  "items": [],
  "total": 0,
  "page": 1,
  "pageSize": 20
}
```

详情使用业务对象，不返回数据库实体 JSON。

## 8. 实施计划

### Phase 1：设计系统和应用壳

- SEEEX Logo 和品牌色
- 主布局
- 顶部栏
- 应用切换
- 左侧树形菜单
- 面包屑
- 页面状态
- 统一主题变量

### Phase 2：应用和资源模型

- 应用注册
- 应用树
- 资源树
- 菜单注入
- 应用权限边界

### Phase 3：组织管理重做

- 员工
- 部门树
- 职级
- 关系选择器
- 详情抽屉
- 关联对象展示

### Phase 4：权限中心重做

- 角色
- 人员授权
- 部门授权
- 职级授权
- 直接授权
- 权限分析
- 来源追踪
- DENY 优先

### Phase 5：集成能力

- 企业微信
- 文件中心
- 应用认证
- AI 应用接入

### Phase 6：质量验收

- 页面逐项验收
- API 契约检查
- 权限组合检查
- 空状态和错误状态检查
- 前端 lint/build
- 后端 test/checkstyle
- 真实数据库迁移检查

## 9. 当前已准备

- SEEEX Logo 已放置到：
  `seeex-tech-ui/public/brand/seeex-logo.png`
- 当前后端已经具备多来源权限计算基础：
  - 人员直接资源授权
  - 人员、部门、职级角色绑定
  - DENY 优先
  - 最终权限来源返回
- 下一步从 Phase 1 开始，先重做主布局、登录页和应用分组菜单。

