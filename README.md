# med-collab-system

医疗协作与医学影像科研验证 monorepo：Kotlin/Ktor 业务服务、React 客户端与独立 Python 分割服务。当前用于原型/课程研究，**不是已通过医院部署、患者独立验证或临床有效性验收的产品**。

- [文档导航](docs/README.md) · [全部 API 参考](docs/api-reference.md)
- [分割与训练操作手册](docs/segmentation-service-operation-manual.md) · [医院 AI 职责边界](docs/hospital-ai-boundary-policy.md)
- [产品目标](goal-and-requirements.md) · [架构规格](architecture.md) · [UNet 封存实验与限制](segmentation-service/app/training/SPEC.md)

## 当前实现，不要与目标混淆

- auth 管理账号、医院/科室和进程内通知；patient 管理患者；metric 提供影像/分析/报告记录及会话文件。
- 影像请求执行 `executeControlledMetricReportPipeline`，**不运行 Koog 策略图**。文本分支只是固定边界说明，不是已实现的多轮 LLM 或基于历史证据的报告问答。
- metric 默认 `METRIC_SEGMENTATION_SERVICE_ENABLED=false`，使用旧本地启发式。该分支仍可能生成正式样式 PDF，但灰度、常量间距、血流/风险等派生值不构成有效医学测量。
- 显式开启远程分割后才调用 Python。失败、LIMITED、mock、未加载权重等结果不进入受控报告，也不由 LLM 补结论。
- 课程包仅支持 `research` / `ULTRASOUND`，返回像素量与未经临床校准的前景概率，生成明确非诊断的科研 PDF；无标定毫米或固定临床置信度。
- **生成 PDF、用户确认分析、登记报告是不同步骤**。PDF 通常已在确认前生成；浏览器随后分别调用分析/报告持久化 API。没有跨步骤事务、可靠幂等、执行恢复或医生资质签署证明。

职责原则由[医院 AI 政策](docs/hospital-ai-boundary-policy.md)统一规定；现有 JWT、结构化结果、质量 PASS、权重加载和 CI 成功都不代表完整医院授权或医学正确性。

## 模块与默认端口

| 模块 | 技术 / 职责 | 本地默认端口 | 开发说明 |
|---|---|---:|---|
| auth-service | Kotlin 2.2 / Ktor 3.3，身份、组织、通知 | 8081 | [README](auth-service/README.md) |
| patient-service | Kotlin/Ktor，患者业务 | 8082 | [README](patient-service/README.md) |
| metric-service | Kotlin/Ktor，受控分析、PDF及业务登记 | 8083 | [README](metric-service/README.md) |
| api-gateway | Kotlin/Ktor，HTTP 转发 | 8088 | [README](api-gateway/README.md) |
| segmentation-service | Python/FastAPI/PyTorch，分割及课程训练 | 8099 | [README](segmentation-service/README.md) |
| frontend | React 19 / Vite 8 客户端 | 5173（全栈脚本） | [README](frontend/README.md) |
| shared | DTO、枚举、JWT及数据库工具库 | 无 | [README](shared/README.md) |

浏览器 REST 通常走网关，metric 客户端存在直连回退；通知/AI WebSocket 直连 auth/metric，**Ktor 网关没有 WS 代理**。详细地址、前缀和实际鉴权见 [API 索引](docs/api-reference.md)。根目录的旧 Node 依赖不代表客户端技术栈；前端以 `frontend/package.json` 为准，不是 Vue。

## 本地运行前提

- **Java 21**；仓库 Gradle Wrapper 为 9.2.1。
- 建议 **Node 22.x**（已有验证使用 22.22.0）及 npm，按 `frontend/package-lock.json` 安装。
- Python **3.11+**（若启动分割服务）；训练/绘图需 `training` extra，推理需 `unet` extra。
- 已运行且可访问的 MySQL 协议数据库（如 MySQL/TiDB），以及本地私密配置。默认 JDBC 指向 `localhost:4000`，库名为 `med_auth`、`patient`、`analysis`，并非默认 3306。

启动脚本**不启动数据库服务器、不自动加载 `.env.docker`**。业务应用启动会尝试 `CREATE DATABASE IF NOT EXISTS` 和补建表/列，需要相应权限，不能当作只读检查或正式数据库迁移方案。patient 当前 AuthClient 仍固定访问 `localhost:8081`，跨容器/跨主机拓扑未整体适配。

配置变量入口：

