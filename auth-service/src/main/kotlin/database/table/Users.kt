package database.table

import org.jetbrains.exposed.dao.id.IdTable
import org.jetbrains.exposed.sql.javatime.CurrentDateTime
import org.jetbrains.exposed.sql.javatime.datetime
import java.util.UUID


object Users : IdTable<String>("med_auth.user") {
    val hospitalId = varchar("hospital_id", length = 20).nullable()
    val deptCode = varchar("dept_code", length = 20).nullable()
    val userSeq = varchar("user_seq", length = 20)
    val fullName = varchar("full_name", length = 100)
    val passwordHash = varchar("password_hash", length = 255)
    val role = varchar("role", length = 50).default("doctor")
    val isDeleted = bool("is_deleted").default(false)
    val isFrozen = bool("is_frozen").default(false)  // 添加冻结状态
    val createdAt = datetime("created_at").defaultExpression(CurrentDateTime)
    val updatedAt = datetime("updated_at").nullable()

    override val id = varchar("id", length = 36).entityId()

    // 添加 UUID 生成函数
    fun generateId(): String = UUID.randomUUID().toString()
}