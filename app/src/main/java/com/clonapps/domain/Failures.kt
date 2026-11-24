package com.clonapps.domain

import com.clonapps.core.diag.Redactor
import com.clonapps.core.error.ErrorMapper
import com.clonapps.core.model.Failure
import com.clonapps.core.model.FailureCause
import com.clonapps.core.model.FailureStage
import com.clonapps.core.model.RecoveryAction
import com.clonapps.core.validate.PrecheckResult
import com.clonapps.profile.ProfileProtocol

/** Mengubah hasil pemeriksaan dan balasan profil menjadi [Failure] yang lengkap. */
object Failures {

    fun stageFor(cause: FailureCause): FailureStage = when (cause) {
        FailureCause.PROFILE_NOT_FOUND, FailureCause.PROFILE_PAUSED, FailureCause.PROFILE_LOCKED,
        FailureCause.PROFILE_NOT_PAIRED, FailureCause.PROFILE_NOT_OWNER, FailureCause.PROFILE_UNREACHABLE,
        FailureCause.FOREIGN_PROFILE_EXISTS -> FailureStage.PROFILE
        FailureCause.BASE_APK_MISSING, FailureCause.APK_UNREADABLE, FailureCause.SPLIT_MISSING,
        FailureCause.ARCHIVE_MISMATCH, FailureCause.PACKAGE_INVALID -> FailureStage.PACKAGE_INCOMPLETE
        FailureCause.NO_MATCHING_ABI -> FailureStage.ABI
        FailureCause.MISSING_SHARED_LIBRARY -> FailureStage.DEPENDENCY
        FailureCause.MANAGED_USERS_FEATURE_MISSING, FailureCause.PROVISIONING_NOT_ALLOWED,
        FailureCause.PROVISIONING_UI_MISSING, FailureCause.PROVISIONING_CANCELLED, FailureCause.PROVISIONING_FAILED -> FailureStage.PROVISIONING
        FailureCause.TARGET_SDK_TOO_LOW, FailureCause.TARGET_RESTRICTS_PROFILE -> FailureStage.TARGET_RESTRICTION
        FailureCause.AUTH_FAILED, FailureCause.BAD_REQUEST -> FailureStage.COMMUNICATION
        else -> FailureStage.PRECHECK
    }

    fun recoveryFor(cause: FailureCause): RecoveryAction = when (cause) {
        FailureCause.PROFILE_NOT_FOUND -> RecoveryAction.CREATE_PROFILE
        FailureCause.PROFILE_PAUSED -> RecoveryAction.UNPAUSE_PROFILE
        FailureCause.PROFILE_LOCKED -> RecoveryAction.UNLOCK_PROFILE
        FailureCause.PROFILE_NOT_PAIRED, FailureCause.AUTH_FAILED, FailureCause.PROFILE_UNREACHABLE -> RecoveryAction.REPAIR_PAIRING
        FailureCause.FOREIGN_PROFILE_EXISTS -> RecoveryAction.OPEN_PROFILE_SETTINGS
        FailureCause.INSUFFICIENT_STORAGE -> RecoveryAction.FREE_STORAGE
        FailureCause.SPLIT_MISSING, FailureCause.APK_UNREADABLE, FailureCause.ARCHIVE_MISMATCH,
        FailureCause.BASE_APK_MISSING -> RecoveryAction.UPDATE_ORIGINAL_APP
        FailureCause.NO_MATCHING_ABI -> RecoveryAction.USE_OTHER_DEVICE_OR_ROM
        FailureCause.MISSING_SHARED_LIBRARY -> RecoveryAction.INSTALL_DEPENDENCY_FIRST
        FailureCause.SYSTEM_APP_NOT_IN_PROFILE -> RecoveryAction.INSTALL_FROM_WORK_PLAY_STORE
        FailureCause.UNKNOWN_SOURCES_BLOCKED -> RecoveryAction.ALLOW_UNKNOWN_SOURCES_IN_PROFILE
        else -> RecoveryAction.COLLECT_ADB_LOG
    }

    /** Kegagalan dari pemeriksaan pra-instalasi: hanya item FAIL pertama dijadikan penyebab utama. */
    fun fromPrecheck(pre: PrecheckResult, now: Long): Failure {
        val first = pre.blocking.firstOrNull()
        val cause = first?.cause ?: FailureCause.UNKNOWN
        val others = pre.blocking.drop(1).joinToString { "${it.id}:${it.cause}" }
        val retryable = cause in setOf(
            FailureCause.PROFILE_PAUSED, FailureCause.PROFILE_LOCKED, FailureCause.INSUFFICIENT_STORAGE,
            FailureCause.ARCHIVE_MISMATCH, FailureCause.PROFILE_NOT_PAIRED, FailureCause.UNKNOWN_SOURCES_BLOCKED,
            FailureCause.SPLIT_MISSING, FailureCause.APK_UNREADABLE,
        )
        return Failure(
            stage = stageFor(cause),
            cause = cause,
            code = "PRECHECK_${first?.id ?: "UNKNOWN"}",
            detail = listOfNotNull(first?.detail, others.takeIf { it.isNotEmpty() }?.let { "lainnya: $it" }).joinToString(" | ").ifEmpty { null },
            recovery = recoveryFor(cause),
            retryable = retryable,
            atMillis = now,
        )
    }

