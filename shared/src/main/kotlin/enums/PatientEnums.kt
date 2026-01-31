package enums

/**
 * 患者性别枚举类
 */
enum class Gender {
    M, // 男
    F  // 女
}

/**
 * 患者血型枚举类
 */
enum class BloodType {
    A, // A型
    B, // B型
    O, // O型
    AB, // AB型
    Unknown // 未知
}

/**
 * 患者状态枚举类
 */
enum class Status{
    Active,     // 激活
    Discharged, // 出院
    Deceased    // 死亡
}