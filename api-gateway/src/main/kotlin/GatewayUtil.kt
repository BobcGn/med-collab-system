package com.example

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import kotlinx.serialization.Serializable
import java.util.zip.GZIPInputStream

/**
 * 网关工具类
 * 负责请求转发、响应处理等功能
 */
object GatewayUtil {
    val httpClient = HttpClient(CIO) {
        expectSuccess = false  // 不自动抛出异常,由网关处理
    }

    /**
     * 转发请求到目标服务
     */
    suspend fun ApplicationCall.forwardRequest(
        targetUrl: String,
        originalHeaders: Headers
    ) {
        try {
            // 复制请求方法
            val method = request.httpMethod

            // 构建转发请求
            val response = httpClient.request(targetUrl) {
                this.method = method

                // 复制请求头(跳过一些不应该转发的头)
                originalHeaders.forEach { key, values ->
                    if (!shouldSkipHeader(key)) {
                        values.forEach { value ->
                            headers.append(key, value)
                        }
                    }
                }

                // 转发请求体(对于POST/PUT/PATCH请求)
                if (method in listOf(HttpMethod.Post, HttpMethod.Put, HttpMethod.Patch)) {
                    // 直接转发请求体，Content-Type已在headers中
                    setBody(receiveChannel())
                }

                // 添加网关标识头
                headers.append("X-Gateway", "med-collab-gateway")
            }

            // 转发响应头
            response.headers.forEach { key, values ->
                if (!shouldSkipResponseHeader(key)) {
                    values.forEach { value ->
                        this.response.headers.append(key, value)
                    }
                }
            }

            // 转发响应状态
            this.response.status(response.status)

            // 转发响应体
            val bodyBytes = response.readBytes()

            // 处理压缩响应
            val responseBody = if (response.headers.contains(HttpHeaders.ContentEncoding)) {
                decompressGzip(bodyBytes)
            } else {
                bodyBytes
            }

            respondBytes(responseBody)

        } catch (e: Exception) {
            application.environment.log.error("请求转发失败: ${e.message}", e)
            respond(
                HttpStatusCode.BadGateway,
                StandardResponse(
                    success = false,
                    code = 502,
                    message = "服务暂时不可用",
                    data = null
                )
            )
        }
    }

    /**
     * 不应该转发的请求头
     */
    private fun shouldSkipHeader(name: String): Boolean {
        val skipHeaders = listOf(
            HttpHeaders.Host,
            HttpHeaders.ContentLength,
            HttpHeaders.Connection,
            "Keep-Alive"
        )
        return skipHeaders.any { name.equals(it, ignoreCase = true) }
    }

    /**
     * 不应该转发的响应头
     */
    private fun shouldSkipResponseHeader(name: String): Boolean {
        val skipHeaders = listOf(
            HttpHeaders.ContentLength,
            HttpHeaders.Connection,
            "Keep-Alive",
            "Transfer-Encoding",
            // 跳过CORS相关头，由网关统一处理
            "Access-Control-Allow-Origin",
            "Access-Control-Allow-Credentials",
            "Access-Control-Allow-Methods",
            "Access-Control-Allow-Headers",
            "Access-Control-Expose-Headers",
            "Access-Control-Max-Age"
        )
        return skipHeaders.any { name.equals(it, ignoreCase = true) }
    }

    /**
     * 解压GZIP响应
     */
    private fun decompressGzip(data: ByteArray): ByteArray {
        return GZIPInputStream(data.inputStream()).use { it.readBytes() }
    }

    /**
     * 提取JWT令牌
     */
    fun extractToken(authorizationHeader: String?): String? {
        return authorizationHeader?.takeIf { it.startsWith("Bearer ") }?.substring(7)
    }

    /**
     * 验证JWT令牌
     */
    fun validateToken(token: String, jwtUtil: utils.JwtUtil): Boolean {
        return jwtUtil.validateToken(token)
    }
}
