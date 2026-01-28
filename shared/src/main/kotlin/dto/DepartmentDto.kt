package dto

import kotlinx.serialization.Serializable

sealed class DepartmentDto {
    /**
     * 部门基本信息
     */
    @Serializable
    data class DepartmentInfo(
        val id: String,
        val hospitalId: String,
        val name: String,
        val isActive: Boolean,
        val createdAt: String,
        val updatedAt: String?
    ) : DepartmentDto()

    /**
     * 部门创建数据类
     */
    @Serializable
    data class DepartmentCreate(
        val id: String,
        val hospitalId: String,
        val name: String
    ) : DepartmentDto()

    /**
     * 部门更新数据类
     */
    @Serializable
    data class DepartmentUpdate(
        val name: String? = null,
        val isActive: Boolean? = null
    ) : DepartmentDto()
}


