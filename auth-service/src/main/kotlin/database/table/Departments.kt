package database.table

import org.jetbrains.exposed.dao.id.IdTable
import org.jetbrains.exposed.sql.javatime.CurrentDateTime
import org.jetbrains.exposed.sql.javatime.datetime

object Departments : IdTable<kotlin.String>("department") {

    val name = varchar("name", length = 100)
    val hospitalId = varchar("hospital_id", length = 20)
    val isActive = bool("is_active").default(true)
    val createdAt = datetime("created_at").defaultExpression(CurrentDateTime)
    val updatedAt = datetime("updated_at").nullable()
    override val id = varchar("id", length = 20).entityId()
}
