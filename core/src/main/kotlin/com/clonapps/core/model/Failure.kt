package com.clonapps.core.model

/** Tahap tempat kegagalan terjadi. Dipakai untuk membedakan kelas kegagalan. */
enum class FailureStage {
    PROVISIONING,
    PROFILE,
    PRECHECK,
    PACKAGE_INCOMPLETE,
    ABI,
    DEPENDENCY,
    INSTALL,
    VERIFY,
    LAUNCH,
    LOGIN,
    TARGET_RESTRICTION,
    UNINSTALL,
    COMMUNICATION,
    CANCELLED,
}

/**
 * Penyebab kegagalan yang diketahui. Setiap nilai dipetakan ke teks
 * "penyebab", "yang belum diketahui", dan "tindakan pemulihan" di lapisan UI.
 */
enum class FailureCause {
    // Provisioning / profil
    MANAGED_USERS_FEATURE_MISSING,
    PROVISIONING_NOT_ALLOWED,
    PROVISIONING_UI_MISSING,
    PROVISIONING_CANCELLED,
    PROVISIONING_FAILED,
    FOREIGN_PROFILE_EXISTS,
    PROFILE_NOT_FOUND,
    PROFILE_PAUSED,
    PROFILE_LOCKED,
    PROFILE_NOT_PAIRED,
    PROFILE_NOT_OWNER,
    PROFILE_UNREACHABLE,

    // Precheck & paket
    INSUFFICIENT_STORAGE,
    BASE_APK_MISSING,
    APK_UNREADABLE,
    SPLIT_MISSING,
    ARCHIVE_MISMATCH,
    NO_MATCHING_ABI,
    MISSING_SHARED_LIBRARY,
    SYSTEM_APP_NOT_IN_PROFILE,
    IS_CLONAPPS_ITSELF,

    // Instalasi
    PENDING_USER_ACTION,
    USER_CANCELLED,
    UNKNOWN_SOURCES_BLOCKED,
    INSTALL_BLOCKED_BY_POLICY,
    INSTALL_EXISTING_REJECTED,
    SIGNATURE_CONFLICT,
    VERSION_DOWNGRADE,
    TARGET_SDK_TOO_LOW,
    DEVICE_SDK_TOO_OLD,
    PACKAGE_INVALID,
    PERMISSION_CONFLICT,
    PROVIDER_CONFLICT,
    VERIFICATION_FAILED,
    INSTALL_TIMEOUT,
    INSTALL_CONFLICT,
    SESSION_ERROR,
    NOT_PRESENT_AFTER_INSTALL,
    INTERRUPTED,

    // Peluncuran / aplikasi target
    NO_LAUNCHER_ACTIVITY,
    LAUNCH_SECURITY_EXCEPTION,
    LAUNCH_ACTIVITY_NOT_FOUND,
    TARGET_RESTRICTS_PROFILE,

    // Komunikasi lintas profil
    AUTH_FAILED,
    BAD_REQUEST,

    UNKNOWN,
}

/** Tindakan pemulihan yang disarankan. */
enum class RecoveryAction {
    NONE,
    RETRY,
    FREE_STORAGE,
    UNPAUSE_PROFILE,
    UNLOCK_PROFILE,
    CREATE_PROFILE,
    REPAIR_PAIRING,
    OPEN_PROFILE_SETTINGS,
    ALLOW_UNKNOWN_SOURCES_IN_PROFILE,
    UPDATE_ORIGINAL_APP,
    INSTALL_FROM_WORK_PLAY_STORE,
    INSTALL_DEPENDENCY_FIRST,
    REMOVE_PROFILE_COPY_FIRST,
    COLLECT_ADB_LOG,
    CONTACT_TARGET_DEVELOPER,
    USE_OTHER_DEVICE_OR_ROM,
}

/**
 * Catatan kegagalan lengkap. [detail] berisi pesan mentah dari sistem
 * yang sudah melalui [com.clonapps.core.diag.Redactor].
 */
data class Failure(
    val stage: FailureStage,
    val cause: FailureCause,
    /** Kode mentah, mis. "PackageInstaller.STATUS_FAILURE_INCOMPATIBLE(7)" atau "SecurityException". */
    val code: String,
    val detail: String? = null,
    val recovery: RecoveryAction = RecoveryAction.NONE,
    val retryable: Boolean = false,
    val atMillis: Long = 0L,
)
