package com.example

import ai.koog.ktor.Koog
import ai.koog.prompt.executor.clients.deepseek.DeepSeekModels
import ai.koog.prompt.llm.LLMProvider
import aiagent.tools.MedicalImageAnalyzerTool
import io.ktor.server.application.*
import io.ktor.server.netty.EngineMain
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

fun main(args: Array<String>) {
    EngineMain.main(args)
}

fun Application.module() {
    val deepSeekSettings = resolveDeepSeekSettings(environment.config)
    val segmentationServiceSettings = resolveSegmentationServiceSettings(environment.config)
    MedicalImageAnalyzerTool.configureSegmentationService(segmentationServiceSettings)
    if (segmentationServiceSettings.enabled) {
        log.info("Segmentation service enabled at {}", segmentationServiceSettings.baseUrl)
    } else {
        log.warn("Segmentation service is disabled; metric-service will use local deterministic analyzer only")
    }

    /**
     * 安装Koog插件并配置DeepSeek Agent
     * 当前仅为后续“结果解释类文本能力”预留，不参与影像正式分析与正式报告生成。
     */
    if (deepSeekSettings.isConfigured) {
        log.info("DeepSeek provider configured from {} for non-diagnostic text capabilities", deepSeekSettings.source)
        install(Koog) {
            llm {
                /**
                 * 配置DeepSeek提供商
                 * 使用环境变量中的API密钥，确保在生产环境中正确设置。
                 * 注意：该提供商不得替代 U-Net/结构化工具生成医学分析结论。
                 */
                deepSeek(apiKey = deepSeekSettings.apiKey.orEmpty()) {
                    baseUrl = "https://api.deepseek.com"
                    /**
                     * 配置超时设置
                     * requestTimeout: 15分钟 - 允许LLM处理复杂请求的时间
                     * connectTimeout: 60秒 - 建立连接的最大时间
                     * socketTimeout: 15分钟 - 保持连接的最大时间
                     */
                    timeouts {
                        requestTimeout = 15.minutes
                        connectTimeout = 60.seconds
                        socketTimeout = 15.minutes
                    }
                }

                /**
                 * 配置回退设置
                 * 当请求的提供商未配置时，使用此设置。
                 * 该回退仅适用于非诊断文本能力，不适用于正式影像分析流程。
                 */
                fallback {
                    provider = LLMProvider.DeepSeek
                    model = DeepSeekModels.DeepSeekReasoner
                }
            }
        }
    } else {
        log.warn("DeepSeek provider is disabled: {}", deepSeekSettings.unavailableReason())
    }

    configureSecurity()
    configureHTTP()
    configureWebSockets()
    configureSerialization()
    configureDatabases()
    configureRouting()
}
