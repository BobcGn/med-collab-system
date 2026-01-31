package com.example.database.entity

import database.table.Patients
import dto.PatientDto
import enums.BloodType
import enums.Gender
import enums.Status
import org.jetbrains.exposed.dao.Entity
import org.jetbrains.exposed.dao.EntityClass
import org.jetbrains.exposed.dao.id.EntityID
import java.time.Period

class PatientEntity(id: EntityID<String>) : Entity<String>(id) {
    companion object : EntityClass<String, PatientEntity>(Patients)

    var hospitalId by Patients.hospitalId
    var patientId by Patients.patientId
    var name by Patients.name
    var gender by Patients.gender
    var birthDate by Patients.birthDate
    var phone by Patients.phone
    var idCard by Patients.idCard
    var department by Patients.department
    var attendingDoctorId by Patients.attendingDoctorId
    var allergies by Patients.allergies
    var medicalHistory by Patients.medicalHistory
    var familyHistory by Patients.familyHistory
    var chiefComplaint by Patients.chiefComplaint
    var heightCm by Patients.heightCm
    var weightKg by Patients.weightKg
    var bloodType by Patients.bloodType
    var status by Patients.status
    var firstVisitDate by Patients.firstVisitDate
    var lastVisitDate by Patients.lastVisitDate
    var isDeleted by Patients.isDeleted
    var createdAt by Patients.createdAt
    var updatedAt by Patients.updatedAt

    /**
     * 转换为 PatientInfo DTO
     */
    fun toPatientInfo(): PatientDto.PatientInfo {
        return PatientDto.PatientInfo(
            id = id.value,
            hospitalId = hospitalId,
            patientId = patientId,
            name = name,
            gender = when (gender.lowercase()) {
                "m" -> Gender.M
                "f" -> Gender.F
                else -> throw IllegalArgumentException("Invalid gender: $gender")
            },
            birthDate = birthDate?.toString(),
            phone = phone,
            idCard = idCard,
            department = department,
            attendingDoctorId = attendingDoctorId,
            attendingDoctorName = null, // TODO: 从用户服务获取医生姓名
            allergies = allergies,
            medicalHistory = medicalHistory,
            familyHistory = familyHistory,
            chiefComplaint = chiefComplaint,
            heightCm = heightCm?.toInt(),
            weightKg = weightKg?.toFloat(),
            bloodType = try {
                BloodType.valueOf(bloodType!!)
            } catch (e: Exception) {
                BloodType.Unknown
            },
            status = when (status.lowercase()) {
                "active" -> Status.Active
                "discharged" -> Status.Discharged
                "deceased" -> Status.Deceased
                else -> Status.Active
            },
            firstVisitDate = firstVisitDate?.toString(),
            lastVisitDate = lastVisitDate?.toString(),
            createdAt = createdAt.toString(),
            updatedAt = updatedAt?.toString(),
            isDeleted = isDeleted
        )
    }

    /**
     * 转换为 PatientListItem DTO
     */
    fun toPatientListItem(): PatientDto.PatientListItem {
        val age = birthDate?.let {
            Period.between(it, java.time.LocalDate.now()).years
        }

        return PatientDto.PatientListItem(
            id = id.value,
            hospitalId = hospitalId,
            patientId = patientId,
            name = name,
            gender = when (gender.lowercase()) {
                "m" -> Gender.M
                "f" -> Gender.F
                else -> Gender.M
            },
            age = age,
            department = department,
            attendingDoctorId = attendingDoctorId,
            attendingDoctorName = null, // TODO: 从用户服务获取医生姓名
            bloodType = try {
                BloodType.valueOf(bloodType!!)
            } catch (e: Exception) {
                BloodType.Unknown
            },
            status = when (status.lowercase()) {
                "active" -> Status.Active
                "discharged" -> Status.Discharged
                "deceased" -> Status.Deceased
                else -> Status.Active
            },
            lastVisitDate = lastVisitDate?.toString(),
            createdAt = createdAt.toString()
        )
    }
}