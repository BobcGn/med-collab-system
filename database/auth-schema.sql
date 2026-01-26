drop database if exists med_auth;
create database med_auth;
use med_auth;

-- 创建医院表
drop table if exists med_auth.hospital;
create table med_auth.hospital (
    id VARCHAR(20) NOT NULL PRIMARY KEY COMMENT '医院编码，如 BJH',
    name VARCHAR(100) NOT NULL COMMENT '医院名称',
    is_active TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否启用',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_hospital_active (is_active)
)ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 创建科室表
drop table if exists med_auth.department;
create table med_auth.department (
    id VARCHAR(20) NOT NULL COMMENT '科室编码，如 RAD',
    hospital_id VARCHAR(20) NOT NULL COMMENT '所属医院编码',
    name VARCHAR(100) NOT NULL COMMENT '科室名称',
    is_active TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否启用',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id, hospital_id),
    INDEX idx_dept_hospital (hospital_id),
    INDEX idx_dept_active (is_active)
)ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 创建用户表
drop table if exists med_auth.user;
create table med_auth.user (
    id CHAR(36) NOT NULL PRIMARY KEY COMMENT 'UUIDv4',
    hospital_id VARCHAR(20) NOT NULL COMMENT '医院编码，如 BJH',
    dept_code VARCHAR(20) NOT NULL COMMENT '科室编码，如 RAD',
    user_seq VARCHAR(20) NOT NULL COMMENT '序列号，如 00123 或随机字符串',
    
    -- 自动生成的账号：BJH-RAD-00123
    username VARCHAR(100) GENERATED ALWAYS AS (
        CONCAT(hospital_id, '-', dept_code, '-', user_seq)
    ) STORED UNIQUE,
    
    full_name VARCHAR(100) NOT NULL,
    password_hash VARCHAR(255) NOT NULL COMMENT 'BCrypt 加密',
    role ENUM('doctor', 'admin') NOT NULL DEFAULT 'doctor',
    is_deleted TINYINT(1) NOT NULL DEFAULT 0,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    
    -- 索引
    INDEX idx_hospital_dept (hospital_id, dept_code),
    INDEX idx_username (username),
    INDEX idx_deleted (is_deleted)
)ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;