package database.entity

import database.table.Departments
import org.jetbrains.exposed.dao.Entity
import org.jetbrains.exposed.dao.EntityClass
import org.jetbrains.exposed.dao.id.EntityID


class DepartmentEntity(id: EntityID<String>) : Entity<String>(id) {
    companion object : EntityClass<String, DepartmentEntity>(Departments)

    var name by Departments.name
    var hospitalId by Departments.hospitalId
    var isActive by Departments.isActive
    var createdAt by Departments.createdAt
    var updatedAt by Departments.updatedAt
}