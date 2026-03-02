package database.entity

import database.table.MedicalImages
import org.jetbrains.exposed.dao.Entity
import org.jetbrains.exposed.dao.EntityClass
import org.jetbrains.exposed.dao.id.EntityID

class MedicalImageEntity(id: EntityID<String>) : Entity<String>(id) {
    companion object : EntityClass<String, MedicalImageEntity>(MedicalImages)

    var hospitalId by MedicalImages.hospitalId
    var patientId by MedicalImages.patientId
    var imageType by MedicalImages.imageType
    var filePath by MedicalImages.filePath
    var fileSize by MedicalImages.fileSize
    var uploadTime by MedicalImages.uploadTime
    var status by MedicalImages.status
    var isDeleted by MedicalImages.isDeleted
}