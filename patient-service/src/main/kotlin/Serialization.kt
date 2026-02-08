package com.example

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.jetbrains.exposed.sql.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule

fun Application.configureSerialization() {
    install(ContentNegotiation) {
        json(Json {
            // 忽略未知字段
            ignoreUnknownKeys = true
            // 允许宽松的JSON解析
            isLenient = true
            // 允许结构化对象
            encodeDefaults = true
            // 明确的null处理
            explicitNulls = false
        })
    }
}
