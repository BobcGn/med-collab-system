package com.example

import kotlinx.serialization.Serializable

/**
 * 标准响应格式
 */
@Serializable
data class StandardResponse<T>(
    val success: Boolean,
    val code: Int,
    val message: String,
    val data: T?
)

/**
 * 健康检查响应
 */
@Serializable
data class HealthResponse(
    val status: String,
    val service: String,
    val timestamp: Long
)
