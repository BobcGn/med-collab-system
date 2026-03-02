-- 创建数据库
drop database if exists analysis;
CREATE DATABASE IF NOT EXISTS analysis;
USE analysis;

-- 医疗图像元数据表
DROP TABLE IF EXISTS analysis.medical_images;
CREATE TABLE analysis.medical_images (
     id CHAR(36) NOT NULL PRIMARY KEY,
     hospital_id VARCHAR(20) NOT NULL,
     patient_id CHAR(36) NOT NULL COMMENT '关联 patient_dev.patients.id',
     patient_name VARCHAR(100) NOT NULL COMMENT '患者姓名',
     image_type VARCHAR(20) NOT NULL COMMENT 'CT/MRI/XRAY/ULTRASOUND',
     file_path VARCHAR(512) NOT NULL COMMENT '对象存储路径，如 s3://bucket/xxx.dcm',
     file_size BIGINT,
     upload_time DATETIME DEFAULT CURRENT_TIMESTAMP,

     status ENUM('uploaded', 'processing', 'completed', 'failed') NOT NULL DEFAULT 'uploaded',
     is_deleted TINYINT(1) NOT NULL DEFAULT 0,

     INDEX idx_patient (patient_id),
     INDEX idx_status (status),
     INDEX idx_hospital (hospital_id),
     INDEX idx_deleted (is_deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 模型分析结果表
DROP TABLE IF EXISTS analysis.analysis_results;
CREATE TABLE analysis.analysis_results (
    id CHAR(36) NOT NULL PRIMARY KEY,
    hospital_id VARCHAR(20) NOT NULL,
    image_id CHAR(36) NOT NULL COMMENT '关联 medical_images.id',
    patient_id CHAR(36) NOT NULL COMMENT '冗余字段，加速查询',
    patient_name VARCHAR(100) NOT NULL COMMENT '患者姓名',
    metrics JSON NOT NULL COMMENT '模型输出的结构化指标',
    status ENUM('pending', 'running', 'success', 'failed') NOT NULL DEFAULT 'pending',
    error_message TEXT,

    is_deleted TINYINT(1) NOT NULL DEFAULT 0,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    completed_at DATETIME,

    INDEX idx_image (image_id),
    INDEX idx_patient (patient_id),
    INDEX idx_status (status),
    INDEX idx_hospital (hospital_id),
    INDEX idx_deleted (is_deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 报表记录表
DROP TABLE IF EXISTS analysis.reports;
CREATE TABLE analysis.reports (
    id CHAR(36) NOT NULL PRIMARY KEY,
    hospital_id VARCHAR(20) NOT NULL,
    patient_id CHAR(36) NOT NULL,
    patient_name VARCHAR(100) NOT NULL COMMENT '患者姓名',
    analysis_ids JSON NOT NULL COMMENT '关联的 analysis_results.id 列表',
    report_type VARCHAR(30) NOT NULL COMMENT 'daily/weekly/final/custom',
    file_path VARCHAR(512) COMMENT '生成的 PDF 路径',
    file_size BIGINT,

    status ENUM('generating', 'completed', 'failed') NOT NULL DEFAULT 'generating',
    error_message TEXT,

    is_deleted TINYINT(1) NOT NULL DEFAULT 0,
    generated_at DATETIME,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,

    INDEX idx_patient (patient_id),
    INDEX idx_status (status),
    INDEX idx_hospital (hospital_id),
    INDEX idx_deleted (is_deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;