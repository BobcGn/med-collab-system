package dto

sealed class HospitalDto {
    /**
     * 医院基本信息
     */
    data class HospitalInfo(
        val id: String,
        val name: String,
        val isActive: Boolean,
        val createdAt: String,
        val updatedAt: String?
    ) : HospitalDto()

    /**
     * 医院创建数据类
     */
    data class HospitalCreate(
        val id: String,
        val name: String
    ) : HospitalDto()

    /**
     * 医院更新数据类
     */
    data class HospitalUpdate(
        val id: String,
        val name: String? = null,
        val isActive: Boolean? = null
    ) : HospitalDto()
}
