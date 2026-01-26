package com.example

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.cors.routing.*

/**
 * HTTP配置
 * 配置CORS等HTTP相关功能
 */
fun Application.configureHTTP() {
    install(CORS) {
        // 允许的方法
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Patch)
        allowMethod(HttpMethod.Delete)
        allowMethod(HttpMethod.Options)

        // 允许的请求头
        allowHeader(HttpHeaders.ContentType)
        allowHeader(HttpHeaders.Authorization)
        allowHeader("X-Requested-With")

        // 允许的凭证
        allowCredentials = true

        // 允许的来源(开发环境可以设置为 *)
        anyHost()  // 生产环境建议设置具体域名
    }
}