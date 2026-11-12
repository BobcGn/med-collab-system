---
id: segmentation-service
type: module-design
status: draft
title: UNet 训练验证与结构化分割服务
parent: med-collab-architecture
references: [hospital-ai-boundary]
tags: [unet, coursework, inference]
---

# UNet 训练验证与结构化分割服务

## 职责与已确认范围

Python 服务拥有影像解码、模型预处理与执行、mask 后处理、质量门禁及分割工件，不拥有业务账号、患者档案、报告定稿或自由问答。跨服务契约归 `med-collab-architecture`，医疗权限边界归 `hospital-ai-boundary`。

用户确认个人课程作业需要独立完成训练、评测、模型导出和平台推理闭环，不依赖研究生的模型交付。现已具备本地数据准备、训练、原尺寸评测、模型包导出与服务消费代码；训练边界归 `unet-training`。已完成本机 MPS 的 BUSI 训练、封存内部评测，以及真实模型经 Python/Kotlin 工具链生成科研 PDF 的验证；实验结果与限制归 `unet-training`，仍无患者独立或临床效度证据。

个人作业与正式 Agent 建设分别在独立分支进行。合并顺序是用户的工作安排，不改变服务契约，也不表示课程模型自动获得医院使用资格。用户已确认使用本机 Apple Silicon M5 / 32 GiB，首个任务为乳腺超声二值区域分割，结合 UNet、BFS 图搜索和视觉区域量化；不做良恶性分类。本次使用用户确认来源的公开 BUSI 镜像，许可声明、原始校验和冲突组清理归 `unet-training`；病例分组、课程完整评分标准与效果阈值仍未确认，不承诺尚未测量的精度或训练速度。

## 现有输入与模型契约

单张与顺序批量接口接收 JSON；LOCAL_FILE、DATA_URL 和 HTTP(S) OBJECT_STORAGE 最终均由 Pillow 解码为单张栅格图。INLINE_REFERENCE 没有像素，执行时拒绝；尚无原生 DICOM、体数据、HU 窗宽窗位、物理间距/方向读取或多帧处理能力。旧配置的 Python 模态字段只要求非空并转大写；课程模型包则在加载、流水线及执行层严格限制 ULTRASOUND。

训练、评测与服务共享灰度/双线性拉伸、[0,1] 到 [-1,1] 归一化及 float32 概率恢复；mask 采用 nearest。概率不经 8-bit 图像量化，阈值化和四连通 BFS 过滤均在原图尺寸进行。旧配置多通道仍是灰度复制，不是真 RGB；课程包只支持单通道二值输出。

加载器固定构造仓库内 UNet2D（四级下采样、Conv/BatchNorm/ReLU、转置卷积与 skip 连接）；本地默认输入为 1×256×256、base_channels=32、单输出。接受原始 state_dict 或 state_dict / model_state_dict 包装，严格匹配键和形状；不支持任意网络权重、TorchScript 或 ONNX 自动适配。

旧配置单输出取 sigmoid，多输出 softmax 后只取一个 lesion 通道，仍不是多类别结构化分割系统；默认允许随机前向和显式 mock 联调。课程包返回 target_region，固定 intended_use=research；weights_loaded 只证明加载，不证明精度。

通过 model.manifest_path / SEGMENTATION_SERVICE_MODEL_MANIFEST_PATH 加载课程包时，先校验 manifest、预处理版本、权重哈希与结构，再派生所有阶段共用的有效设置，强制 Torch、禁用随机权重，拒绝不匹配/缺失/损坏文件。尺寸、通道和概率阈值以包为准，不允许部署环境静默改写训练契约。原始 checkpoint 也使用受限 weights_only 加载。

研究包无显式覆盖时，BFS 默认最小区域为 16 原图像素以匹配评测；旧 Settings/local 为24，test TOML 为8，不是统一服务默认。显式 Settings 参数、自定义 TOML 或环境变量可覆盖。随仓库提供的旧 TOML 中默认 24 不视为课程显式覆盖。研究包总是取消物理间距，不能通过设置全局间距恢复未经标定的毫米。服务设备不可用时仍可能按既有逻辑回到 CPU，实际设备由描述符记录。

## 输出的真实含义

区域返回 pixel_count、width_px、height_px、覆盖率、百分比边界框、采样轮廓和平均前景概率。课程包的 estimated_size_mm 为 null、severity 为 not_assessed，位置明确为图像象限；均不代表解剖定位或临床风险，前景概率未经临床校准。旧模式仍使用全局间距和规则派生值，不能视为患者特异标定。轮廓采样尚未证明保留完整拓扑。

/segment、/models/current 及工件 metadata 保留模型研究用途、支持模态与 bundle_manifest_sha256。消费方必须保留用途与单位，具体校验和报告映射由 `metric-agent-harness` 定义。

质量门禁对空 mask / 无区域返回 FAIL，低置信度或单区域覆盖过大返回 LIMITED，其余 PASS；PASS 不检查真实训练来源，随机/mock 输出也可能通过。结果状态与 HTTP 状态不同：质量失败仍可通过 HTTP 200 返回 failed payload。

默认保存 mask、overlay、result.json，即使门禁失败也可能产生工件。输出 mask 使用与区域一致的 BFS 清理结果；return_contour 和 return_mask 生效，return_metrics 仍保留旧行为。请求 ID 必须是安全单目录组件，工件目录原子预留且不可覆盖；重复 ID 在写入阶段返回存储500，不是幂等命中，失败可能留下部分目录，批量先前工件也不回滚。包内可追溯预处理/训练配置和权重哈希；元数据记录模型包哈希，但这不是对来源许可或效度的独立认证。/health 仅表示原始设置下的服务存活，模型通常仍在依赖首次解析时初始化，不是 readiness；工件路径不是 URL，当前无下载端点。

[API 参考](API.md)维护实际字段/错误和输出选项；配置与设备的操作差异见[手册](../docs/segmentation-service-operation-manual.md)。配置只选一个 TOML，未知环境回 local，环境覆盖后再派生包约束，无 dotenv 或热更新；不能把操作示例当作新的后端能力。

## 训练与服务边界

`unet-training` 拥有数据清单、训练选择、评测和模型导出；它复用本模块的模型、变换、BFS 与模型包契约，而不是另写推理前处理。评测通过不改变 `hospital-ai-boundary` 的权限范围。离线空标注可正常评分，在线空预测仍失败/复核，不推断患者正常。

## 已知安全与运行限制

原始输入仍缺少统一字节/像素/批量限制、本地目录与远程 URL 白名单及服务鉴权。配置的推理超时尚未落实到执行取消，批量异常可中止整批；工件可能含患者身份信息，不能公开暴露路径或把真实患者资料直接用于课程实验。

现有回归覆盖合成训练/导出、受限权重加载、部署配置、原尺寸离线/API 一致、BFS、工件安全及用途/模态失败边界。本机 MPS 已通过合成与真实模型的离线/API 概率一致性验证；真实受控链路验证生成了明确非诊断的科研 PDF，但未验证数据库登记及完整前端/鉴权流程。BUSI 已完成封存内部评测，结果仍受数据划分、单种子和误检限制；质量门禁 PASS 不是临床效度证明。
