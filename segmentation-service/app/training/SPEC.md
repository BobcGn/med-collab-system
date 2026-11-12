---
id: unet-training
type: submodule-design
status: draft
title: UNet 课程训练、数据划分与实验评测
parent: segmentation-service
references: [hospital-ai-boundary]
tags: [training, evaluation, coursework]
---

# UNet 课程训练、数据划分与实验评测

## 职责与边界

提供本地 BUSI 目录准备、可重现训练、原尺寸评测和模型包导出 CLI，不抓取或下载未核实许可的数据，不执行诊断，也不操作患者业务库。部署契约与共享组件的依赖由 `segmentation-service` 统一定义。

数据准备 CLI 仅在清单构建成功后打印路径与分组统计；OSError / ValueError 仍通过 argparse 以退出码 2 结束，不输出伪成功信息或空清单兜底。指标模块显式公开指标函数及复用的 BFS 接口，保持与服务端同一函数对象。成功分支与公共导出均显式表达，以便静态分析识别，不改变训练或评测数值。

源码具备闭环，不代表已训练出有效医学模型。本次数据下载来源已由用户确认，镜像许可声明和清理记录见下文；病例独立性及临床效度仍未验证，真实精度只能以完成的封存测试结果为准。

## 数据与实验独立性

仅接收已解压的 benign / malignant / normal PNG 目录。人工明确提供原发布 URL、许可和确认标志后才生成清单；user-confirmed 是操作者声明，不是联网验证或许可自动认证。

同一原图的多个 mask 合并；正样本必须有 mask，normal 缺 mask 显式作为零前景。检查尺寸、二值性、孤立标注和损坏文件。以训练实际使用的灰度像素哈希组成重复组，同组不跨 train/val/test；冲突标注拒绝。默认按类别/重复组近似 70/15/15，固定 seed。当前无患者 ID，只能声明 duplicate_group 划分且 patient_independence_verified=false，不能伪称患者独立测试。

增强为训练集成对翻转，由 seed/epoch/ID 决定；验证和测试不增强。保存完整清单哈希及影像/标注文件摘要以发现训练后的变化。评测要求与训练封存清单一致；新的外部队列需要独立的显式评测流程，当前未提供。

## 本次 BUSI 数据与溯源

用户确认下载自 Kaggle 的 aryashah2k/breast-ultrasound-images-dataset。原始文件校验为 780 原图、798 mask：benign 437、malignant 210、normal 133；从 master 工作区复制到作业工作区的所有文件 SHA-256 一致，原件不变，数据只放在 Git 忽略目录。

镜像页面公示 CC0: Public Domain；记录此声明，不把它当作独立权利链认证或原作者重新授权证明。保留 Al-Dhabyani 等原作者及 DOI 10.1016/j.dib.2019.104863 的引用；论文的 CC BY 4.0 与镜像数据标签分别注明。实际下载修订号未独立核验，不伪称下载了另一个整理版。

本地校验发现 benign/benign (433) 和 malignant/malignant (145) 的灰度像素完全相同，但合并 mask 不同。用户批准在派生训练数据中排除整组，而非猜测哪份标注正确；原始 780 图全部保留，排除文件和理由写入 curation.json。清理后共 778 图（436 benign、209 malignant、133 normal），seed=42 的清单为 544 train / 118 val / 116 test。此处理不解决近似重复或未知患者级关联。

## 训练与模型选择

FP32、UNet2D、Adam、BCEWithLogitsLoss + 逐图 Soft Dice；学习目标包含空标注样本。auto 优先 MPS 再 CPU，显式指定不可用 MPS 则报错；记录设备、依赖版本、seed 与超参数，不承诺跨设备逐位复现。

仅训练 train，按原尺寸 val macro Dice 的严格改善选择最佳权重，同分保留较早 epoch；支持有界 epoch 和可选 patience。最后 checkpoint 保存优化器与训练进度，但不提供任意 pickle 恢复入口。导出的受限 state_dict 和 manifest 才是推理输入。

输出目录必须是新目录或空目录，不能覆盖数据清单、已有曲线或 checkpoint。此约束源于曾发现同目录 history.json 清单会被训练历史覆盖的缺陷；不能通过仅列出少数已知输出名来替代目录隔离。

## 评测的含义

按原图尺寸逐图计算 Dice / IoU / precision / recall，再做 macro 汇总。双空 Dice/IoU=1、单空=0；无定义 precision/recall 仅双空时记 1。单独列出有前景组、空标注组的图像级假阳性率与前景像素比例，缺失组为 null 而非虚假的完美得分。

同一模型比较未清理 mask、共享四连通 BFS 小区域过滤、Otsu 阈值基线；Otsu 方向预声明或只在 val 上选择。test 不允许临时覆盖概率阈值；BFS 参数应在 val 选择后固定，并与服务配置一致。增强消融使用相同清单、seed 和训练条件分别运行，不把模型差异都归因于后处理。BFS 可能删除真实小目标，不能预设它必然提高指标。

输出逐图 CSV、JSON、曲线和预测对照图，参数与数据来源一起记录。不以合成测试数值、随机/手工权重或只挑选的成功图冒充公开数据精度；具体性能阈值尚未由课程要求或真实实验确认。

## 本次冻结实验与限制

M5 / 32 GiB、Torch 2.15.0、FP32、256×256、base_channels=32、batch=2、Adam lr=0.001、seed=42、概率阈值 0.5、最多 20 epoch / patience=5。无增强运行 11 轮、最佳第 6 轮；成对翻转增强运行 20 轮、最佳第 18 轮。验证集 raw Dice 分别为 0.61535 / 0.65516，由验证集预先选增强模型。

只在 118 张验证图上比较 BFS 最小区域 [1,16,64,256,1024]，按 macro Dice 选 1024 个原图像素，同分偏向较小阈值；在评测测试图前写入并哈希封存 validation-selection.json。部署必须显式使用同一 1024，服务默认 16 不是本次实验设置。

116 张封存测试图（96 有前景、20 空标注）的 macro Dice / IoU：Otsu 为 0.14445 / 0.08805，UNet 基线为 0.59852 / 0.50481，增强 UNet 为 0.63654 / 0.54637，增强 + BFS 为 0.65516 / 0.56594。补充对照“基线 + 同一 BFS”Dice 为 0.64269，其空标注误检少于增强模型，不能声称增强模型在所有指标上占优。

增强模型的 BFS 使空标注误检由 8/20 降为 6/20，但有前景 Dice 仅由 0.64416 升为 0.64581；7 张有前景图的 Dice 下降，2 张的非空预测被清空。该搜索后处理不能修正模型定位错误，也可能删除小目标。单种子、不同实际训练轮数和未知患者关联限制结论，不能称为统计显著提升、临床准确率或患者独立泛化表现。

本地证据位于 Git 忽略的 var/coursework/：两组 run/history/curves、validation-selection.json、逐图评测、results/summary.json 与对照/失败图、live-verification.json 及科研 PDF。原始数据、权重和生成结果不入库；此记录只描述本次冻结实验，不授权根据已见测试结果继续调参。
