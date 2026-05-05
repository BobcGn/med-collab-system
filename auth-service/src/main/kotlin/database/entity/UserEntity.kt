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

    // 计算属性: username (含角色前缀)
    // admin -> ADMIN-{userSeq}
    // doctor -> DR-{hospitalId}-{deptCode}-{userSeq}
    // nurse -> NR-{hospitalId}-{deptCode}-{userSeq}
    // receptionist -> RC-{hospitalId}-{deptCode}-{userSeq}
    val username: String
        get() {
            if (hospitalId == null) return "ADMIN-$userSeq"
            val prefix = when (role) {
                "doctor" -> "DR"
                "nurse" -> "NR"
                "receptionist" -> "RC"
                else -> "USR"
            }
            return "$prefix-$hospitalId-$deptCode-$userSeq"
        }

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