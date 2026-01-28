package com.example

import io.ktor.server.application.*
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.DatabaseConfig
import java.util.Properties

fun Application.configureDatabases() {
    val databaseUrl = environment.config.property("database.mysql.url").getString()
    val databaseUser = environment.config.property("database.mysql.user").getString()
    val databasePassword = environment.config.property("database.mysql.password").getString()
    val databaseDriver = environment.config.property("database.mysql.driver").getString()

    // 配置数据库连接属性，避免查询 information_schema.KEYWORDS 表
    val connectionProperties = Properties().apply {
        put("useInformationSchema", "false")
        put("nullDatabaseMeansCurrent", "true")
        put("useUnicode", "true")
        put("characterEncoding", "UTF-8")
    }

    // 连接数据库
    Database.connect(
        url = databaseUrl,
        user = databaseUser,
        password = databasePassword,
        driver = databaseDriver,
        setupConnection = { connection ->
            // 禁用自动提交
            connection.autoCommit = false
        }
    )
}
