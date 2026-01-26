package database.entity

import database.table.Hospitals
import org.jetbrains.exposed.dao.Entity
import org.jetbrains.exposed.dao.EntityClass
import org.jetbrains.exposed.dao.id.EntityID

class HospitalEntity(id: EntityID<String>) : Entity<String>(id) {
    companion object : EntityClass<String, HospitalEntity>(Hospitals)

    var name by Hospitals.name
    var isActive by Hospitals.isActive
    var createdAt by Hospitals.createdAt
    var updatedAt by Hospitals.updatedAt
}