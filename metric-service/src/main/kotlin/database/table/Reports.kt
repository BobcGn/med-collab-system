package database.table

import org.jetbrains.exposed.dao.id.IdTable
import org.jetbrains.exposed.sql.javatime.CurrentDateTime
import org.jetbrains.exposed.sql.javatime.datetime

object Reports : IdTable<String>("analysis.reports") {
    val hospitalId = varchar("hospital_id", length = 20)
    val patientId = varchar("patient_id", length = 36)
    val analysisIds = text("analysis_ids")
    val reportType = varchar("report_type", length = 30)
    val filePath = varchar("file_path", length = 512).nullable()
    val fileSize = long("file_size").nullable()
    val status = text("status").default("'generating'")
    val errorMessage = text("error_message").nullable()
    val isDeleted = bool("is_deleted").default(false)
    val generatedAt = datetime("generated_at").nullable()
    val createdAt = datetime("created_at").defaultExpression(CurrentDateTime)
    override val id = varchar("id", length = 36).autoIncrement().entityId()
}