# shared

[仓库说明](../README.md) · [模块规格](SPEC.md) · [API 总览](../docs/api-reference.md) · [文档导航](../docs/README.md)

## 职责：库，不是服务

四个 Kotlin 服务编译依赖本模块，复用以下契约与工具：

- [DTO](src/main/kotlin/dto)：用户、医院、科室、患者、影像、指标、分析、报告、通知/WS 和响应类型。
- [枚举](src/main/kotlin/enums)及[异常](src/main/kotlin/exception)：患者性别/血型/状态、影像类型和业务错误类型；HTTP 映射由各服务负责。
- [JwtUtil](src/main/kotlin/utils/JwtUtil.kt)：JWT 签发、验签、声明读取及刷新辅助。
- [MySqlDatabaseBootstrap](src/main/kotlin/utils/MySqlDatabaseBootstrap.kt)：JDBC URL 处理、TiDB 参数兼容及 MySQL 建库辅助。

本模块没有 main 或监听端口，不提供 `run`、`installDist`、`buildFatJar` 或 Docker 服务入口。[资源配置](src/main/resources/application.conf)中的遗留模板不能作为可启动服务的依据。DTO 的存在也**不意味着已注册对应 HTTP 路由**；完整接口见 [auth API](../auth-service/API.md)、[patient API](../patient-service/API.md)、[metric API](../metric-service/API.md)及[网关规则](../api-gateway/API.md)，本库没有独立 HTTP API 或 Swagger 页面。

## 契约注意事项

- **BREAKING CHANGE：`GeneralMetric.confidence` 为 `Double? = null`。** 无校准置信度时保留 null/缺省，消费者必须兼容，不能填成 0 或固定百分数；其他模态指标的 confidence 字段未因此统一改为可空。科研结果不自动获得临床意义。
- 多态判别键由消费者的 JSON 配置决定，不是 shared 全局设定：metric 工具 payload、写入解码和持久化使用 `kind`，普通 GET 的默认 JSON 使用 `type`，标签如 `dto.MetricDto.GeneralMetric`。GET 结果不能未经递归适配直接回 POST，详见 [metric API 的序列化约定](../metric-service/API.md)。
- 业务 DTO 的日期多为 `String`，没有全局时区或统一日期格式承诺；不要假设都是带 `Z` 的 UTC。默认值、null 是否输出也由各服务的序列化配置决定。
- Python 分割 schema 是独立 HTTP 契约，不会随 Kotlin DTO 自动更新；修改共享字段须同步检查服务、前端及跨语言消费者。

## 配置与数据库副作用

`JwtUtil` 接收宿主传入的 `ApplicationConfig`，读取 `jwt.secret`、`jwt.expiration`、`jwt.audience`、`jwt.issuer` 及可选 `jwt.realm`；不会自行建立完整授权系统。宿主服务通过 `JWT_SECRET` 等配置映射注入密钥，签发/验证服务的密钥、issuer、audience 须一致。验签不等于账号状态检查、撤销机制或医院资源授权；不应把真实密钥或 token 写进文档/日志。

数据库工具自身不拥有业务表，只有调用时才产生副作用：MySQL URL 规范化为 `useInformationSchema=false`，`ensureMySqlDatabaseExists` 通过 JDBC 执行 `CREATE DATABASE IF NOT EXISTS`。auth/patient/metric 启动随后用 Exposed 补表/列；这不是 MySQL/TiDB 的生产迁移方案，也不授予跨服务访问其他表的权限。

宿主默认数据库地址为 `localhost:4000`，auth 库为 `med_auth`，patient 库为 `patient`，metric 库为 `analysis`；分别通过 `AUTH_DATABASE_*`、`PATIENT_DATABASE_*`、`METRIC_DATABASE_*` 配置（`*` 为 `URL`、`USER`、`PASSWORD`、`DRIVER`），不是本库独立的环境开关。实际启动前提及权限见 [auth README](../auth-service/README.md)、[patient README](../patient-service/README.md)和 [metric README](../metric-service/README.md)。H2 仅用于测试，不是生产或默认数据库方案。

## 根目录构建与测试

需要 Java 21、根目录 Gradle Wrapper 及可下载依赖的构建环境；任务依据 [build.gradle.kts](build.gradle.kts)。以下命令仅示意，本次未执行，无需为现有 URL 工具单元测试启动真实数据库或业务服务。

```bash
# 构建库（含测试检查）
./gradlew :shared:build
# 单独运行本模块测试任务
./gradlew :shared:test
```

## 验证范围

本次仅静态核对规格、DTO/枚举/工具、构建配置和消费者序列化，并校对链接与命令语法；未运行构建、测试、服务或数据库。[现有测试](src/test/kotlin/utils/MySqlDatabaseBootstrapTest.kt)覆盖 URL 处理，不代表真实建库、JWT 安全或所有 DTO 兼容性已验收。边界见[目标规格](../goal-and-requirements.md)、[架构](../architecture.md)及[医院 AI 政策](../docs/hospital-ai-boundary-policy.md)。
