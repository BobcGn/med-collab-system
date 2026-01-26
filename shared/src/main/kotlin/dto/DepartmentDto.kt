package dto

sealed class DepartmentDto {
    /**
     * 部门基本信息
     */
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
    data class DepartmentCreate(
        val id: String,
        val hospitalId: String,
        val name: String
    ) : DepartmentDto()

    /**
     * 部门更新数据类
     */
    data class DepartmentUpdate(
        val id: String,
        val name: String? = null,
        val isActive: Boolean? = null
    ) : DepartmentDto()
}
