package com.example

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DatabasesTest {
    @Test
    fun `should extract database name from mysql jdbc url`() {
        val databaseName = extractDatabaseName(
            "jdbc:mysql://localhost:4000/analysis?useSSL=false&serverTimezone=UTC",
        )

        assertEquals("analysis", databaseName)
    }

    @Test
    fun `should return null when jdbc url misses database name`() {
        val databaseName = extractDatabaseName("jdbc:mysql://localhost:4000/")

        assertNull(databaseName)
    }

    @Test
    fun `should convert jdbc url to server level url`() {
        val serverJdbcUrl = toServerJdbcUrl(
            "jdbc:mysql://localhost:4000/analysis?useSSL=false&serverTimezone=UTC",
        )

        assertEquals(
            "jdbc:mysql://localhost:4000/?useSSL=false&serverTimezone=UTC",
            serverJdbcUrl,
        )
    }
}
