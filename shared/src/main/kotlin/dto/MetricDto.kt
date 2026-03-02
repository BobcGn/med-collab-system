package dto

import kotlinx.serialization.Serializable

/**
 * 指标数据密封类
 */
@Serializable
sealed class MetricDto {
    /**
     * CT 扫描指标
     */
    @Serializable
    data class CTMetric(
        val density: Double, // 密度值
        val size: Double, // 大小（mm）
        val location: String, // 位置
        val severity: String, // 严重程度
        val confidence: Double // 置信度
    ) : MetricDto()

    /**
     * MRI 扫描指标
     */
    @Serializable
    data class MRIMetric(
        val signalIntensity: String, // 信号强度
        val size: Double, // 大小（mm）
        val location: String, // 位置
        val tissueCharacteristics: String, // 组织特征
        val confidence: Double // 置信度
    ) : MetricDto()

    /**
     * X光指标
     */
    @Serializable
    data class XRayMetric(
        val opacity: String, // 不透光度
        val size: Double, // 大小（mm）
        val location: String, // 位置
        val boneStructure: String, // 骨骼结构
        val confidence: Double // 置信度
    ) : MetricDto()

    /**
     * 超声指标
     */
    @Serializable
    data class UltrasoundMetric(
        val echogenicity: String, // 回声性
        val size: Double, // 大小（mm）
        val location: String, // 位置
        val bloodFlow: String, // 血流情况
        val confidence: Double // 置信度
    ) : MetricDto()

    /**
     * 通用指标（适用于多种检查类型）
     */
    @Serializable
    data class GeneralMetric(
        val name: String, // 指标名称
        val value: Double, // 指标值
        val unit: String, // 单位
        val referenceRange: String, // 参考范围
        val confidence: Double // 置信度
    ) : MetricDto()

    /**
     * 多指标集合
     */
    @Serializable
    data class MetricCollection(
        val metrics: List<MetricDto> // 指标列表
    ) : MetricDto()
}