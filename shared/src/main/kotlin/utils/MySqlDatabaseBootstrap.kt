package utils

import java.sql.DriverManager

fun isMySqlJdbcUrl(jdbcUrl: String): Boolean =
    jdbcUrl.startsWith("jdbc:mysql:", ignoreCase = true)

fun toTiDbCompatibleJdbcUrl(jdbcUrl: String): String {
    if (!isMySqlJdbcUrl(jdbcUrl)) {
        return jdbcUrl
    }

    return appendJdbcUrlParameterIfMissing(
        jdbcUrl = jdbcUrl,
        parameterName = "useInformationSchema",
        parameterValue = "false",
    )
}

fun ensureMySqlDatabaseExists(
    databaseUrl: String,
    databaseUser: String,
    databasePassword: String,
    databaseDriver: String,
): String {
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

    return databaseName
}

fun extractDatabaseName(jdbcUrl: String): String? {
    val basePart = jdbcUrl.substringBefore('?')
    val slashIndex = basePart.lastIndexOf('/')
    if (slashIndex < 0 || slashIndex == basePart.lastIndex) {
        return null
    }
    return basePart.substring(slashIndex + 1).takeIf { it.isNotBlank() }
}

fun toServerJdbcUrl(jdbcUrl: String): String {
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

private fun appendJdbcUrlParameterIfMissing(
    jdbcUrl: String,
    parameterName: String,
    parameterValue: String,
): String {
    val basePart = jdbcUrl.substringBefore('?')
    val queryPart = jdbcUrl.substringAfter('?', "")
    if (queryPart.isBlank()) {
        return "$basePart?$parameterName=$parameterValue"
    }

    val normalizedParameters = queryPart
        .split('&')
        .filter { it.isNotBlank() }
        .filterNot { it.substringBefore('=').equals(parameterName, ignoreCase = true) }
        .toMutableList()
    normalizedParameters += "$parameterName=$parameterValue"
    return "$basePart?${normalizedParameters.joinToString("&")}"
}
