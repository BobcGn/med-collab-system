package com.example

import io.ktor.server.application.*

fun main(args: Array<String>) {
    io.ktor.server.netty.EngineMain.main(args)
}

fun Application.module() {
    // 1. 配置HTTP(CORS等)
    configureHTTP()

    // 2. 配置JSON序列化
    configureSerialization()

    // 3. 配置JWT认证
    configureSecurity()

    // 4. 配置路由转发
    configureRouting()

    // 5. 配置监控指标
    configureMonitoring()
}