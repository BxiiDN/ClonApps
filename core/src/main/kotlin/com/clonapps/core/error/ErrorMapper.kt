package com.clonapps.core.error

import com.clonapps.core.diag.Redactor
import com.clonapps.core.model.Failure
import com.clonapps.core.model.FailureCause
import com.clonapps.core.model.FailureStage
import com.clonapps.core.model.RecoveryAction

/**
 * Konstanta PackageInstaller disalin agar modul inti dapat diuji tanpa Android.
 * Nilainya publik dan stabil di android.content.pm.PackageInstaller.
 */
object InstallerStatus {
    const val PENDING_USER_ACTION = -1
    const val SUCCESS = 0
    const val FAILURE = 1
    const val FAILURE_BLOCKED = 2
    const val FAILURE_ABORTED = 3
    const val FAILURE_INVALID = 4
    const val FAILURE_CONFLICT = 5
    const val FAILURE_STORAGE = 6
    const val FAILURE_INCOMPATIBLE = 7
    const val FAILURE_TIMEOUT = 8

    fun name(status: Int): String = when (status) {
        PENDING_USER_ACTION -> "STATUS_PENDING_USER_ACTION"
        SUCCESS -> "STATUS_SUCCESS"
        FAILURE -> "STATUS_FAILURE"
        FAILURE_BLOCKED -> "STATUS_FAILURE_BLOCKED"
        FAILURE_ABORTED -> "STATUS_FAILURE_ABORTED"
        FAILURE_INVALID -> "STATUS_FAILURE_INVALID"
        FAILURE_CONFLICT -> "STATUS_FAILURE_CONFLICT"
        FAILURE_STORAGE -> "STATUS_FAILURE_STORAGE"
        FAILURE_INCOMPATIBLE -> "STATUS_FAILURE_INCOMPATIBLE"
        FAILURE_TIMEOUT -> "STATUS_FAILURE_TIMEOUT"
        else -> "STATUS_$status"
    }
}

object ErrorMapper {

    private data class Rule(
        val token: String,
        val stage: FailureStage,
        val cause: FailureCause,
        val recovery: RecoveryAction,
        val retryable: Boolean,
    )

    // Urutan penting: token yang lebih spesifik lebih dulu.
    private val messageRules = listOf(
        Rule("INSTALL_FAILED_NO_MATCHING_ABIS", FailureStage.ABI, FailureCause.NO_MATCHING_ABI, RecoveryAction.USE_OTHER_DEVICE_OR_ROM, false),
        Rule("INSTALL_FAILED_MISSING_SHARED_LIBRARY", FailureStage.DEPENDENCY, FailureCause.MISSING_SHARED_LIBRARY, RecoveryAction.INSTALL_DEPENDENCY_FIRST, true),
        Rule("INSTALL_FAILED_MISSING_SPLIT", FailureStage.PACKAGE_INCOMPLETE, FailureCause.SPLIT_MISSING, RecoveryAction.UPDATE_ORIGINAL_APP, true),
        Rule("INSTALL_FAILED_INSUFFICIENT_STORAGE", FailureStage.INSTALL, FailureCause.INSUFFICIENT_STORAGE, RecoveryAction.FREE_STORAGE, true),
        Rule("INSTALL_FAILED_DEPRECATED_SDK_VERSION", FailureStage.TARGET_RESTRICTION, FailureCause.TARGET_SDK_TOO_LOW, RecoveryAction.CONTACT_TARGET_DEVELOPER, false),
        Rule("INSTALL_FAILED_OLDER_SDK", FailureStage.INSTALL, FailureCause.DEVICE_SDK_TOO_OLD, RecoveryAction.NONE, false),
        Rule("INSTALL_FAILED_VERSION_DOWNGRADE", FailureStage.INSTALL, FailureCause.VERSION_DOWNGRADE, RecoveryAction.REMOVE_PROFILE_COPY_FIRST, false),
        Rule("INSTALL_FAILED_UPDATE_INCOMPATIBLE", FailureStage.INSTALL, FailureCause.SIGNATURE_CONFLICT, RecoveryAction.REMOVE_PROFILE_COPY_FIRST, false),
        Rule("INSTALL_FAILED_SHARED_USER_INCOMPATIBLE", FailureStage.INSTALL, FailureCause.SIGNATURE_CONFLICT, RecoveryAction.NONE, false),
        Rule("INSTALL_FAILED_DUPLICATE_PERMISSION", FailureStage.INSTALL, FailureCause.PERMISSION_CONFLICT, RecoveryAction.NONE, false),
        Rule("INSTALL_FAILED_CONFLICTING_PROVIDER", FailureStage.INSTALL, FailureCause.PROVIDER_CONFLICT, RecoveryAction.NONE, false),
        Rule("INSTALL_FAILED_USER_RESTRICTED", FailureStage.INSTALL, FailureCause.INSTALL_BLOCKED_BY_POLICY, RecoveryAction.ALLOW_UNKNOWN_SOURCES_IN_PROFILE, true),
        Rule("INSTALL_FAILED_VERIFICATION_TIMEOUT", FailureStage.INSTALL, FailureCause.VERIFICATION_FAILED, RecoveryAction.RETRY, true),
        Rule("INSTALL_FAILED_VERIFICATION_FAILURE", FailureStage.INSTALL, FailureCause.VERIFICATION_FAILED, RecoveryAction.NONE, false),
        Rule("INSTALL_FAILED_ABORTED", FailureStage.CANCELLED, FailureCause.USER_CANCELLED, RecoveryAction.RETRY, true),
        Rule("INSTALL_FAILED_INVALID_APK", FailureStage.PACKAGE_INCOMPLETE, FailureCause.PACKAGE_INVALID, RecoveryAction.UPDATE_ORIGINAL_APP, false),
        Rule("INSTALL_PARSE_FAILED", FailureStage.PACKAGE_INCOMPLETE, FailureCause.PACKAGE_INVALID, RecoveryAction.UPDATE_ORIGINAL_APP, false),
        Rule("INSTALL_FAILED_SESSION_INVALID", FailureStage.INSTALL, FailureCause.SESSION_ERROR, RecoveryAction.RETRY, true),
    )

