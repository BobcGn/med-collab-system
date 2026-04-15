package utils

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MySqlDatabaseBootstrapTest {
    @Test
    fun `should append tidb compatibility parameter for mysql jdbc url`() {
        val normalizedUrl = toTiDbCompatibleJdbcUrl("jdbc:mysql://localhost:4000/analysis")

        assertEquals(
            "jdbc:mysql://localhost:4000/analysis?useInformationSchema=false",
            normalizedUrl,
        )
    }

    @Test
    fun `should override existing tidb compatibility parameter`() {
        val normalizedUrl = toTiDbCompatibleJdbcUrl(
            "jdbc:mysql://localhost:4000/analysis?useSSL=false&useInformationSchema=true",
        )

        assertEquals(
            "jdbc:mysql://localhost:4000/analysis?useSSL=false&useInformationSchema=false",
            normalizedUrl,
        )
    }

    @Test
    fun `should detect mysql jdbc url`() {
        assertTrue(isMySqlJdbcUrl("jdbc:mysql://localhost:4000/analysis"))
        assertFalse(isMySqlJdbcUrl("jdbc:h2:file:./.data/metric-service"))
    }

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
