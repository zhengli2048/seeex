# Seeex 认证与基础应用平台

前后端与设计文档的统一工作区。平台包含认证、组织/职级、角色/资源、权限策略与数据范围、应用注册及Web/后台入口、动态导航、文件中心、企业微信同步和审计。

- `seeex-tech-backend/`：Java 21 / Spring Boot 4 / MyBatis-Plus / MySQL，多模块后端。
- `seeex-tech-ui/`：React / TypeScript / Arco Design（字节），管理控制台。
- `docs/`：按钮级页面清单、接口契约、方案、实施记录与静态审查。

当前状态与限制见 [平台审查](docs/design/13-platform-status-and-code-review.md) 和 [缺陷修复记录](docs/design/14-platform-defect-remediation.md)；页面清单见 [实施清单](docs/plans/seeex-console-implementation-checklist.md)。审批流、新应用申请、办理和定向交付尚处方案阶段，不能视为已上线功能。

本轮遵照用户要求仅修改源码并静态检查，没有编译、类型检查、运行测试、服务启动、数据库迁移或真实接口调用。V24发布前需停旧同步消费者，前后端同批发布，迁移步骤见修复记录。配置通过环境变量注入，不提交本地环境、密钥、IDE文件或构建产物。

后端工程约束见 `seeex-tech-backend/AGENTS.md`（与 `CLAUDE.md` 同步）；其中常规运行检查在本轮受用户“不运行”要求覆盖，未声称通过。
