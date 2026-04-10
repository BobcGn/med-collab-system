package com.example.service

import com.example.database.repository.AnalysisRepository
import com.example.database.repository.MedicalImageRepository
import com.example.database.repository.ReportRepository
import dto.AnalysisResultDto
import dto.MedicalImageDto
import dto.ReportDto

/**
 * 指标与报表服务
 * 提供图像分析、指标计算和报表生成功能
 */
class MetricService(
    private val analysisRepository: AnalysisRepository,
    private val medicalImageRepository: MedicalImageRepository,
    private val reportRepository: ReportRepository
) {
    
    /**
     * 创建医学影像
     * @param image 医学影像创建请求
     * @return 创建结果
     */
    suspend fun createMedicalImage(image: MedicalImageDto.MedicalImageCreate) = 
        medicalImageRepository.createImage(image)
    
    /**
     * 获取所有医学影像
     * @return 医学影像列表
     */
    suspend fun getAllMedicalImages() = 
        medicalImageRepository.getAllImages()
    
    /**
     * 根据患者姓名查询医学影像
     * @param patientName 患者姓名
     * @return 医学影像列表
     */
    suspend fun getMedicalImagesByPatientName(patientName: String) = 
        medicalImageRepository.findImageByPatientName(patientName)
    
    /**
     * 删除医学影像
     * @param id 影像ID
     * @return 删除结果
     */
    suspend fun deleteMedicalImage(id: String) = 
        medicalImageRepository.deleteImage(id)
    
    /**
     * 创建分析结果
     * @param result 分析结果创建请求
     * @return 创建结果
     */
    suspend fun createAnalysisResult(result: AnalysisResultDto.AnalysisResultComplete) = 
        analysisRepository.createResult(result)
    
    /**
     * 获取所有分析结果
     * @return 分析结果列表
     */
    suspend fun getAllAnalysisResults() = 
        analysisRepository.getAllResults()
    
    /**
     * 根据患者姓名查询分析结果
     * @param patientName 患者姓名
     * @return 分析结果列表
     */
    suspend fun getAnalysisResultsByPatientName(patientName: String) = 
        analysisRepository.getResultsByPatientName(patientName)
    
    /**
     * 创建报表
     * @param report 报表创建请求
     * @return 创建结果
     */
    suspend fun createReport(report: ReportDto.ReportComplete) = 
        reportRepository.createReport(report)
    
    /**
     * 获取所有报表
     * @return 报表列表
     */
    suspend fun getAllReports() = 
        reportRepository.getAllReports()
    
    /**
     * 根据患者姓名查询报表
     * @param patientName 患者姓名
     * @return 报表列表
     */
    suspend fun getReportsByPatientName(patientName: String) = 
        reportRepository.getReportsByPatientName(patientName)

    /**
     * 根据报表ID查询报表
     * @param reportId 报表ID
     * @return 报表详情
     */
    suspend fun getReportById(reportId: String) =
        reportRepository.getReportById(reportId)
}
