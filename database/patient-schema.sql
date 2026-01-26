drop database if exists patient;
create database patient;
use patient;

drop table if exists patient.patients;
create table patient.patients (
                                  id CHAR(36) NOT NULL PRIMARY KEY COMMENT '系统内患者UUID',
                                  hospital_id VARCHAR(20) NOT NULL COMMENT '所属医院',

                                  patient_id VARCHAR(50) NOT NULL COMMENT '医院内部患者ID（病历号）',
                                  name VARCHAR(100) NOT NULL,
                                  gender ENUM('M', 'F', 'Other') NOT NULL,
                                  birth_date DATE,
                                  phone VARCHAR(20),
                                  id_card VARCHAR(30) COMMENT '身份证号（应用层加密）',

                                  is_deleted TINYINT(1) NOT NULL DEFAULT 0,
                                  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                                  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

-- 联合唯一：同一医院不能有重复 patient_id
                                  UNIQUE KEY uk_hospital_patient (hospital_id, patient_id),
                                  INDEX idx_hospital (hospital_id),
                                  INDEX idx_deleted (is_deleted)
)ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;