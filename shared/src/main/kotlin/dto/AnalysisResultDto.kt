package dto

import kotlinx.serialization.Serializable

/**
 * 分析结果密封类
 */
@Serializable
sealed class AnalysisResultDto {
    /**
     * 分析结果基础信息
     */
    @Serializable
    data class AnalysisResultBase(
        val id: String, // 分析结果ID
        val hospitalId: String, // 医院ID
        val imageId: String, // 图像ID
        val patientId: String, // 患者ID
        val patientName: String, // 患者姓名
        val status: String, // 分析状态
        val createdAt: String, // 创建时间
        val completedAt: String? = null, // 完成时间
        val errorMessage: String? = null // 错误信息
    ) : AnalysisResultDto()

    /**
     * 完整分析结果（包含指标）
     */
    @Serializable
    data class AnalysisResultComplete(
        val id: String, // 分析结果ID
        val hospitalId: String, // 医院ID
        val imageId: String, // 图像ID
        val patientId: String, // 患者ID
        val patientName: String, // 患者姓名
        val metrics: MetricDto, // 分析指标
        val status: String, // 分析状态
        val createdAt: String, // 创建时间
        val completedAt: String? = null, // 完成时间
        val errorMessage: String? = null // 错误信息
    ) : AnalysisResultDto()

    /**
     * 分析结果列表项
     */
    @Serializable
    data class AnalysisResultItem(
        val id: String, // 分析结果ID
        val imageId: String, // 图像ID
        val imageType: String, // 图像类型
        val patientId: String, // 患者ID
        val patientName: String, // 患者姓名
        val status: String, // 分析状态
        val createdAt: String, // 创建时间
        val completedAt: String? = null, // 完成时间
        val summary: String? = null // 分析摘要
    ) : AnalysisResultDto()

    /**
     * 分析结果创建请求
     */
    @Serializable
    data class AnalysisResultCreate(
        val hospitalId: String, // 医院ID
        val imageId: String, // 图像ID
        val patientId: String, // 患者ID
        val patientName: String // 患者姓名
    ) : AnalysisResultDto()

    /**
     * 分析结果更新请求
     */
    @Serializable
    data class AnalysisResultUpdate(
        val status: String, // 分析状态
        val metrics: MetricDto? = null, // 分析指标
        val completedAt: String? = null, // 完成时间
        val errorMessage: String? = null // 错误信息
    ) : AnalysisResultDto()

    /**
     * 分析结果分页响应
     */
    @Serializable
    data class AnalysisResultPageResponse(
        val results: List<AnalysisResultItem>, // 分析结果列表
        val total: Int, // 总数量
        val page: Int, // 当前页码
        val size: Int // 每页大小
    ) : AnalysisResultDto()
}