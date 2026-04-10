package database.table

import org.jetbrains.exposed.dao.id.IdTable
import org.jetbrains.exposed.sql.javatime.CurrentDateTime
import org.jetbrains.exposed.sql.javatime.datetime
import java.util.UUID

object MedicalImages : IdTable<String>("medical_images") {
    val hospitalId = varchar("hospital_id", length = 20)
    val patientId = varchar("patient_id", length = 36)
    val patientName = varchar("patient_name", length = 100)
    val imageType = varchar("image_type", length = 20)
    val filePath = varchar("file_path", length = 512)
    val fileSize = long("file_size").nullable()
    val uploadTime = datetime("upload_time").defaultExpression(CurrentDateTime)
    val status = text("status").default("uploaded")
    val isDeleted = bool("is_deleted").default(false)
    override val id = varchar("id", length = 36).entityId()

    fun generateId(): String = UUID.randomUUID().toString()
}
