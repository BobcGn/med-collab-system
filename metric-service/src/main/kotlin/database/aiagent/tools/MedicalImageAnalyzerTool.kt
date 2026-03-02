package com.example.database.AIAgent.tools

import ai.koog.agents.core.tools.Tool
import ai.koog.agents.core.tools.annotations.LLMDescription

import enums.ImageType
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer

/**
 * 医疗图像分析工具
 * @args 医学影像
 * @return 分析结果
 */
object MedicalImageAnalyzerTool: Tool<MedicalImageAnalyzerTool.Args, String>() {
    @Serializable
    data class Args (
        @property:LLMDescription("医学影像路径")
        val imagePath: String,
        @property:LLMDescription("医学影像类型")
        val imageType: ImageType,
        @property:LLMDescription("所属医院ID（英文简称）")
        val hospitalId: String,
        @property:LLMDescription("所属患者ID")
        val patientId: String,
        @property:LLMDescription("所属患者姓名")
        val patientName: String
    )

    // Args类序列化工具
    override val argsSerializer = Args.serializer()
    override val resultSerializer = String.serializer()

    // 工具名称
    override val name = "医学影像分析工具"

    // 工具描述
    override val description = "用于分析医学影像，并返回分析结果"

    // 工具执行逻辑
    override suspend fun execute(args: Args): String {
        TODO("Not yet implemented")
    }
}