| 范围 | 变量 |
|---|---|
| 三个业务数据库 | `AUTH_DATABASE_*`、`PATIENT_DATABASE_*`、`METRIC_DATABASE_*`：各自 `URL` / `USER` / `PASSWORD` / `DRIVER` |
| JWT | `JWT_SECRET`：Kotlin 服务使用一致的私密值，不使用源码开发默认值 |
| 网关下游 | `AUTH_SERVICE_URL`、`PATIENT_SERVICE_URL`、`METRIC_SERVICE_URL`、`REPORT_SERVICE_URL` |
| metric → Python | `METRIC_SEGMENTATION_SERVICE_ENABLED` / `URL` / `TIMEOUT_SECONDS` |
| 前端构建期地址 | `VITE_*`，见 [frontend README](frontend/README.md) |

请在私密本地环境中配置口令，不将真实值写入文档、Git、请求日志或截图。全栈脚本继承环境；不要全局设置同一个 `PORT` 后期待四个后端仍分别使用默认端口。各服务 `application.conf` 和模块说明是具体配置入口。

## 一键启动：开发/演示，不是科研模型就绪证明

从仓库根目录运行：

```bash
scripts/quick-start.sh
```

脚本会：

1. 在 frontend 执行锁定依赖安装和 build。
2. 构建四个 Kotlin `installDist`，**显式 `-x test`**。
3. 准备 Python 虚拟环境，按后端安装基础或 `.[unet]` 依赖。
4. 先启动业务服务，再启动 Python，最后启动 Vite preview。
5. 将日志写到 `.run/logs/`；当前进程持续监控，Ctrl+C 清理它启动的子进程。

端口被占用会失败，请先确认占用者，不要盲目杀进程。脚本的 TCP/日志探活不代表数据库业务、模型推理或完整流程通过。

> 网络提示：全栈脚本将 Vite preview/dev 的监听覆盖为 **`0.0.0.0`**，默认端口 5173；其他监听也需按部署配置核对。请仅在隔离/可信开发环境使用公开或合成数据，不直接在不可信网络处理真实病历。Vite preview 不是生产服务器。

常用方式：

```bash
# 不启动Python：metric默认仍是旧本地启发式，不是课程UNet。
scripts/quick-start.sh --without-segmentation

# 显式mock只适合Python接口演示；不能通过metric的已加载权重/非mock门禁。
scripts/quick-start.sh --segmentation-backend mock

# 默认也是torch_unet；选择后端不等于已有训练权重。
scripts/quick-start.sh --segmentation-backend torch_unet

scripts/quick-start.sh --frontend-mode dev
scripts/quick-start.sh --skip-build
scripts/quick-start.sh --segmentation-python /path/to/python3.11
scripts/quick-start.sh --help
```

`--skip-build` 要求已有后端发行物、前端 dist，以及可用的 Python venv；不会校验这些产物是否匹配当前源码。独立 `segmentation-service/scripts/run-dev.sh` 的行为不同：会尝试停止同 host/port 的匹配旧 reload 进程并可能强制结束，缺依赖时自动安装 Torch extra；详见[操作手册](docs/segmentation-service-operation-manual.md)。

### 全栈脚本的环境变量

| 变量 | 默认值 / 用途 |
|---|---|
| `CLIENT_PORT` | 5173 |
| `SEGMENTATION_HOST` / `SEGMENTATION_PORT` | 127.0.0.1 / 8099 |
| `SEGMENTATION_ENV` | local |
| `SEGMENTATION_BACKEND` | torch_unet |
| `SEGMENTATION_PYTHON_BIN` | 依次选指定解释器、python3.11、python3，要求版本≥3.11 |
| `SEGMENTATION_VENV_DIR` | segmentation-service/.venv |
| `METRIC_SEGMENTATION_SERVICE_ENABLED` | false，启动Python本身不打开它 |
| `METRIC_SEGMENTATION_SERVICE_URL` | http://127.0.0.1:8099 |
| `METRIC_SEGMENTATION_SERVICE_TIMEOUT_SECONDS` | 30；连接/请求超时，不等整条执行取消 |

脚本的 `SEGMENTATION_*` 会显式转换为对应 Python `SEGMENTATION_SERVICE_*` 环境变量；实际监听以 Uvicorn CLI host/port 为准。更多模型参数见[模块说明](segmentation-service/README.md)。

## 显式使用课程科研模型包

先在本地准备完整、可信的导出目录：`manifest.json` 和其引用的哈希权重文件必须同时存在。Git 不包含训练数据或权重；不能只复制 manifest，也不能把随机前向/mock当作真实模型。

以下是**部署命令模板**，需先将示例路径替换为自己的有效包；不下载模型、不训练：

