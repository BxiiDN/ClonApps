package com.clonapps.core.validate

import com.clonapps.core.model.CloneStatus
import com.clonapps.core.model.FailureCause
import com.clonapps.core.model.InstallMethod
import com.clonapps.core.model.ProfileOwnership

/** Keadaan profil tujuan sebagaimana terlihat dari profil utama. */
data class ProfileView(
    val ownership: ProfileOwnership,
    val quietMode: Boolean,
    val unlocked: Boolean,
)

/** Kemampuan instalasi di profil tujuan, dilaporkan oleh ClonApps di dalam profil. Null = belum diketahui. */
data class InstallCapability(
    val unknownSourcesRestricted: Boolean?,
    val canRequestPackageInstalls: Boolean?,
)

data class PrecheckInput(
    val selfPackageName: String,
    val snapshot: PackageSnapshot,
    val deviceAbis: List<String>,
    val profile: ProfileView,
    val capability: InstallCapability,
    val freeBytes: Long?,
    /** Versi paket di profil tujuan bila sudah ada, null bila tidak ada, dan [presenceKnown]=false bila tak dapat diperiksa. */
    val profileVersionCode: Long?,
    val presenceKnown: Boolean,
    /** Untuk aplikasi sistem: apakah paket ada (mungkin nonaktif) di image profil. Null = belum diketahui. */
    val systemAppAvailableInProfile: Boolean?,
)

data class PrecheckResult(
    val packageName: String,
    val items: List<CheckItem>,
    val overall: CloneStatus,
    val plannedMethod: InstallMethod?,
    val requiredBytes: Long,
) {
    val blocking: List<CheckItem> get() = items.filter { it.outcome == CheckOutcome.FAIL }
    val warnings: List<CheckItem> get() = items.filter { it.outcome == CheckOutcome.WARN }
}

object Precheck {
    /** Ruang sementara + hasil kompilasi dex + cadangan. Ini perkiraan, bukan angka pasti. */
    const val SAFETY_MARGIN_BYTES: Long = 100L * 1024 * 1024
    fun requiredBytes(apkBytes: Long): Long = apkBytes * 2 + SAFETY_MARGIN_BYTES

    private val profileCauses = setOf(
        FailureCause.PROFILE_NOT_FOUND, FailureCause.PROFILE_PAUSED, FailureCause.PROFILE_LOCKED,
        FailureCause.PROFILE_NOT_PAIRED, FailureCause.UNKNOWN_SOURCES_BLOCKED, FailureCause.INSUFFICIENT_STORAGE,
    )

    fun evaluate(input: PrecheckInput): PrecheckResult {
        val s = input.snapshot
        val items = mutableListOf<CheckItem>()
        val required = requiredBytes(s.totalApkBytes)

        if (s.packageName == input.selfPackageName) {
            items += CheckItem(CheckId.NOT_SELF, CheckOutcome.FAIL, FailureCause.IS_CLONAPPS_ITSELF)
            return PrecheckResult(s.packageName, items, CloneStatus.FAILED, null, required)
        }

        // Profil
        items += when (input.profile.ownership) {
            ProfileOwnership.NONE -> CheckItem(CheckId.PROFILE_READY, CheckOutcome.FAIL, FailureCause.PROFILE_NOT_FOUND)
            ProfileOwnership.FOREIGN -> CheckItem(CheckId.PROFILE_READY, CheckOutcome.FAIL, FailureCause.FOREIGN_PROFILE_EXISTS)
            ProfileOwnership.OURS_UNPAIRED -> CheckItem(CheckId.PROFILE_READY, CheckOutcome.FAIL, FailureCause.PROFILE_NOT_PAIRED)
            ProfileOwnership.OURS_PAIRED -> CheckItem(CheckId.PROFILE_READY, CheckOutcome.PASS)
        }
        if (input.profile.ownership == ProfileOwnership.OURS_PAIRED || input.profile.ownership == ProfileOwnership.OURS_UNPAIRED) {
            items += when {
                input.profile.quietMode -> CheckItem(CheckId.PROFILE_ACTIVE, CheckOutcome.FAIL, FailureCause.PROFILE_PAUSED)
                !input.profile.unlocked -> CheckItem(CheckId.PROFILE_ACTIVE, CheckOutcome.FAIL, FailureCause.PROFILE_LOCKED)
                else -> CheckItem(CheckId.PROFILE_ACTIVE, CheckOutcome.PASS)
            }
        }

        // Sudah ada di profil?
        if (input.presenceKnown && input.profileVersionCode != null) {
            val newer = input.profileVersionCode >= s.versionCode
            items += CheckItem(
                CheckId.ALREADY_IN_PROFILE,
                if (newer) CheckOutcome.PASS else CheckOutcome.WARN,
                detail = "profil=${input.profileVersionCode} utama=${s.versionCode}",
            )
            if (newer) {
                return PrecheckResult(s.packageName, items, CloneStatus.INSTALLED, InstallMethod.ALREADY_PRESENT, required)
            }
        } else if (!input.presenceKnown) {
            items += CheckItem(CheckId.ALREADY_IN_PROFILE, CheckOutcome.UNKNOWN)
        }

        val method: InstallMethod?
        if (s.isSystemApp) {
            // Aplikasi sistem tidak disalin; hanya diaktifkan bila ada di image profil.
            method = InstallMethod.ENABLE_SYSTEM_APP
            items += when (input.systemAppAvailableInProfile) {
                true -> CheckItem(CheckId.SYSTEM_APP_IN_PROFILE, CheckOutcome.PASS)
                false -> CheckItem(CheckId.SYSTEM_APP_IN_PROFILE, CheckOutcome.FAIL, FailureCause.SYSTEM_APP_NOT_IN_PROFILE)
                null -> CheckItem(CheckId.SYSTEM_APP_IN_PROFILE, CheckOutcome.UNKNOWN)
            }
        } else {
            method = InstallMethod.PACKAGE_INSTALLER_COPY
            items += PackageValidator.validate(s, input.deviceAbis)
            items += when (input.capability.unknownSourcesRestricted) {
                true -> CheckItem(CheckId.INSTALL_CAPABILITY, CheckOutcome.FAIL, FailureCause.UNKNOWN_SOURCES_BLOCKED)
                false -> if (input.capability.canRequestPackageInstalls == false) {
                    // Pengguna akan diminta mengizinkan "instal aplikasi tidak dikenal" untuk ClonApps di profil.
                    CheckItem(CheckId.INSTALL_CAPABILITY, CheckOutcome.WARN, FailureCause.PENDING_USER_ACTION)
                } else {
                    CheckItem(CheckId.INSTALL_CAPABILITY, CheckOutcome.PASS)
                }
                null -> CheckItem(CheckId.INSTALL_CAPABILITY, CheckOutcome.UNKNOWN)
            }
            items += when {
                input.freeBytes == null -> CheckItem(CheckId.STORAGE, CheckOutcome.UNKNOWN)
                input.freeBytes < required -> CheckItem(
                    CheckId.STORAGE, CheckOutcome.FAIL, FailureCause.INSUFFICIENT_STORAGE,
                    "bebas=${input.freeBytes} perlu≈$required",
                )
                else -> CheckItem(CheckId.STORAGE, CheckOutcome.PASS)
            }
        }

        val fails = items.filter { it.outcome == CheckOutcome.FAIL }
        val overall = when {
            fails.isEmpty() -> CloneStatus.READY
            fails.all { it.cause in profileCauses } -> CloneStatus.NEEDS_USER_ACTION
            else -> CloneStatus.FAILED
        }
        return PrecheckResult(s.packageName, items, overall, method, required)
    }
}
