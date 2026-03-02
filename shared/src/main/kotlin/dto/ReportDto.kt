package dto

import kotlinx.serialization.Serializable

/**
 * 报表密封类
 */
@Serializable
sealed class ReportDto {
    /**
     * 报表基础信息
     */
    @Serializable
    data class ReportBase(
        val id: String, // 报表ID
        val hospitalId: String, // 医院ID
        val patientId: String, // 患者ID
        val patientName: String, // 患者姓名
        val analysisIds: List<String>, // 关联的分析结果ID列表
        val reportType: String, // 报表类型
        val status: String, // 报表状态
        val createdAt: String, // 创建时间
        val generatedAt: String? = null, // 生成时间
        val errorMessage: String? = null // 错误信息
    ) : ReportDto()

    /**
     * 完整报表信息（包含文件信息）
     */
    @Serializable
    data class ReportComplete(
        val id: String, // 报表ID
        val hospitalId: String, // 医院ID
        val patientId: String, // 患者ID
        val patientName: String, // 患者姓名
        val analysisIds: List<String>, // 关联的分析结果ID列表
        val reportType: String, // 报表类型
        val filePath: String? = null, // 报表文件路径
        val fileSize: Long? = null, // 报表文件大小
        val status: String, // 报表状态
        val createdAt: String, // 创建时间
        val generatedAt: String? = null, // 生成时间
        val errorMessage: String? = null // 错误信息
    ) : ReportDto()

    /**
     * 报表列表项
     */
    @Serializable
    data class ReportItem(
        val id: String, // 报表ID
        val patientId: String, // 患者ID
        val patientName: String? = null, // 患者姓名
        val reportType: String, // 报表类型
        val status: String, // 报表状态
        val createdAt: String, // 创建时间
        val generatedAt: String? = null, // 生成时间
        val fileSize: Long? = null // 报表文件大小
    ) : ReportDto()

    /**
     * 报表创建请求
     */
    @Serializable
    data class ReportCreate(
        val hospitalId: String, // 医院ID
        val patientId: String, // 患者ID
        val patientName: String, // 患者姓名
        val analysisIds: List<String>, // 关联的分析结果ID列表
        val reportType: String // 报表类型
    ) : ReportDto()

    /**
     * 报表更新请求
     */
    @Serializable
    data class ReportUpdate(
        val status: String, // 报表状态
        val filePath: String? = null, // 报表文件路径
        val fileSize: Long? = null, // 报表文件大小
        val generatedAt: String? = null, // 生成时间
        val errorMessage: String? = null // 错误信息
    ) : ReportDto()

    /**
     * 报表分页响应
     */
    @Serializable
    data class ReportPageResponse(
        val reports: List<ReportItem>, // 报表列表
        val total: Int, // 总数量
        val page: Int, // 当前页码
        val size: Int // 每页大小
    ) : ReportDto()

    /**
     * 报表统计信息
     */
    @Serializable
    data class ReportStatistics(
        val totalReports: Int, // 总报表数
        val byType: Map<String, Int>, // 按类型统计
        val byStatus: Map<String, Int>, // 按状态统计
        val recentReports: List<ReportItem> // 最近报表
    ) : ReportDto()
}