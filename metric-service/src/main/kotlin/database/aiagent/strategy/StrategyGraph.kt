package database.aiagent.strategy

import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.tools.ToolRegistry
import com.example.database.AIAgent.tools.MedicalImageAnalyzerTool

/**
 * 医疗图像分析与生生成报表策略图
 */
val metricReportStrategy = strategy<String, String>(""){
    TODO("所有的图、节点的输入输出类型尚未细化，暂时以String代替")
    // 将影像工具加入工具注册表
    val toolRegistry = ToolRegistry{ tool(MedicalImageAnalyzerTool) }


    /**
     * 定义子图
     */
    // 上传影像子图
    val subgraphUploadImage by subgraph<String,String>("上传影像") {
        // 定义节点
        val nodeUploadImage by node<String, String>("上传影像"){
            TODO()
        }
        val nodeValidateImage by node<String, String>("验证影像"){
            TODO()
        }
        TODO()
    }

    val subgraphAnalyzeImage by subgraph<String,String>("分析影像") {
        // 定义节点
        val nodeCallModelTool by node<String, String>("调用模型工具"){
            TODO()
        }
        val nodeHandleFailure by node<String, String>("处理失败"){
            TODO()
        }
        TODO()
    }

    val subgraphGenerateReport by subgraph<String,String>("生成报告") {
        // 定义节点
        val nodeRenderPdf by node<String, String>("生成报告"){
            TODO()
        }
        val nodeSaveReport by node<String, String>("保存报告"){
            TODO()
        }

        TODO()
    }

    /**
     * 子图路径
     */
    // 开始节点 -> 影像上传
    nodeStart then
            subgraphUploadImage then
            // 影像上传 -> 验证影像
            subgraphAnalyzeImage then
            // 验证影像 -> 报表生成
            subgraphGenerateReport then
            nodeFinish
}