```bash
# 在仓库根目录；此路径仅作占位，不能直接执行为成功部署。
export SEGMENTATION_SERVICE_MODEL_MANIFEST_PATH="$PWD/segmentation-service/var/models/YOUR_BUNDLE/manifest.json"
export SEGMENTATION_SERVICE_DEVICE=mps
export SEGMENTATION_SERVICE_MIN_REGION_PIXELS=1024
export METRIC_SEGMENTATION_SERVICE_ENABLED=true
export METRIC_SEGMENTATION_SERVICE_URL=http://127.0.0.1:8099
scripts/quick-start.sh --segmentation-backend torch_unet
```

- `mps` 适用于已支持的 Apple 设备；服务不可用设备会回 CPU，应查看实际模型描述，不把环境请求值当证据。CPU 可显式设 `cpu`。
- **1024 是本次 BUSI val 选定的原图像素阈值，不是通用默认**；研究包无显式覆盖为16，旧local为24、test为8。仅复现该冻结实验时使用1024，不可根据已见test再次调参。
- bundle 强制 research、ULTRASOUND、Torch、单输入/输出、包内变换和概率阈值，禁止随机/mock回退；模型/权重哈希只验证完整性，不赋予临床或许可资格。
- 从 Python `/api/v1/models/current` 核对 `weights_loaded`、`intended_use`、`supported_image_types`、包摘要和实际device；`/health` 不能替代它。即使这些正确，也需用有权读取的公开验证图核对真实链路，而不是重新刷封存测试集。
- 本机路径不是浏览器上传协议。metric 与 Python 分离时需要明确共享文件/挂载或可解码的data URL约定，不能把服务端路径当下载URL。

完整配置、安装、prepare/train/evaluate及排错见[操作手册](docs/segmentation-service-operation-manual.md)；实验选择与已知误检见[训练规格](segmentation-service/app/training/SPEC.md)。科研PDF保留非诊断用途，不制造毫米、血流、良恶性、风险或固定临床置信度。

## 探活与测试

| 检查 | 实际证明范围 |
|---|---|
| auth `/health` | 简单存活响应，不检查数据库 |
| gateway `/health` | 网关存活，不检查下游 |
| patient `/health` | 读取患者表计数，不检查完整授权 |
| metric `/health` | 数据库 SELECT 1，不验证分割模型 |
| Python `/health` | 原始配置/存活，不加载模型 |
| Python `/api/v1/models/current` | 解析模型依赖/加载信息，不验证准确率 |

示例（服务启动后）：

```bash
curl -fsS http://localhost:8083/health
curl -fsS http://127.0.0.1:8099/health
curl -fsS http://127.0.0.1:8099/api/v1/models/current
```

测试与构建是独立门禁：

```bash
./gradlew test
npm --prefix frontend ci --include=optional
npm --prefix frontend run build
```

Python测试需先装 `.[dev,training]`，核心训练/包用例不能因缺Torch被跳过；命令与JUnit门禁见[分割说明](segmentation-service/README.md)。auth/api-gateway当前无测试源码，不能把Gradle NO-SOURCE算覆盖；真实模型集成测试默认opt-in，需本地包和公开验证图，见[metric说明](metric-service/README.md)。构建成功不等于完整浏览器、业务登记或医院验收。

日志位于 `.run/logs/{auth-service,patient-service,metric-service,api-gateway,segmentation-service}.log`，前端为 `frontend-preview.log` 或 `frontend-dev.log`。日志可能含身份、路径或调试认证信息，不能作为已脱敏的医院审计。

## Docker 与生产限制

[Compose](docker-compose.yml)是既有开发配置，不是开箱即用的科研/医院部署：依赖外部TiDB网络/主机，仅Nginx发布端口；Dockerfile还引用需另行准备的 `docker/gradle-dists`。Compose默认Python为mock、metric远程为true，与本地脚本不同，且未准备课程包/权重共享挂载。patient对auth的本机固定地址、前端构建期origin、部署前缀和文件共享均需单独处理。

现有配置存在明文数据库口令、无显式非root/HEALTHCHECK等缺口；Docker CI job也不是实际镜像构建验收。不要仅凭 `docker compose up` 命令存在或CI条件跳过宣称容器通过。

完整医院授权/跨院隔离、输入与资源上界、日志/凭据治理、可靠取消恢复/登记、性能、外部兼容和临床验证尚未完成。旧设计材料按[现状对照](docs/unet-ai-chain-integration-plan.md)阅读；12个规格仍draft，等待人工审阅。原始数据、模型、图表、PDF及本地验收记录在忽略目录，清理工作区前需另行备份，不提交到Git或PR附件。
