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
    PRIMARY KEY (id),
    UNIQUE KEY unique_dept_per_hospital (id, hospital_id) COMMENT '同一医院下科室ID唯一',
    INDEX idx_dept_hospital (hospital_id),
    INDEX idx_dept_active (is_active)
)ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 创建用户表
-- 注意: username 为应用层计算属性，不在数据库中存储（见 UserEntity.kt）
-- 命名规则: ADMIN-{userSeq} / DR/NR/RC-{hospitalId}-{deptCode}-{userSeq}
drop table if exists med_auth.user;
create table med_auth.user (
    id CHAR(36) NOT NULL PRIMARY KEY COMMENT 'UUIDv4',
    hospital_id VARCHAR(20) NULL COMMENT '医院编码，如 BJH（管理员可为NULL）',
    dept_code VARCHAR(20) NULL COMMENT '科室编码，如 RAD（管理员可为NULL）',
    user_seq VARCHAR(20) NOT NULL COMMENT '序列号，如 00123 或随机字符串',
    full_name VARCHAR(100) NOT NULL COMMENT '用户真实姓名',
    password_hash VARCHAR(255) NOT NULL COMMENT 'SHA-256 加密',
    role VARCHAR(50) NOT NULL DEFAULT 'doctor' COMMENT '角色: admin, doctor, nurse, receptionist',
    is_deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否删除（软删除）',
    is_frozen TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否冻结',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    -- 索引
    INDEX idx_hospital_dept (hospital_id, dept_code),
    INDEX idx_deleted (is_deleted),
    INDEX idx_frozen (is_frozen),
    INDEX idx_role (role)
)ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
