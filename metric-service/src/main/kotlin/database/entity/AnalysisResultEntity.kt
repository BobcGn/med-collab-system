package database.entity

import database.table.AnalysisResults
import org.jetbrains.exposed.dao.Entity
import org.jetbrains.exposed.dao.EntityClass
import org.jetbrains.exposed.dao.id.EntityID

class AnalysisResultEntity(id: EntityID<String>) : Entity<String>(id) {
    companion object : EntityClass<String, AnalysisResultEntity>(AnalysisResults)

    var hospitalId by AnalysisResults.hospitalId
    var imageId by AnalysisResults.imageId
    var patientId by AnalysisResults.patientId
    var patientName by AnalysisResults.patientName
    var metrics by AnalysisResults.metrics
    var status by AnalysisResults.status
    var errorMessage by AnalysisResults.errorMessage
    var isDeleted by AnalysisResults.isDeleted
    var createdAt by AnalysisResults.createdAt
    var completedAt by AnalysisResults.completedAt


}