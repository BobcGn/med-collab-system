package com.example

import ai.koog.ktor.Koog
import ai.koog.prompt.executor.clients.deepseek.DeepSeekModels
import ai.koog.prompt.llm.LLMProvider
import io.ktor.server.application.*
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

fun main(args: Array<String>) {
    io.ktor.server.netty.EngineMain.main(args)
}

fun Application.module() {
    val deepSeekSettings = resolveDeepSeekSettings(environment.config)

    /**
     * 安装Koog插件并配置DeepSeek Agent
     * 参考文档: https://openaidoc.org/koog/ktor-plugin
     */
    if (deepSeekSettings.isConfigured) {
        log.info("DeepSeek provider configured from {}", deepSeekSettings.source)
        install(Koog) {
            llm {
                /**
                 * 配置DeepSeek提供商
                 * 使用环境变量中的API密钥，确保在生产环境中正确设置
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
                 * 当请求的提供商未配置时，使用此设置
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
    configureRouting(deepSeekSettings)
}
