# Frontend Foundation Refactor Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 将当前前端原型整理为可扩展的前端基础架构，不实现业务页面、RBAC 或动态菜单。

**Architecture:** 以 Arco Design 作为后台基础组件，Tailwind/CSS Variables 负责布局与主题，React Router 负责页面路由；菜单、主题和应用配置通过独立配置模块提供，后续可无痛替换为后端数据。全局 Provider 统一承载 Arco、Query 和路由上下文。

**Tech Stack:** React 19, TypeScript, Vite, React Router 7, Arco Design, Tailwind CSS, TanStack Query, Axios, Lucide.

**Spec:** 用户确认“先完成基础架构重构，用户/权限后续设计，其他基础能力由助手完成”。

## Global Constraints

- 保留 React 19 + TypeScript + Vite 现有技术栈。
- 不在本阶段定义用户、角色、权限数据模型或后端权限接口。
- 菜单和主题先使用前端本地配置，并保留未来接入 API 的边界。
- 不提交 `dist/`、`node_modules/` 或其他生成物。
- 遵守仓库约定：TypeScript/TSX/CSS 使用 2 空格缩进。

## Review Focus

- 刷新任意路由时，应用仍能由 Vite 正常加载并显示对应页面。
- 菜单配置与路由配置不一致时，不应出现死链接或运行时异常。
- 主题配置缺失或颜色值不完整时，应回退到默认主题。
- Axios API 错误响应应保留后端 `code/error_type/error_message`，不被普通异常覆盖。
- 移动端展开侧栏后切换路由，侧栏应自动关闭。

### Task 1: 应用配置与主题基础

**Files:**
- Create: `seeex-tech-ui/src/config/app.ts`
- Create: `seeex-tech-ui/src/config/theme.ts`
- Create: `seeex-tech-ui/src/styles/theme.css`
- Modify: `seeex-tech-ui/src/index.css`

- [ ] 定义 `AppConfig`、`ThemeConfig` 类型和默认配置。
- [ ] 将主题颜色映射为 CSS Variables，并提供本地主题读取/保存函数。
- [ ] 保留当前主题切换能力，缺失配置回退默认值。

### Task 2: API 层与全局 Providers

**Files:**
- Modify: `seeex-tech-ui/src/lib/api.ts`
- Create: `seeex-tech-ui/src/app/AppProviders.tsx`
- Modify: `seeex-tech-ui/src/main.tsx`

- [ ] 为 Axios 增加请求 ID、鉴权 token 预留和统一响应错误转换。
- [ ] 创建 `QueryClientProvider`、`ConfigProvider` 和路由 Provider 的组合入口。
- [ ] 不实现用户登录或权限判断，只保留扩展接口。

### Task 3: 路由与布局基础接口

**Files:**
- Create: `seeex-tech-ui/src/routes/router.tsx`
- Create: `seeex-tech-ui/src/layouts/AdminLayout.tsx`
- Create: `seeex-tech-ui/src/layouts/Header.tsx`
- Create: `seeex-tech-ui/src/layouts/Sidebar.tsx`
- Create: `seeex-tech-ui/src/components/PageContainer.tsx`
- Modify: `seeex-tech-ui/src/App.tsx`

- [ ] 使用 `createBrowserRouter` 建立可扩展的路由入口和最小根路由，不实现业务页面。
- [ ] 将侧栏、头部的 Props/回调接口从 `App.tsx` 拆出，但不填充业务菜单。
- [ ] 为未来菜单和权限预留类型边界，不实现菜单获取、RBAC 或权限判断。

### Task 4: 基础组件与样式归档

**Files:**
- Create: `seeex-tech-ui/src/components/PageContainer.tsx`
- Create: `seeex-tech-ui/src/components/RoutePlaceholder.tsx`
- Modify: `seeex-tech-ui/src/App.css`

- [ ] 只提供通用页面容器和路由占位组件，不开发仪表盘、用户、分析或设置页面。
- [ ] 使用 Arco 的 Layout/ConfigProvider 基础能力，Tailwind 仅用于布局和补充样式。
- [ ] 不创建业务菜单配置；未来由 RBAC + 动态菜单设计确定后再添加。

### Task 5: 验证与文档

**Files:**
- Modify: `seeex-tech-ui/README.md`（如存在）

- [ ] 运行 `npm run lint`。
- [ ] 运行 `npm run build`。
- [ ] 验证 Providers、路由入口、主题变量和 API 层可以独立构建。
- [ ] 记录后续接入 RBAC、动态菜单和业务页面的扩展位置。