    /** Memetakan hasil akhir PackageInstaller (EXTRA_STATUS + EXTRA_STATUS_MESSAGE). */
    fun fromInstaller(status: Int, message: String?, now: Long = 0L): Failure? {
        if (status == InstallerStatus.SUCCESS) return null
        val clean = message?.let { Redactor.redact(it) }
        val code = "PackageInstaller.${InstallerStatus.name(status)}($status)"
        val upper = message?.uppercase().orEmpty()
        messageRules.firstOrNull { upper.contains(it.token) }?.let {
            return Failure(it.stage, it.cause, code, clean, it.recovery, it.retryable, now)
        }
        val (stage, cause, recovery, retry) = when (status) {
            InstallerStatus.PENDING_USER_ACTION -> Quad(FailureStage.INSTALL, FailureCause.PENDING_USER_ACTION, RecoveryAction.NONE, true)
            InstallerStatus.FAILURE_ABORTED -> Quad(FailureStage.CANCELLED, FailureCause.USER_CANCELLED, RecoveryAction.RETRY, true)
            InstallerStatus.FAILURE_BLOCKED -> Quad(FailureStage.INSTALL, FailureCause.INSTALL_BLOCKED_BY_POLICY, RecoveryAction.ALLOW_UNKNOWN_SOURCES_IN_PROFILE, true)
            InstallerStatus.FAILURE_INVALID -> Quad(FailureStage.PACKAGE_INCOMPLETE, FailureCause.PACKAGE_INVALID, RecoveryAction.UPDATE_ORIGINAL_APP, false)
            InstallerStatus.FAILURE_CONFLICT -> Quad(FailureStage.INSTALL, FailureCause.INSTALL_CONFLICT, RecoveryAction.REMOVE_PROFILE_COPY_FIRST, false)
            InstallerStatus.FAILURE_STORAGE -> Quad(FailureStage.INSTALL, FailureCause.INSUFFICIENT_STORAGE, RecoveryAction.FREE_STORAGE, true)
            InstallerStatus.FAILURE_INCOMPATIBLE -> Quad(FailureStage.INSTALL, FailureCause.DEVICE_SDK_TOO_OLD, RecoveryAction.USE_OTHER_DEVICE_OR_ROM, false)
            InstallerStatus.FAILURE_TIMEOUT -> Quad(FailureStage.INSTALL, FailureCause.INSTALL_TIMEOUT, RecoveryAction.RETRY, true)
            else -> Quad(FailureStage.INSTALL, FailureCause.UNKNOWN, RecoveryAction.COLLECT_ADB_LOG, true)
        }
        // STATUS_FAILURE_INCOMPATIBLE tanpa pesan spesifik bisa ABI atau SDK: jangan menebak.
        val finalCause = if (status == InstallerStatus.FAILURE_INCOMPATIBLE && upper.isEmpty()) FailureCause.UNKNOWN else cause
        return Failure(stage, finalCause, code, clean, recovery, retry, now)
    }

    /** Memetakan exception saat meluncurkan aplikasi di profil. */
    fun fromLaunch(exceptionName: String, message: String?, now: Long = 0L): Failure {
        val clean = message?.let { Redactor.redact(it) }
        val cause = when {
            exceptionName.endsWith("ActivityNotFoundException") -> FailureCause.LAUNCH_ACTIVITY_NOT_FOUND
            exceptionName.endsWith("SecurityException") -> FailureCause.LAUNCH_SECURITY_EXCEPTION
            exceptionName.endsWith("IllegalStateException") && message?.contains("locked", true) == true -> FailureCause.PROFILE_LOCKED
            else -> FailureCause.UNKNOWN
        }
        val recovery = when (cause) {
            FailureCause.PROFILE_LOCKED -> RecoveryAction.UNLOCK_PROFILE
            FailureCause.LAUNCH_ACTIVITY_NOT_FOUND -> RecoveryAction.RETRY
            else -> RecoveryAction.COLLECT_ADB_LOG
        }
        return Failure(FailureStage.LAUNCH, cause, exceptionName.substringAfterLast('.'), clean, recovery, cause != FailureCause.UNKNOWN, now)
    }

    /** resultCode dari activity provisioning sistem. */
    fun fromProvisioningResult(resultOk: Boolean, now: Long = 0L): Failure? =
        if (resultOk) null else Failure(
            FailureStage.PROVISIONING, FailureCause.PROVISIONING_CANCELLED, "RESULT_CANCELED",
            "Layar penyiapan Android ditutup atau ditolak. Android tidak memberi alasan lebih rinci ke aplikasi.",
            RecoveryAction.CREATE_PROFILE, true, now,
        )

    private data class Quad(val a: FailureStage, val b: FailureCause, val c: RecoveryAction, val d: Boolean)
}
