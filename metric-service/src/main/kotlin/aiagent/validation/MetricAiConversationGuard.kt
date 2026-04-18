package aiagent.validation

enum class MetricAiConversationScope {
    IMAGE_ANALYSIS,
    METRIC_DISCUSSION,
    UNSUPPORTED,
}

private val chineseTopicKeywords = listOf(
    "医学影像",
    "医疗影像",
    "影像",
    "影像学",
    "影像报告",
    "检查报告",
    "报表",
    "片子",
    "胶片",
    "胸片",
    "x光",
    "x线",
    "ct",
    "核磁",
    "磁共振",
    "超声",
    "b超",
    "病理",
    "放射",
    "阅片",
    "扫描",
    "增强",
    "平扫",
    "序列",
    "病灶",
    "结节",
    "占位",
    "阴影",
    "钙化",
    "积液",
    "骨折",
    "磨玻璃",
    "实变",
    "低密度",
    "高密度",
    "信号",
    "回声",
    "强化",
    "梗阻",
    "狭窄",
    "出血",
    "水肿",
    "肿瘤",
    "囊肿",
    "斑块",
    "指标",
    "参数",
    "数值",
    "测量",
    "定量",
    "分割",
    "面积",
    "体积",
    "直径",
    "长度",
    "宽度",
    "厚度",
    "密度值",
    "置信度",
)

private val englishTopicPatterns = listOf(
    Regex("""(?i)\b(x[-\s]?ray|xray|ct|mri|mr|pet(?:-ct)?|cta|ultrasound|pathology|dicom)\b"""),
    Regex("""(?i)\b(hu|adc|suv|ef|t1|t2|dwi|flair|birads|ti-rads|tirads|pirads)\b"""),
)

internal fun determineMetricAiConversationScope(
    message: String?,
    hasImage: Boolean,
): MetricAiConversationScope {
    if (hasImage) {
        return MetricAiConversationScope.IMAGE_ANALYSIS
    }

    val normalizedMessage = message
        ?.trim()
        ?.lowercase()
        ?.takeIf { it.isNotEmpty() }
        ?: return MetricAiConversationScope.UNSUPPORTED

    val containsChineseKeyword = chineseTopicKeywords.any(normalizedMessage::contains)
    val containsEnglishKeyword = englishTopicPatterns.any { it.containsMatchIn(normalizedMessage) }

    return if (containsChineseKeyword || containsEnglishKeyword) {
        MetricAiConversationScope.METRIC_DISCUSSION
    } else {
        MetricAiConversationScope.UNSUPPORTED
    }
}

internal fun buildUnsupportedMetricAiPrompt(): String {
    return "AI Agent 仅支持受控医疗影像分析、既有结构化结果/正式报告解读，不提供闲聊或其他通用问答。LLM 不执行分割、不生成诊断性影像结论、不改写正式结构化分析结果。请上传医学影像，或输入正式报告、影像所见、测量指标等相关问题。"
}