    fun fromResult(r: ProfileProtocol.Result, defaultStage: FailureStage, now: Long): Failure {
        val detail = listOfNotNull(r.message, r.steps.takeIf { it.isNotEmpty() }?.joinToString(" → ")).joinToString(" | ")
            .let { Redactor.redact(it) }.ifEmpty { null }
        fun f(stage: FailureStage, cause: FailureCause, recovery: RecoveryAction, retry: Boolean) =
            Failure(stage, cause, "Profile.${r.code}", detail, recovery, retry, now)
        return when (r.code) {
            ProfileProtocol.ResultCode.INSTALLER_FAILED ->
                (ErrorMapper.fromInstaller(r.installerStatus ?: -999, r.message, now) ?: f(defaultStage, FailureCause.UNKNOWN, RecoveryAction.COLLECT_ADB_LOG, true))
                    .copy(detail = detail)
            ProfileProtocol.ResultCode.AUTH_FAILED -> f(FailureStage.COMMUNICATION, FailureCause.AUTH_FAILED, RecoveryAction.REPAIR_PAIRING, true)
            ProfileProtocol.ResultCode.NOT_PAIRED -> f(FailureStage.PROFILE, FailureCause.PROFILE_NOT_PAIRED, RecoveryAction.REPAIR_PAIRING, true)
            ProfileProtocol.ResultCode.NOT_PROFILE_OWNER -> f(FailureStage.PROFILE, FailureCause.PROFILE_NOT_OWNER, RecoveryAction.OPEN_PROFILE_SETTINGS, false)
            ProfileProtocol.ResultCode.BAD_REQUEST -> f(FailureStage.COMMUNICATION, FailureCause.BAD_REQUEST, RecoveryAction.COLLECT_ADB_LOG, false)
            ProfileProtocol.ResultCode.FILES_UNREADABLE -> f(FailureStage.PACKAGE_INCOMPLETE, FailureCause.APK_UNREADABLE, RecoveryAction.UPDATE_ORIGINAL_APP, true)
            ProfileProtocol.ResultCode.ARCHIVE_MISMATCH -> f(FailureStage.PACKAGE_INCOMPLETE, FailureCause.ARCHIVE_MISMATCH, RecoveryAction.RETRY, true)
            ProfileProtocol.ResultCode.SYSTEM_APP_ENABLE_FAILED -> f(FailureStage.INSTALL, FailureCause.SYSTEM_APP_NOT_IN_PROFILE, RecoveryAction.INSTALL_FROM_WORK_PLAY_STORE, false)
            ProfileProtocol.ResultCode.NOT_INSTALLED -> f(FailureStage.VERIFY, FailureCause.NOT_PRESENT_AFTER_INSTALL, RecoveryAction.COLLECT_ADB_LOG, true)
            ProfileProtocol.ResultCode.USER_DECLINED -> f(FailureStage.CANCELLED, FailureCause.USER_CANCELLED, RecoveryAction.RETRY, true)
            ProfileProtocol.ResultCode.TIMEOUT -> f(defaultStage, FailureCause.INSTALL_TIMEOUT, RecoveryAction.RETRY, true)
            ProfileProtocol.ResultCode.LAUNCH_FAILED -> ErrorMapper.fromLaunch(r.message?.substringBefore(':') ?: "", r.message, now)
            ProfileProtocol.ResultCode.INTERNAL_ERROR -> f(defaultStage, FailureCause.UNKNOWN, RecoveryAction.COLLECT_ADB_LOG, true)
            ProfileProtocol.ResultCode.OK -> f(defaultStage, FailureCause.UNKNOWN, RecoveryAction.NONE, false)
        }
    }

    fun unreachable(stage: FailureStage, message: String?, now: Long) = Failure(
        stage, FailureCause.PROFILE_UNREACHABLE, "NO_CROSS_PROFILE_HANDLER", message?.let { Redactor.redact(it) },
        RecoveryAction.REPAIR_PAIRING, true, now,
    )

    fun interrupted(stage: FailureStage, now: Long) = Failure(
        stage, FailureCause.INTERRUPTED, "EMPTY_RESULT",
        "Layar profil kerja ditutup, profil dijeda, atau proses berhenti sebelum memberi hasil.",
        RecoveryAction.RETRY, true, now,
    )

    fun simple(stage: FailureStage, cause: FailureCause, code: String, now: Long, detail: String? = null, retry: Boolean = true) =
        Failure(stage, cause, code, detail, recoveryFor(cause), retry, now)
}
