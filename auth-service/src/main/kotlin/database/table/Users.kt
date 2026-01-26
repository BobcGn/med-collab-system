package database.table

import org.jetbrains.exposed.dao.id.IdTable
import org.jetbrains.exposed.sql.javatime.CurrentDateTime
import org.jetbrains.exposed.sql.javatime.datetime


object Users : IdTable<String>("med_auth.user") {
    val hospitalId = varchar("hospital_id", length = 20)
    val deptCode = varchar("dept_code", length = 20)
    val userSeq = varchar("user_seq", length = 20)
    val username = varchar("username", length = 100).nullable()
    val fullName = varchar("full_name", length = 100)
    val passwordHash = varchar("password_hash", length = 255)
    val role = text("role").default("'doctor'")
    val isDeleted = bool("is_deleted").default(false)
    val createdAt = datetime("created_at").defaultExpression(CurrentDateTime)
    val updatedAt = datetime("updated_at").nullable()
    override val id = varchar("id", length = 36).autoIncrement().entityId()
}