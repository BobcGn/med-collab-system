package database.table

import org.jetbrains.exposed.dao.id.IdTable
import org.jetbrains.exposed.sql.javatime.CurrentDateTime
import org.jetbrains.exposed.sql.javatime.datetime

object MedicalImages : IdTable<String>("analysis.medical_images") {
    val hospitalId = varchar("hospital_id", length = 20)
    val patientId = varchar("patient_id", length = 36)
    val imageType = varchar("image_type", length = 20)
    val filePath = varchar("file_path", length = 512)
    val fileSize = long("file_size").nullable()
    val uploadTime = datetime("upload_time").defaultExpression(CurrentDateTime)
    val status = text("status").default("'uploaded'")
    val isDeleted = bool("is_deleted").default(false)
    override val id = varchar("id", length = 36).autoIncrement().entityId()
}