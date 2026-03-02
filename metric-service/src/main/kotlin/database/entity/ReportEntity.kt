package database.entity

import database.table.Reports
import org.jetbrains.exposed.dao.Entity
import org.jetbrains.exposed.dao.EntityClass
import org.jetbrains.exposed.dao.id.EntityID

class ReportEntity(id: EntityID<String>) : Entity<String>(id) {
    companion object : EntityClass<String, ReportEntity>(Reports)

    var hospitalId by Reports.hospitalId
    var patientId by Reports.patientId
    var patientName by Reports.patientName
    var analysisIds by Reports.analysisIds
    var reportType by Reports.reportType
    var filePath by Reports.filePath
    var fileSize by Reports.fileSize
    var status by Reports.status
    var errorMessage by Reports.errorMessage
    var isDeleted by Reports.isDeleted
    var generatedAt by Reports.generatedAt
    var createdAt by Reports.createdAt
}