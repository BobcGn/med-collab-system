package exception

sealed class PatientException : Exception() {
    abstract val code: Int
    abstract override val message: String

    // ==================== 患者相关异常 ====================
    /**
     * 患者不存在异常
     * 错误码: 1101
     */
    data class PatientNotFoundException(
        override val code: Int = 1101,
        override val message: String = "患者不存在"
    ) : PatientException()

    /**
     * 患者已存在异常
     * 错误码: 1102
     */
    data class PatientAlreadyExistsException(
        override val code: Int = 1102,
        override val message: String = "患者已存在"
    ) : PatientException()

    /**
     * 创建患者失败异常
     * 错误码: 1103
     */
    data class CreatePatientFailedException(
        override val code: Int = 1103,
        override val message: String = "创建患者失败"
    ) : PatientException()

    /**
     * 更新患者失败异常
     * 错误码: 1104
     */
    data class UpdatePatientFailedException(
        override val code: Int = 1104,
        override val message: String = "更新患者失败"
    ) : PatientException()

    /**
     * 删除患者失败异常
     * 错误码: 1105
     */
    data class DeletePatientFailedException(
        override val code: Int = 1105,
        override val message: String = "删除患者失败"
    ) : PatientException()

    // ==================== 数据验证异常 ====================
    /**
     * 医院ID无效异常
     * 错误码: 1201
     */
    data class HospitalIdInvalidException(
        override val code: Int = 1201,
        override val message: String = "医院ID无效"
    ) : PatientException()

    /**
     * 医生ID无效异常
     * 错误码: 1202
     */
    data class DoctorIdInvalidException(
        override val code: Int = 1202,
        override val message: String = "医生ID无效"
    ) : PatientException()

    /**
     * 科室无效异常
     * 错误码: 1203
     */
    data class DepartmentInvalidException(
        override val code: Int = 1203,
        override val message: String = "科室无效"
    ) : PatientException()

    /**
     * 患者数据无效异常
     * 错误码: 1204
     */
    data class PatientDataInvalidException(
        override val code: Int = 1204,
        override val message: String = "患者数据无效"
    ) : PatientException()

    // ==================== 权限相关异常 ====================
    /**
     * 无权限访问患者数据异常
     * 错误码: 1301
     */
    data class PermissionDeniedException(
        override val code: Int = 1301,
        override val message: String = "无权限访问患者数据"
    ) : PatientException()

    /**
     * 不能修改他人负责的患者异常
     * 错误码: 1302
     */
    data class CannotModifyOtherPatientException(
        override val code: Int = 1302,
        override val message: String = "不能修改他人负责的患者"
    ) : PatientException()
}