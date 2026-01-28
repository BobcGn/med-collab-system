package database.entity

import database.table.Users
import dto.UserDto
import org.jetbrains.exposed.dao.Entity
import org.jetbrains.exposed.dao.EntityClass
import org.jetbrains.exposed.dao.id.EntityID

class UserEntity(id: EntityID<String>) : Entity<String>(id) {
    companion object : EntityClass<String, UserEntity>(Users)

    var hospitalId by Users.hospitalId
    var deptCode by Users.deptCode
    var userSeq by Users.userSeq
    var fullName by Users.fullName
    var passwordHash by Users.passwordHash
    var role by Users.role
    var isDeleted by Users.isDeleted
    var isFrozen by Users.isFrozen  // 添加冻结状态
    var createdAt by Users.createdAt
    var updatedAt by Users.updatedAt

    // 计算属性: username (管理员为 admin-{user_seq}，普通用户为 {hospitalId}-{deptCode}-{userSeq})
    val username: String
        get() = if (hospitalId == null) "admin-$userSeq" else "$hospitalId-$deptCode-$userSeq"

    fun UserEntity.toUserInfo() = UserDto.UserInfo(
        id = id.value,
        hospitalId = hospitalId,
        deptCode = deptCode,
        userSeq = userSeq,
        username = username,
        fullName = fullName,
        role = role,
        createdAt = createdAt.toString(),
        updatedAt = updatedAt?.toString(),
        isDeleted = isDeleted,
        isFrozen = isFrozen
    )
}