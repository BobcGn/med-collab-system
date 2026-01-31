package database.table

import org.jetbrains.exposed.dao.id.IdTable
import org.jetbrains.exposed.sql.javatime.CurrentDateTime
import org.jetbrains.exposed.sql.javatime.date
import org.jetbrains.exposed.sql.javatime.datetime
import java.util.UUID

object Patients : IdTable<String>("patient.patients") {
    val hospitalId = varchar("hospital_id", length = 20)
    val patientId = varchar("patient_id", length = 50)
    val name = varchar("name", length = 100)
    val gender = text("gender")
    val birthDate = date("birth_date").nullable()
    val phone = varchar("phone", length = 20).nullable()
    val idCard = varchar("id_card", length = 30).nullable()
    val department = varchar("department", length = 50)
    val attendingDoctorId = varchar("attending_doctor_id", length = 36)
    val allergies = text("allergies").nullable()
    val medicalHistory = text("medical_history").nullable()
    val familyHistory = text("family_history").nullable()
    val chiefComplaint = text("chief_complaint").nullable()
    val heightCm = short("height_cm").nullable()
    val weightKg = decimal("weight_kg", precision = 5, scale = 2).nullable()
    val bloodType = text("blood_type").nullable().default("'Unknown'")
    val status = text("status").default("'active'")
    val firstVisitDate = datetime("first_visit_date").nullable()
    val lastVisitDate = datetime("last_visit_date").nullable()
    val isDeleted = bool("is_deleted").default(false)
    val createdAt =  datetime("created_at").defaultExpression(CurrentDateTime)
    val updatedAt = datetime("updated_at").nullable()

    override val id = varchar("id", length = 36).entityId()

    // 添加 UUID 生成函数
    fun generateId(): String = UUID.randomUUID().toString()
}