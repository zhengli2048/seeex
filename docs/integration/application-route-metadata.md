# 应用接入认证基础平台

更新：2026-11-16。本文对齐当前接口源码；本轮没有运行服务、构建、测试或数据库迁移。平台当前提供自定义 SSO 和权限判定接口，不声明兼容 OIDC。

## 1 在页面注册应用

管理员进入“基础平台 → 应用管理 → 应用中心”，点击“新增应用”，配置 appCode、name、description、type、logo、ownerEmployeeId、status、baseUrl（Web）、adminUrl（可选后台）、apiUrl、repositoryUrl、ssoCallbackPath。应用编码不可修改，platform 为保留编码。

POST `/api/platform/apps` 需要 Bearer 和 `platform:apps:create`；PUT `/api/platform/apps/{appCode}` 需要 `platform:apps:update`。HTTP(S) 地址不得带账号密码、查询串或片段。SSO 回调路径须为安全站内路径，两端使用各自登记域名加该路径。

创建响应 `{app,ciToken,clientSecret}` 中的两个凭证仅显示一次：

- ciToken：CI 上报路由，存入 CI Secret；轮换需要 `platform:apps:ci`。
- clientSecret：应用后端进行身份交换、查询和检查权限；仅保存在后端 Secret。轮换需要 `platform:apps:auth`。

存量应用在迁移 V21 后，通过应用详情“认证与接入 → 轮换服务凭证”取得 clientSecret。凭证明文不支持回查，也不得下发给浏览器。

## 2 上报双端路由

页面的“路由与版本 → 添加路由版本”提供结构化表单；也可以由 CI 上报：

```http
POST /api/platform/apps/ai-recruitment/route-metadata
X-CI-Token: <ci-token>
Content-Type: application/json

{
  "version": "2026.11.16.1",
  "gitCommit": "a1b2c3d",
  "environment": "PROD",
  "baseUrl": "https://recruit.example.com",
  "routes": [
    {
      "routeCode": "jobs",
      "name": "招聘岗位",
      "path": "/jobs",
      "permissionCode": "app:ai-recruitment:web:jobs:view",
      "entry": "WEB",
      "category": "招聘服务",
      "parentRouteCode": null,
      "icon": "Briefcase",
      "openType": "NEW_TAB",
      "visible": true,
      "sortOrder": 10
    },
    {
      "routeCode": "jobs-admin",
      "name": "岗位管理",
      "path": "/jobs",
      "permissionCode": "app:ai-recruitment:admin:jobs:view",
      "entry": "ADMIN",
      "category": "招聘管理",
      "parentRouteCode": null,
      "icon": "Settings",
      "openType": "CURRENT_TAB",
      "visible": true,
      "sortOrder": 10
    }
  ]
}
```

entry 为 WEB/ADMIN，旧报文缺省为 WEB；ADMIN 要求应用已配置 adminUrl。category 缺省常用功能，最多64字；parentRouteCode 可空，上级须存在且同端、同分类，不得成环。routeCode 在版本内唯一；path 在同端内唯一；permissionCode 全版本唯一，不能 Web/后台共用。空权限码由系统按 `app:{appCode}:{web|admin}:{routeCode}:view` 生成。

environment 为 DEV/TEST/PROD；baseUrl 须等于登记的 Web 地址。同版本同内容幂等，不同内容返回冲突。手工导入用 POST `/api/platform/apps/{appCode}/route-metadata/manual` 和管理 Bearer。

管理员需先 `POST .../{version}/review {"approved":true}`，再 `POST .../{version}/publish`。发布把路由同步为权限 MENU 资源；新资源不会自动授权。去角色或人员权限中分配本应用对应端资源。版本移除的历史路由资源转 INACTIVE；发布/回滚会覆盖同一路由人工维护的名称、路径、分类、可见性和父级等字段。API/ACTION 资源在资源中心单独维护，不随路由发布清理。

## 3 分类导航与 SSO

登录后，平台前端读取 GET `/api/me/navigation`。服务器按当前权限返回“工作台、基础平台、应用A、应用B”；应用内按 Web/后台、分类、资源父子组树。GROUP 仅展开，INTERNAL 站内跳转，EXTERNAL 发起 SSO。GET `/api/me/apps` 是工作台的已授权应用；`/api/me/menus` 为兼容平级结果。

```http
GET /api/sso/authorize?appCode=ai-recruitment&entry=ADMIN&redirectPath=/jobs
Authorization: Bearer <user-access-token>
```

返回 `{code,expiresIn:60,redirectUrl}`。redirectUrl 使用对应端的登记地址和回调路径，携带 code、appCode、entry。前端遵守 CURRENT_TAB 或 NEW_TAB/EXTERNAL 打开方式。

目标应用的回调后端立即交换：

```http
POST /api/sso/token/exchange
X-App-Secret: <application-client-secret>
Content-Type: application/json

{"code":"<one-time-code>","appCode":"ai-recruitment","entry":"ADMIN"}
```

交换码绑定应用和端，60 秒且单次；服务端消费时重新检查员工当前页面权限、应用状态和灰度。成功返回 ApiResponse.data：`{subjectId,appCode,redirectPath,expiresAt,entry,permissions}`。应用验证结果后建立本地会话，不接受浏览器自行声明的 subjectId 或 permissions。交换结果是登录时快照，不是永久授权凭证。

## 4 每个业务 API 持续校验权限

服务端从自身已验证会话取得 subjectId，使用独立服务凭证调用：

```http
POST /api/app-auth/check
X-App-Code: ai-recruitment
X-App-Secret: <application-client-secret>
Content-Type: application/json

{"subjectId":"123","entry":"ADMIN","permissionCode":"app:ai-recruitment:admin:jobs:view"}
```

ApiResponse.data 为 `{appCode,subjectId,entry,permissionCode,allowed,checkedAt}`。仅 allowed=true 才放行业务；网络、超时、业务错误均应拒绝。针对新增、导出、删除等敏感动作分别登记 ACTION/API 权限并检查，不能只验证页面 view 权限。

POST `/api/app-auth/permissions` 使用相同头，body `{subjectId,entry}`，返回 `{appCode,subjectId,entry,allow,deny,effective,checkedAt}`，只含当前应用当前端 ACTIVE 资源。DENY 覆盖 ALLOW；员工停用或权限撤回后后续判定不允许，应用停用后服务凭证调用拒绝。

visible 与灰度属于菜单/页面入口展示维度，权限接口仍可检查隐藏页面及 ACTION/API。权限缓存若由目标应用自行引入，撤销延迟也由该应用承担；本平台未实现对所有外部应用本地会话的统一注销或推送撤销。数据范围对业务行的过滤仍是未完成项，不能用本接口的功能权限替代行级控制。
