package database.aiagent.strategy

import ai.koog.agents.core.dsl.builder.forwardTo
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.tools.ToolRegistry
import database.aiagent.tools.MedicalImageAnalyzerTool
import database.aiagent.tools.ReportGenerateTool
import enums.ImageType
import org.jetbrains.exposed.sql.transactions.transaction

/**
 * 辅助函数：解析输入参数
 */
fun parseInput(input: String): Map<String, Any> {
    // 简单的JSON解析模拟
    // 实际项目中，应该使用标准的JSON解析库
    val inputMap = mutableMapOf<String, Any>()
    
    // 模拟解析逻辑
    if (input.contains("imagePath")) {
        inputMap["imagePath"] = input.substringAfter("imagePath:").substringBefore('"').trim()
    }
    if (input.contains("imageType")) {
        val typeStr = input.substringAfter("imageType:").substringBefore('"').trim()
        inputMap["imageType"] = ImageType.valueOf(typeStr)
    }
    if (input.contains("hospitalId")) {
        inputMap["hospitalId"] = input.substringAfter("hospitalId:").substringBefore('"').trim()
    }
    if (input.contains("patientId")) {
        inputMap["patientId"] = input.substringAfter("patientId:").substringBefore('"').trim()
    }
    if (input.contains("patientName")) {
        inputMap["patientName"] = input.substringAfter("patientName:").substringBefore('"').trim()
    }
    
    return inputMap
}

/**
 * 医疗图像分析与生成报表策略图
 * 输入：包含影像信息的JSON字符串
 * 输出：生成的报表结果
 */
val metricReportStrategy = strategy<String, String>("医疗影像分析与报表生成策略"){
    // 将影像分析和报表生成工具加入工具注册表
    val toolRegistry = ToolRegistry{
        tool(MedicalImageAnalyzerTool)
        tool(ReportGenerateTool)
    }

    /**
     * 定义子图
     */
    // 上传影像子图
    val subgraphUploadImage by subgraph<String, Map<String, Any>>("上传医学影像子图") {
        // 定义节点
        val nodeUploadImage by node<String, Map<String, Any>>("上传影像"){ input ->
            // 解析输入参数
            val inputData = parseInput(input)
            
            // 模拟影像上传过程
            val imageData = mapOf(
                "imagePath" to inputData["imagePath"] as String,
                "imageType" to inputData["imageType"] as ImageType,
                "hospitalId" to inputData["hospitalId"] as String,
                "patientId" to inputData["patientId"] as String,
                "patientName" to inputData["patientName"] as String,
                "uploadTime" to java.time.LocalDateTime.now().toString()
            )
            
            imageData
        }
        
        val nodeValidateImage by node<Map<String, Any>, Map<String, Any>>("验证影像"){ input ->
            // 验证上传的医学影像
            val imagePath = input["imagePath"] as String
            val imageType = input["imageType"] as ImageType
            
            // 模拟验证过程
            if (imagePath.isBlank()) {
                throw IllegalArgumentException("影像路径不能为空")
            }
            
            // 添加验证结果
            input + mapOf("validated" to true, "validationTime" to java.time.LocalDateTime.now().toString())
        }

        // 定义边
        edge(nodeUploadImage forwardTo nodeValidateImage)
    }

    // 影像分析子图
    val subgraphAnalyzeImage by subgraph<Map<String, Any>, String>(
        name = "医学影像分析子图",
        tools = listOf(MedicalImageAnalyzerTool)
    ) {
        // 定义节点
        val nodeCallModelTool by node<Map<String, Any>, String>("调用模型工具分析医学影像"){ input ->
            // 调用模型工具分析医学影像
            val analysisResult = MedicalImageAnalyzerTool.execute(MedicalImageAnalyzerTool.Args(
                imagePath = input["imagePath"] as String,
                imageType = input["imageType"] as ImageType,
                hospitalId = input["hospitalId"] as String,
                patientId = input["patientId"] as String,
                patientName = input["patientName"] as String
            ))
            
            analysisResult
        }
        
        val nodeHandleFailure by node<String, String>("处理失败"){ input ->
            // 处理失败情况
            if (input.contains("error")) {
                // 记录错误信息
                println("分析失败: $input")
            }
            input
        }

        // 定义边
        edge(nodeCallModelTool forwardTo nodeHandleFailure)
    }

    // 报表生成子图
    val subgraphGenerateReport by subgraph<String, String>(
        name = "生成报表子图",
        tools = listOf(ReportGenerateTool)
    ) {
        // 定义节点
        val nodeRenderPdf by node<String, String>("生成报告"){ input ->
            // 生成报告
            val report = ReportGenerateTool.execute(ReportGenerateTool.Args(
                analysisResult = input
            ))
            report
        }
        
        val nodeSaveReport by node<String, String>("保存报告"){ input ->
            // 保存报告到数据库
            transaction {
                // 实际项目中，这里应该将报告保存到数据库
                println("保存报告到数据库: $input")
            }
            input
        }

        // 定义边
        edge(nodeRenderPdf forwardTo nodeSaveReport)
    }

    /**
     * 子图路径
     */
    // 开始节点 -> 影像上传
    nodeStart then
            subgraphUploadImage then
            // 影像上传 -> 影像分析
            subgraphAnalyzeImage then
            // 影像分析 -> 报表生成
            subgraphGenerateReport then
            // 报表生成 -> 结束节点
            nodeFinish
}