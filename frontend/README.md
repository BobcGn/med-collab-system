# frontend：React 客户端

当前客户端是 **React 19 + Vite 8**，入口为 `src/main.jsx`，没有使用Vue或Vue Router。工具链以本目录 `package.json` / `package-lock.json` 为准；根package只代理脚本，历史root node_modules不是客户端架构依据。当前锁定React/react-dom19.2.5、Vite8.3.8、plugin-react6.0.1，不是要求手工固定传递依赖。

[项目入口](../README.md) · [API索引](../docs/api-reference.md) · [客户端边界](SPEC.md) · [医院AI政策](../docs/hospital-ai-boundary-policy.md)

## 安装与命令

建议Node22.x（已有验证22.22.0）与npm。从仓库根目录：

```bash
npm --prefix frontend ci --include=optional
npm --prefix frontend run dev
npm --prefix frontend run build
npm --prefix frontend run preview
```

也可在本目录执行对应 `npm ci` / `npm run ...`。使用锁文件，不用 `npm audit fix --force` 或手工跨大版本override解决安装问题。可选原生绑定缺失时先确认Node/平台和锁定安装，勿修改业务源码绕过依赖错误。

- 独立dev：`127.0.0.1:5173`；独立preview：`127.0.0.1:4173`，均strictPort。
- Vite资源base与自定义路由base为 **`/medcollab/`**，建议访问 `http://localhost:5173/medcollab/login`（preview独立端口则4173）。`src/App.jsx`负责去除/补上base和浏览器history，不是React Router。
- 根 `scripts/quick-start.sh` 默认preview，**将host覆盖为0.0.0.0、port覆盖为CLIENT_PORT=5173**；并非独立preview默认值。只用于隔离开发/演示，Vite preview不是医院生产服务器。
- `dist/`为构建产物，不手工编辑/提交；构建成功不代表完整页面流程、权限或医学语义已验收。

## API与WebSocket配置

`src/lib/runtimeConfig.js`读取构建期 `VITE_*`。未指定origin时使用浏览器当前host/协议配合以下端口；页面HTTPS时会选择HTTPS/WSS，需要部署端真实支持相应协议，不会自动为后端配置TLS。

| 完整origin变量 | 端口备用变量 | 默认端口 |
|---|---|---:|
| `VITE_GATEWAY_ORIGIN` | `VITE_GATEWAY_PORT` | 8088 |
| `VITE_METRIC_API_ORIGIN` | `VITE_METRIC_API_PORT` | 8083 |
| `VITE_AUTH_WS_ORIGIN` | `VITE_AUTH_WS_PORT` | 8081 |
| `VITE_METRIC_WS_ORIGIN` | `VITE_METRIC_WS_PORT` | 8083 |

推荐origin包含协议、无末尾斜杠，不附加`/api/auth`、`/metric`或`/ws/...`；调用代码自行补路径。不要在`VITE_*`中放JWT签名秘密或云API key，它们会进入公开客户端bundle。修改构建期值后需重启dev或重新build，已有dist不会自动更新。

REST通常经gateway：auth前缀`/api/auth`、患者`/patients`、metric`/metric`。metric遇网关403还会直连metric；通知和AI WS始终直连各自服务（query token）。Ktor gateway不代理WS。详细路径/请求/错误见[API参考](../docs/api-reference.md)，不能概括成“所有请求都经过网关并获得资源授权”。

## 页面与持久化事实

- `src/App.jsx`：账号、组织、患者、影像/分析/报告、会话页面与自定义路由。
- `src/lib/api.js`：REST、刷新与有限重试、文件响应；`auth.js`：本地认证状态。
- `src/lib/ws.js`：通知及AI WebSocket；`systemNotifications.js`：通知本地状态。
- `src/app.css`、`src/index.css`：界面样式。

AI的`analysisResult`是内层JSON字符串或失败说明，不是总能解析的成功对象；文本回复当前是固定范围说明，不是已执行的多轮LLM。科研bundle只支持ULTRASOUND，界面存在其他模态选项不代表该模型支持它们。

PDF在AI受控函数中先生成；“确认结果”才POST analyses，“生成报表”按钮实际上POST reports登记**已有PDF**。确认、登记及会话保存是分离请求，前端按钮限制不等于服务端授权或跨步骤事务；恢复聊天不等于恢复Agent运行，也不是已核实医生签署。

### 当前兼容与安全缺口

- 已有null守卫不等于科研展示完整：聊天转换未保留顶层`intendedUse`，高亮转换未保留`pixelCount/widthPx/heightPx`；部分区域仍泛称“置信度”，不能视为校准概率，集合指标显示也未全面验收。以明确非诊断的payload/报告及后续专项验证为准，不能把构建通过当端到端科研审计完成。
- `GeneralMetric.confidence`和毫米可空属于BREAKING CHANGE，不补0mm或固定95，不在浏览器推断良恶性、风险、血流等新医学结论。
- 某些patient helper指向不存在的`/soft`、`/restore`、`/batch/status`、`/{id}/doctor`；以[患者API](../patient-service/API.md)为准，不据helper宣称功能已实现。网关DELETE body丢失也影响批量删除。
- 401刷新、502/503重试、403直连存在，但包括写请求，**不是幂等或可靠恢复保证**；JSON解析失败可能被转换成空数组，未报错不代表契约成功。
- token存localStorage、WS query token，以及请求/响应/认证头调试日志均未经医院隐私治理。当前代码会记录部分甚至完整header对象；勿使用真实病历/令牌作公开演示或上传console日志。
- 隐藏菜单、角色标签、hospitalId及JWT不证明完整医院/患者授权；当前还有普通metric直连CRUD未鉴权等服务端缺口。

## 验证

当前无专门前端自动化测试框架，最低客户端检查是`npm --prefix frontend run build`。它只验证安装/编译，不证明全浏览器流程、跨院隔离、科研字段持久化或医院可用。接口变更应同时核对shared DTO、Kotlin/Python schema、`src/lib`和真实消费路径；本轮文档更新没有修改界面或业务实现。
