# api-gateway

[仓库说明](../README.md) · [模块规格](SPEC.md) · [完整 API / 转发规则](API.md) · [API 总览](../docs/api-reference.md) · [文档导航](../docs/README.md)

## 职责与入口

为浏览器 REST 提供统一转发、路径映射和部分前缀的 JWT 校验。不拥有业务数据库，不执行影像推理、报告生成或完整医院资源授权。

默认 HTTP：`http://localhost:8088`，可用 `PORT` 覆盖。完整自身端点、方法、头/body/响应处理、错误及下游 API 链接见 [API.md](API.md)，不是 Swagger 页面。

## 前提与配置

需要 Java 21、根目录 Gradle Wrapper 及可下载依赖的构建环境。网关自身不连接数据库；要实际转发业务，还须准备相应下游及其数据库，见 [auth](../auth-service/README.md)、[patient](../patient-service/README.md)、[metric](../metric-service/README.md)。

配置见 [application.conf](src/main/resources/application.conf)和 [ServiceConfig](src/main/kotlin/ServiceConfig.kt)：

| 环境变量 | 默认下游 | 固定入口前缀 / 路径映射 |
| --- | --- | --- |
| `AUTH_SERVICE_URL` | `http://localhost:8081` | `/api/auth`，剥去前缀 |
| `PATIENT_SERVICE_URL` | `http://localhost:8082` | `/patients`，下游仍保留 `/patients` |
| `METRIC_SERVICE_URL` | `http://localhost:8083` | `/metric`，剥去前缀 |
| `REPORT_SERVICE_URL` | `http://localhost:8084` | `/report`，剥去前缀；占位目标 |

- `PORT` 默认 `8088`；`JWT_SECRET` 应自行提供并与 auth 等服务一致，`jwt.issuer`、`jwt.audience` 也须匹配。不要沿用开发密钥或记录真实 token。
- `/api/auth` 在网关不验 JWT，由 auth 区分公开/受保护操作；`/patients`、`/metric`、`/report` 在网关均要求 JWT。验签不检查角色、医院/患者归属或数据库账号状态。
- 仓库没有独立 report 模块；默认 8084 不代表服务存在，也不是 metric 别名。[Compose](../docker-compose.yml)当前将 `REPORT_SERVICE_URL` 设为 `http://metric-service:8083`，与本机默认不同，不能据此声称 `/report` 契约已验收。metric 报告业务按其 API 经 `/metric` 访问。
- 四个入口前缀在路由中写死，修改配置的 pathPrefix 不会自动重建路由。跨容器需使用可达的下游地址，不能把容器内 localhost 当作其他服务；gateway 的 `AUTH_SERVICE_URL` 也不会配置 patient 的 AuthClient。

## 根目录 Gradle 命令

以下命令仅示意，按需单独执行；本次文档工作未执行。

```bash
# 启动网关
./gradlew :api-gateway:run
# 生成包含依赖和启动脚本的分发目录（不启动服务）
./gradlew :api-gateway:installDist
# 本模块测试任务
./gradlew :api-gateway:test
```

`installDist` 产物位于 `api-gateway/build/install/api-gateway/`，任务及入口见 [build.gradle.kts](build.gradle.kts)。测试任务存在不代表已经覆盖所有转发行为。

## 已知限制与验证范围

- 仅注册 GET、POST、PUT、DELETE 代理；没有 WebSocket 代理，通知及 AI WS 须分别直连 auth/metric。CORS 允许 PATCH 不等于已有 PATCH 业务路由。
- **DELETE body 会丢失**，auth/patient 的批量删除因此不能可靠经网关调用；请求/响应不是统一 DTO 封装，字段与业务状态仍以下游 API 为准。
- `/health` 只表示网关存活，不检查下游或数据库；`/metrics` 返回固定文本，**不是 Prometheus scrape 数据**。
- 公开 `/test-jwt` 不验证 JWT，却记录完整 Authorization 头，并拟回显前 50 字符，存在认证信息泄露风险；勿发送真实凭据，也不能用于生产认证检查。其异构 Map 序列化未运行验收。
- 响应全量读入内存；压缩响应处理、文件下载、开发期宽松 CORS、跨容器/部署前缀仍需验证。不能把网关验签或健康检查当成全链路安全/可用证明。

本次仅静态核对规格、API、源码、构建与资源配置，并校对文档链接及命令语法；未执行 Gradle 构建/测试、启动服务或数据库，未做转发、WS、授权、压缩/下载或部署验收，**尚未验收生产可用性**。边界见[目标规格](../goal-and-requirements.md)、[架构](../architecture.md)及[医院 AI 政策](../docs/hospital-ai-boundary-policy.md)。
