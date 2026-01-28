package com.example

import io.ktor.server.config.*

object ServiceConfig {
    private var config: ApplicationConfig? = null

    fun initConfig(applicationConfig: ApplicationConfig) {
        config = applicationConfig
    }

    val services by lazy {
        mapOf(
            "auth" to ServiceConfigItem(
                name = "认证服务",
                baseUrl = config?.config("services.auth")?.property("baseUrl")?.getString() ?: "http://localhost:8081",
                pathPrefix = config?.config("services.auth")?.property("pathPrefix")?.getString() ?: "/api/auth"
            ),
            "patient" to ServiceConfigItem(
                name = "患者服务",
                baseUrl = config?.config("services.patient")?.property("baseUrl")?.getString() ?: "http://localhost:8082",
                pathPrefix = config?.config("services.patient")?.property("pathPrefix")?.getString() ?: "/patient"
            ),
            "metric" to ServiceConfigItem(
                name = "指标服务",
                baseUrl = config?.config("services.metric")?.property("baseUrl")?.getString() ?: "http://localhost:8083",
                pathPrefix = config?.config("services.metric")?.property("pathPrefix")?.getString() ?: "/metric"
            ),
            "report" to ServiceConfigItem(
                name = "报告服务",
                baseUrl = config?.config("services.report")?.property("baseUrl")?.getString() ?: "http://localhost:8084",
                pathPrefix = config?.config("services.report")?.property("pathPrefix")?.getString() ?: "/report"
            )
        )
    }
    /**
     * 根据路径前缀获取对应的服务配置
     */
    fun getServiceByPath(path: String): ServiceConfigItem? {
        return services.values.find { path.startsWith(it.pathPrefix) }
    }

    /**
     * 检查路径是否需要认证(白名单)
     */
    fun isPublicPath(path: String): Boolean {
        val publicPaths = listOf(
            "/api/auth/login",
            "/api/auth/register"
        )
        return publicPaths.any { path.startsWith(it) }
    }
}

data class ServiceConfigItem(
    val name: String,
    val baseUrl: String,
    val pathPrefix: String
)