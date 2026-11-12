# auth-service

[仓库说明](../README.md) · [模块规格](SPEC.md) · [完整 API](API.md) · [API 总览](../docs/api-reference.md) · [文档导航](../docs/README.md)

## 职责与入口

负责账号注册、登录和 JWT 刷新，用户、医院、科室管理，以及进程内通知。业务经 Ktor 路由、service、repository 持久化；不管理患者临床记录，不执行影像分析或报告生成。

- 默认 HTTP：`http://localhost:8081`，可用 `PORT` 覆盖。
- 默认网关 REST 前缀：`http://localhost:8088/api/auth`，网关剥去 `/api/auth` 后转发。
- 通知 WebSocket：直连 `ws://localhost:8081/ws/notifications`；网关不代理 WS。
- 路由、请求/响应、状态码及通知协议统一见 [API.md](API.md)，不是 Swagger 页面。

## 前提与配置

需要 Java 21、根目录 Gradle Wrapper、可下载依赖的构建环境，以及可访问的 MySQL/TiDB（MySQL 协议）。配置入口为 [application.conf](src/main/resources/application.conf)；不要沿用其中的开发凭据。

| 环境变量 | 默认或要求 |
| --- | --- |
| `PORT` | `8081` |
| `AUTH_DATABASE_URL` | `jdbc:mysql://localhost:4000/med_auth?useInformationSchema=false` |
| `AUTH_DATABASE_USER` | 自行提供数据库用户名 |
| `AUTH_DATABASE_PASSWORD` | 自行提供数据库密码，不写入文档或日志 |
| `AUTH_DATABASE_DRIVER` | `com.mysql.cj.jdbc.Driver` |
| `JWT_SECRET` | 自行提供密钥，与验证该 token 的其他服务一致 |

`jwt.issuer`、`jwt.audience` 等配置也须与 patient、metric、gateway 对齐；只有密钥的环境覆盖在此列出，不代表其他 JWT 配置都有同名环境变量。

**启动有数据库副作用：**[数据库初始化](src/main/kotlin/Databases.kt)会规范化 JDBC 参数为 `useInformationSchema=false`，对 MySQL URL 执行 `CREATE DATABASE IF NOT EXISTS`，再为医院、科室、用户补建缺失的表和列。数据库服务必须已运行，账号须具备相应建库/DDL 权限；应用不会安装或启动数据库。这是开发期 MySQL/TiDB bootstrap，不是版本化生产迁移；勿直接指向生产库。H2 仅用于测试，不是本服务默认数据库。

## 根目录 Gradle 命令

以下命令仅示意，按需单独执行；本次文档工作未执行。`run` 会触发上述初始化。

```bash
# 启动本模块
./gradlew :auth-service:run
# 生成包含依赖和启动脚本的分发目录（不启动服务）
./gradlew :auth-service:installDist
# 本模块测试任务
./gradlew :auth-service:test
```

`installDist` 产物位于 `auth-service/build/install/auth-service/`。任务及入口以 [build.gradle.kts](build.gradle.kts) 为准；测试任务存在不等于已有完整业务/安全测试。

## 已知限制与验证范围

- 不是完善 RBAC：公开注册接受 `admin`；通用用户修改、他人密码重置及删除等操作部分仅要求 JWT。专用管理接口的管理员检查不能覆盖这些缺口，详见 [API 的实际认证边界](API.md)。
- 密码当前使用 SHA-256 摘要；JWT 校验不逐请求核对账号冻结/删除状态，也不提供完整医院隔离。
- 通知连接和公告历史只在当前进程内，重启丢失、无医院分区；发送成功不代表送达。批量 DELETE 的 body 经网关会丢失。
- `/health` 只返回存活状态，不检查数据库。当前没有在本次工作中验收启动、数据库、通知或端到端授权。

本次仅静态核对规格、API、源码、构建与资源配置，并校对文档链接及命令语法；未执行 Gradle 构建/测试、启动服务或数据库，不构成医院生产验收。边界见[目标规格](../goal-and-requirements.md)、[架构](../architecture.md)及[医院 AI 政策](../docs/hospital-ai-boundary-policy.md)。
