package com.example

object ServiceConfig {
    val services = mapOf(
        "auth" to ServiceConfigItem(
            name = "认证服务",
            baseUrl = "http://localhost:8081",
            pathPrefix = "/auth"
        ),
        "patient" to ServiceConfigItem(
            name = "患者服务",
            baseUrl = "http://localhost:8082",
            pathPrefix = "/patient"
        ),
        "metric" to ServiceConfigItem(
            name = "指标服务",
            baseUrl = "http://localhost:8083",
            pathPrefix = "/metric"
        ),
        "report" to ServiceConfigItem(
            name = "报告服务",
            baseUrl = "http://localhost:8084",
            pathPrefix = "/report"
        )
    )
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
            "/auth/login",
            "/auth/register"
        )
        return publicPaths.any { path.startsWith(it) }
    }
}

data class ServiceConfigItem(
    val name: String,
    val baseUrl: String,
    val pathPrefix: String
)