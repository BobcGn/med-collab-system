package dto

import kotlinx.serialization.Serializable
import java.time.LocalDateTime

/**
 * 医学影像密封类
 */
@Serializable
sealed class MedicalImageDto {
    /**
     * 医学影像基础信息
     */
    @Serializable
    data class MedicalImageBase(
        val id: String, // 影像ID
        val hospitalId: String, // 医院ID
        val patientId: String, // 患者ID
        val patientName: String, // 患者姓名
        val imageType: String, // 影像类型（CT/MRI/XRAY/ULTRASOUND）
        val filePath: String, // 文件路径
        val fileSize: Long? = null, // 文件大小
        val status: String, // 状态
        val uploadTime: String, // 上传时间
        val isDeleted: Boolean = false // 是否删除
    ) : MedicalImageDto()

    /**
     * 医学影像详情信息
     */
    @Serializable
    data class MedicalImageDetail(
        val id: String, // 影像ID
        val hospitalId: String, // 医院ID
        val patientId: String, // 患者ID
        val patientName: String? = null, // 患者姓名
        val imageType: String, // 影像类型
        val filePath: String, // 文件路径
        val fileSize: Long? = null, // 文件大小
        val status: String, // 状态
        val uploadTime: String, // 上传时间
        val isDeleted: Boolean = false, // 是否删除
        val analysisResults: List<String>? = null // 关联的分析结果ID列表
    ) : MedicalImageDto()

    /**
     * 医学影像列表项
     */
    @Serializable
    data class MedicalImageItem(
        val id: String, // 影像ID
        val imageType: String, // 影像类型
        val status: String, // 状态
        val uploadTime: String, // 上传时间
        val fileSize: Long? = null, // 文件大小
        val patientName: String? = null // 患者姓名
    ) : MedicalImageDto()

    /**
     * 医学影像创建请求
     */
    @Serializable
    data class MedicalImageCreate(
        val hospitalId: String, // 医院ID
        val patientId: String, // 患者ID
        val patientName: String, // 患者姓名
        val imageType: String, // 影像类型
        val filePath: String, // 文件路径
        val fileSize: Long? = null // 文件大小
    ) : MedicalImageDto()

    /**
     * 医学影像更新请求
     */
    @Serializable
    data class MedicalImageUpdate(
        val status: String? = null, // 状态
        val fileSize: Long? = null // 文件大小
    ) : MedicalImageDto()

    /**
     * 医学影像分页响应
     */
    @Serializable
    data class MedicalImagePageResponse(
        val images: List<MedicalImageItem>, // 影像列表
        val total: Int, // 总数量
        val page: Int, // 当前页码
        val size: Int // 每页大小
    ) : MedicalImageDto()

    /**
     * 医学影像统计信息
     */
    @Serializable
    data class MedicalImageStatistics(
        val totalImages: Int, // 总影像数
        val byType: Map<String, Int>, // 按类型统计
        val byStatus: Map<String, Int>, // 按状态统计
        val recentImages: List<MedicalImageItem> // 最近影像
    ) : MedicalImageDto()
}