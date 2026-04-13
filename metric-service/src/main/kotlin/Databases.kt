package com.example

import com.example.toServerJdbcUrl
import database.table.AnalysisResults
import database.table.MedicalImages
import database.table.Reports
import io.ktor.server.application.*
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import java.sql.DriverManager

fun Application.configureDatabases() {
    val databaseUrl = environment.config.property("database.mysql.url").getString()
    val databaseUser = environment.config.property("database.mysql.user").getString()
    val databasePassword = environment.config.property("database.mysql.password").getString()
    val databaseDriver = environment.config.property("database.mysql.driver").getString()

    if (databaseUrl.startsWith("jdbc:mysql:", ignoreCase = true)) {
        ensureDatabaseExists(
            databaseUrl = databaseUrl,
            databaseUser = databaseUser,
            databasePassword = databasePassword,
            databaseDriver = databaseDriver,
        )
    }

    val database = Database.connect(
        url = databaseUrl,
        user = databaseUser,
        password = databasePassword,
        driver = databaseDriver
    )

    transaction(database) {
        SchemaUtils.createMissingTablesAndColumns(
            MedicalImages,
            AnalysisResults,
            Reports,
        )
    }

    log.info("Metric service database initialized with {}", databaseDriver)
}

private fun Application.ensureDatabaseExists(
    databaseUrl: String,
    databaseUser: String,
    databasePassword: String,
    databaseDriver: String,
) {
    val databaseName = extractDatabaseName(databaseUrl)
        ?: throw IllegalArgumentException("数据库URL缺少数据库名称: $databaseUrl")
    require(databaseName.matches(Regex("[A-Za-z0-9_]+"))) {
        "数据库名称不合法: $databaseName"
    }

    val serverJdbcUrl = toServerJdbcUrl(databaseUrl)
    Class.forName(databaseDriver)

    DriverManager.getConnection(serverJdbcUrl, databaseUser, databasePassword).use { connection ->
        connection.createStatement().use { statement ->
            statement.executeUpdate(
                "CREATE DATABASE IF NOT EXISTS `$databaseName` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci",
            )
        }
    }

    log.info("Metric service database ready: {}", databaseName)
}

internal fun extractDatabaseName(jdbcUrl: String): String? {
    val basePart = jdbcUrl.substringBefore('?')
    val slashIndex = basePart.lastIndexOf('/')
    if (slashIndex < 0 || slashIndex == basePart.lastIndex) {
        return null
    }
    return basePart.substring(slashIndex + 1).takeIf { it.isNotBlank() }
}

internal fun toServerJdbcUrl(jdbcUrl: String): String {
    val queryPart = jdbcUrl.substringAfter('?', "")
    val basePart = jdbcUrl.substringBefore('?')
    val slashIndex = basePart.lastIndexOf('/')
    if (slashIndex < 0) {
        return jdbcUrl
    }

    val serverBase = "${basePart.substring(0, slashIndex)}/"
    return if (queryPart.isBlank()) {
        serverBase
    } else {
        "$serverBase?$queryPart"
    }
}
