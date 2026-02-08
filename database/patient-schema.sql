-- 安全起见，先删除旧库（生产环境慎用）
DROP DATABASE IF EXISTS patient;
CREATE DATABASE patient;

USE patient;

CREATE TABLE patients (
-- 主键 & 多租户
      id CHAR(36) NOT NULL PRIMARY KEY COMMENT '系统内患者UUID',
      hospital_id VARCHAR(50) NOT NULL COMMENT '所属医院编码，如 BJH',

-- 医院内部标识
      patient_id VARCHAR(50) NOT NULL COMMENT '医院病历号（HIS 系统ID）',

-- 基础身份信息
      name VARCHAR(100) NOT NULL COMMENT '患者姓名',
      gender ENUM('M', 'F') NOT NULL COMMENT '性别',
      birth_date DATE COMMENT '出生日期',
      phone VARCHAR(20) COMMENT '手机号（应用层加密存储）',
      id_card VARCHAR(30) COMMENT '身份证号（应用层加密存储）',

-- 医疗关键信息
      department VARCHAR(50) NOT NULL COMMENT '就诊科室，如 放射科、心内科',
      attending_doctor_id VARCHAR(36) NOT NULL COMMENT '主治医生ID，关联 auth_dev.users.id',

      allergies TEXT COMMENT '过敏史，JSON数组格式：["青霉素", "海鲜"]',
      medical_history TEXT COMMENT '既往病史',
      family_history TEXT COMMENT '家族病史',
      chief_complaint TEXT COMMENT '主诉（当前症状）',

      height_cm SMALLINT UNSIGNED COMMENT '身高（厘米）',
      weight_kg DECIMAL(5,2) COMMENT '体重（公斤）',
      blood_type ENUM('A', 'B', 'AB', 'O', 'Unknown') DEFAULT 'Unknown' COMMENT '血型',

-- 系统与流程字段
      status ENUM('Active', 'Discharged', 'Deceased') NOT NULL DEFAULT 'Active' COMMENT '患者状态',
      first_visit_date DATETIME COMMENT '首次就诊时间',
      last_visit_date DATETIME COMMENT '最近就诊时间',

-- 软删除 & 时间戳
      is_deleted TINYINT(1) NOT NULL DEFAULT 0,
      created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
      updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

-- 约束与索引
      UNIQUE KEY uk_hospital_patient (hospital_id, patient_id),
      INDEX idx_hospital (hospital_id),
      INDEX idx_attending_doctor (attending_doctor_id),
      INDEX idx_status (status),
      INDEX idx_last_visit (last_visit_date),
      INDEX idx_deleted (is_deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;