# patient-service

[仓库说明](../README.md) · [模块规格](SPEC.md) · [完整 API](API.md) · [API 总览](../docs/api-reference.md) · [文档导航](../docs/README.md)

## 职责与入口

负责患者档案、查询与统计，以及状态、就诊、转移、生命体征和医疗信息操作。通过 AuthClient 向 auth 查询用户、角色和医生姓名；不执行影像推理或生成报告。

- 默认 HTTP：`http://localhost:8082`，可用 `PORT` 覆盖；无 WebSocket 路由。
- 默认网关：`http://localhost:8088`，患者路径仍为 `/patients/...`，不要重复添加前缀。
- 除直连 `GET /health` 外均需 JWT；完整路由、字段、过滤规则和错误见 [API.md](API.md)，不是 Swagger 页面。

## 前提与配置

需要 Java 21、根目录 Gradle Wrapper、可下载依赖的构建环境及 MySQL/TiDB（MySQL 协议）。创建/修改等角色检查和姓名补充还依赖 [auth-service](../auth-service/README.md)。配置入口为 [application.conf](src/main/resources/application.conf)；不要沿用开发凭据。

| 环境变量 | 默认或要求 |
| --- | --- |
| `PORT` | `8082` |
| `PATIENT_DATABASE_URL` | `jdbc:mysql://localhost:4000/patient?useInformationSchema=false` |
| `PATIENT_DATABASE_USER` | 自行提供数据库用户名 |
| `PATIENT_DATABASE_PASSWORD` | 自行提供数据库密码，不写入文档或日志 |
| `PATIENT_DATABASE_DRIVER` | `com.mysql.cj.jdbc.Driver` |
| `JWT_SECRET` | 自行提供密钥，与 auth 等服务一致；`jwt.issuer`、`jwt.audience` 也须匹配 |

**AuthClient 地址限制：**[当前路由](src/main/kotlin/Routing.kt)直接构造 `AuthClient()`，其[默认地址](src/main/kotlin/service/AuthClient.kt)硬编码为 `http://localhost:8081`，没有读取 `AUTH_SERVICE_URL`。修改 gateway 的同名环境变量不会改变 patient → auth 调用；跨容器部署或改 auth 端口不能据此认为已适配。auth 查询失败可能导致权限判断失败，医生姓名也可能回退为医生 ID。

**启动有数据库副作用：**[数据库初始化](src/main/kotlin/Databases.kt)将 MySQL JDBC 参数规范化为 `useInformationSchema=false`，执行 `CREATE DATABASE IF NOT EXISTS`，再补建患者表的缺表/缺列。须预先运行数据库服务并提供相应建库/DDL 权限；应用不会启动数据库。这是开发期 MySQL/TiDB bootstrap，不是版本化生产迁移，勿直接指向生产库。H2 仅用于[健康检查测试](src/test/kotlin/ApplicationTest.kt)，不是默认或生产数据库。

## 根目录 Gradle 命令

以下命令仅示意，按需单独执行；本次文档工作未执行。`run` 会触发上述初始化。

```bash
# 启动本模块
./gradlew :patient-service:run
# 生成包含依赖和启动脚本的分发目录（不启动服务）
./gradlew :patient-service:installDist
# 本模块测试任务
./gradlew :patient-service:test
```

`installDist` 产物位于 `patient-service/build/install/patient-service/`；任务及入口见 [build.gradle.kts](build.gradle.kts)。现有 H2 健康检查测试不等于 MySQL/TiDB、auth 联调或完整患者业务验收。

## 已知限制与验证范围

- 创建、通用更新要求 JWT role 和 auth 返回角色均为 `admin/receptionist`；状态更新、转移由 service 查询 auth 后要求同样角色。**不是“主治医生即可修改”**。
- 详情、列表、搜索、统计仅 JWT；删除、批量删除及 visit/vitals/medical 修改也没有额外角色/医院范围检查。已有角色检查仍不核对操作者医院，不能宣称完整医院隔离。
- 通用列表有过滤优先级，部分筛选未使用；软删除的读取行为也不一致。以 [API 已知缺口](API.md) 为准，不能由 shared DTO 或前端声明推断路由存在。
- 批量 DELETE 依赖 body，而网关会丢弃该 body。直连 `/health` 会查询数据库，但不验证 auth；网关 `/health` 不是本服务健康检查。

本次仅静态核对规格、API、源码、构建与资源配置，并校对文档链接及命令语法；未执行 Gradle 构建/测试、启动服务或数据库，未验收跨院授权或端到端部署。边界见[目标规格](../goal-and-requirements.md)、[架构](../architecture.md)及[医院 AI 政策](../docs/hospital-ai-boundary-policy.md)。
