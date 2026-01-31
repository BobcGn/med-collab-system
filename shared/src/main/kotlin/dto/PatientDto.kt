package dto

import enums.BloodType
import enums.Gender
import enums.Status
import kotlinx.serialization.Serializable

@Serializable
sealed class PatientDto {
    /**
     * 患者创建数据类
     */
    @Serializable
    data class CreatePatient(
        val hospitalId: String,
        val patientId: String,
        val name: String,
        val gender: Gender,
        val birthDate: String,
        val phone: String? = null,
        val idCard: String? = null,
        val department: String,
        val attendingDoctorId: String,
        val allergies: String? = null,
        val medicalHistory: String? = null,
        val familyHistory: String? = null,
        val chiefComplaint: String? = null,
        val heightCm: Int? = null,
        val weightKg: Float? = null,
        val bloodType: BloodType = BloodType.Unknown,
        val status: Status = Status.Active,
        val firstVisitDate: String? = null
    ) : PatientDto()

    /**
     * 患者更新数据类
     */
    @Serializable
    data class UpdatePatient(
        val id: String,
        val name: String? = null,
        val phone: String? = null,
        val idCard: String? = null,
        val department: String? = null,
        val attendingDoctorId: String? = null,
        val allergies: String? = null,
        val medicalHistory: String? = null,
        val familyHistory: String? = null,
        val chiefComplaint: String? = null,
        val heightCm: Int? = null,
        val weightKg: Float? = null,
        val bloodType: BloodType? = null,
        val status: Status? = null,
        val lastVisitDate: String? = null
    ) : PatientDto()

    /**
     * 患者详情数据类
     */
    @Serializable
    data class PatientInfo(
        val id: String,
        val hospitalId: String,
        val patientId: String,
        val name: String,
        val gender: Gender,
        val birthDate: String? = null,
        val phone: String? = null,
        val idCard: String? = null,
        val department: String,
        val attendingDoctorId: String,
        val attendingDoctorName: String? = null,
        val allergies: String? = null,
        val medicalHistory: String? = null,
        val familyHistory: String? = null,
        val chiefComplaint: String? = null,
        val heightCm: Int? = null,
        val weightKg: Float? = null,
        val bloodType: BloodType = BloodType.Unknown,
        val status: Status = Status.Active,
        val firstVisitDate: String? = null,
        val lastVisitDate: String? = null,
        val createdAt: String,
        val updatedAt: String? = null,
        val isDeleted: Boolean = false
    ) : PatientDto()

    /**
     * 患者列表项数据类（简化版）
     */
    @Serializable
    data class PatientListItem(
        val id: String,
        val hospitalId: String,
        val patientId: String,
        val name: String,
        val gender: Gender,
        val age: Int? = null,
        val department: String,
        val attendingDoctorId: String,
        val attendingDoctorName: String? = null,
        val bloodType: BloodType,
        val status: Status,
        val lastVisitDate: String? = null,
        val createdAt: String
    ) : PatientDto()

    /**
     * 患者搜索参数
     */
    @Serializable
    data class PatientSearchParams(
        val hospitalId: String? = null,
        val department: String? = null,
        val attendingDoctorId: String? = null,
        val status: Status? = null,
        val keyword: String? = null, // 搜索姓名或病历号
        val bloodType: BloodType? = null
    ) : PatientDto()

    /**
     * 患者分页查询响应
     */
    @Serializable
    data class PatientPageResponse(
        val patients: List<PatientListItem>,
        val total: Int,
        val page: Int,
        val size: Int
    ) : PatientDto()

    /**
     * 患者统计信息
     */
    @Serializable
    data class PatientStatistics(
        val total: Int,
        val byStatus: Map<String, Int>,
        val byDepartment: Map<String, Int>,
        val byBloodType: Map<String, Int>,
        val recentPatients: List<PatientListItem>
    ) : PatientDto()
}