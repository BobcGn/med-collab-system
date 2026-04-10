package database.table

import org.jetbrains.exposed.dao.id.IdTable
import org.jetbrains.exposed.sql.javatime.CurrentDateTime
import org.jetbrains.exposed.sql.javatime.datetime
import java.util.UUID

object AnalysisResults : IdTable<String>("analysis_results") {
    val hospitalId = varchar("hospital_id", length = 20)
    val imageId = varchar("image_id", length = 36)
    val patientId = varchar("patient_id", length = 36)
    val patientName = varchar("patient_name", length = 100)
    val metrics = text("metrics")
    val status = text("status").default("pending")
    val errorMessage = text("error_message").nullable()
    val isDeleted = bool("is_deleted").default(false)
    val createdAt = datetime("created_at").defaultExpression(CurrentDateTime)
    val completedAt = datetime("completed_at").nullable()
    override val id = varchar("id", length = 36).entityId()
    fun generateId(): String = UUID.randomUUID().toString()
}
