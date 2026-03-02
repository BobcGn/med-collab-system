package exception

/**
 * 指标与报表服务异常类
 */
sealed class MetricException : Exception() {
    abstract val code: Int
    abstract override val message: String

    // ==================== 医学影像部分 ====================
    /**
     * 医学影像不存在异常
     * 错误码: 4001
     */
    data class MedicalImageNotFoundException(
        override val code: Int = 4001,
        override val message: String = "医学影像不存在"
    ) : MetricException()

    /**
     * 医学影像上传失败异常
     * 错误码: 4002
     */
    data class MedicalImageUploadFailedException(
        override val code: Int = 4002,
        override val message: String = "医学影像上传失败"
    ) : MetricException()

    /**
     * 医学影像处理失败异常
     * 错误码: 4003
     */
    data class MedicalImageProcessingFailedException(
        override val code: Int = 4003,
        override val message: String = "医学影像处理失败"
    ) : MetricException()

    // ==================== 医疗指标部分 ====================
    /**
     * 医疗指标不存在异常
     * 错误码: 4101
     */
    data class MedicalMetricNotFoundException(
        override val code: Int = 4101,
        override val message: String = "医疗指标不存在"
    ) : MetricException()

    /**
     * 创建医疗指标失败异常
     * 错误码: 4102
     */
    data class CreateMedicalMetricFailedException(
        override val code: Int = 4102,
        override val message: String = "创建医疗指标失败"
    ) : MetricException()

    /**
     * 更新医疗指标失败异常
     * 错误码: 4103
     */
    data class UpdateMedicalMetricFailedException(
        override val code: Int = 4103,
        override val message: String = "更新医疗指标失败"
    ) : MetricException()

    /**
     * 删除医疗指标失败异常
     * 错误码: 4104
     */
    data class DeleteMedicalMetricFailedException(
        override val code: Int = 4104,
        override val message: String = "删除医疗指标失败"
    ) : MetricException()

    /**
     * 医疗指标数据无效异常
     * 错误码: 4105
     */
    data class MedicalMetricDataInvalidException(
        override val code: Int = 4105,
        override val message: String = "医疗指标数据无效"
    ) : MetricException()

    // ==================== 报表部分 ====================
    /**
     * 报表不存在异常
     * 错误码: 4201
     */
    data class ReportNotFoundException(
        override val code: Int = 4201,
        override val message: String = "报表不存在"
    ) : MetricException()

    /**
     * 创建报表失败异常
     * 错误码: 4202
     */
    data class CreateReportFailedException(
        override val code: Int = 4202,
        override val message: String = "创建报表失败"
    ) : MetricException()

    /**
     * 生成报表失败异常
     * 错误码: 4203
     */
    data class GenerateReportFailedException(
        override val code: Int = 4203,
        override val message: String = "生成报表失败"
    ) : MetricException()

    /**
     * 导出报表失败异常
     * 错误码: 4204
     */
    data class ExportReportFailedException(
        override val code: Int = 4204,
        override val message: String = "导出报表失败"
    ) : MetricException()

    /**
     * 报表数据无效异常
     * 错误码: 4205
     */
    data class ReportDataInvalidException(
        override val code: Int = 4205,
        override val message: String = "报表数据无效"
    ) : MetricException()

    // ==================== 权限相关异常 ====================
    /**
     * 无权限访问指标数据异常
     * 错误码: 4301
     */
    data class MetricPermissionDeniedException(
        override val code: Int = 4301,
        override val message: String = "无权限访问指标数据"
    ) : MetricException()

    /**
     * 无权限访问报表异常
     * 错误码: 4302
     */
    data class ReportPermissionDeniedException(
        override val code: Int = 4302,
        override val message: String = "无权限访问报表"
    ) : MetricException()
}