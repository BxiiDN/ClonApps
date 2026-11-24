package com.clonapps.platform

import android.annotation.SuppressLint
import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.Intent
import android.content.pm.CrossProfileApps
import android.content.pm.PackageManager
import android.os.PersistableBundle
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import com.clonapps.admin.ClonAppsAdminReceiver
import com.clonapps.core.model.ProfileOwnership
import com.clonapps.data.PairingStore
import com.clonapps.data.Stores
import com.clonapps.profile.ProfileProtocol

data class ProfileInfo(
    val handle: UserHandle,
    val serial: Long,
    /** Representasi UserHandle (mis. "UserHandle{10}") untuk diagnosis. */
    val label: String,
    val hasClonApps: Boolean,
    val quietMode: Boolean,
    val unlocked: Boolean?,
)

data class ProfileState(
    val ownership: ProfileOwnership,
    /** Profil kerja milik ClonApps (atau kandidatnya). */
    val profile: ProfileInfo?,
    /** Semua profil lain yang terlihat (Private Space, Dual Apps ROM, profil kerja organisasi, dll.). */
    val others: List<ProfileInfo>,
    val provisioningAllowed: Boolean,
    val managedUsersFeature: Boolean,
    val provisioningUiPresent: Boolean,
    /** True bila status kepemilikan belum diverifikasi lewat HELLO. */
    val unverified: Boolean,
) {
    val usable: Boolean get() = ownership == ProfileOwnership.OURS_PAIRED && profile != null && !profile.quietMode && profile.unlocked != false
}

sealed class PauseResult {
    object Requested : PauseResult()

    /** Android menolak: hanya launcher default atau aplikasi berizin sistem yang dapat menjeda profil. */
    data class NotPermitted(val message: String?) : PauseResult()
    data class Error(val message: String?) : PauseResult()
}

/** Membaca keadaan profil dari profil utama memakai API publik saja. */
class ProfileEnvironment(private val context: Context, private val pairing: PairingStore, private val stores: Stores) {
    private val um = context.getSystemService(UserManager::class.java)
    private val dpm = context.getSystemService(DevicePolicyManager::class.java)
    private val cpa = context.getSystemService(CrossProfileApps::class.java)

    fun handleForSerial(serial: Long): UserHandle? = runCatching { um.getUserForSerialNumber(serial) }.getOrNull()

    // isUserUnlocked(UserHandle) untuk profil dalam grup yang sama diizinkan; dibungkus runCatching.
    @SuppressLint("MissingPermission")
    fun read(): ProfileState {
        val me = Process.myUserHandle()
        val targets = runCatching { cpa.targetUserProfiles.toSet() }.getOrDefault(emptySet())
        val others = um.userProfiles.filter { it != me }.map { h ->
            ProfileInfo(
                handle = h,
                serial = um.getSerialNumberForUser(h),
                label = h.toString(),
                hasClonApps = h in targets,
                quietMode = runCatching { um.isQuietModeEnabled(h) }.getOrDefault(false),
                unlocked = runCatching { um.isUserUnlocked(h) }.getOrNull(),
            )
        }
        val allowed = runCatching { dpm.isProvisioningAllowed(DevicePolicyManager.ACTION_PROVISION_MANAGED_PROFILE) }.getOrDefault(false)
        val feature = context.packageManager.hasSystemFeature(PackageManager.FEATURE_MANAGED_USERS)
        val uiPresent = provisioningIntent(context, null).resolveActivity(context.packageManager) != null

        val candidate = others.firstOrNull { it.serial == pairing.profileSerial && it.hasClonApps } ?: others.firstOrNull { it.hasClonApps }
        var unverified = false
        val ownership = when {
            candidate != null && pairing.hasSecret() && pairing.profileSerial == candidate.serial -> ProfileOwnership.OURS_PAIRED
            candidate != null -> {
                val hello = stores.hello(candidate.serial)
                when {
                    hello == null -> { unverified = true; ProfileOwnership.OURS_UNPAIRED }
                    hello.first -> ProfileOwnership.OURS_UNPAIRED
                    else -> ProfileOwnership.FOREIGN
                }
            }
            others.isNotEmpty() && !allowed -> ProfileOwnership.FOREIGN
            else -> ProfileOwnership.NONE
        }
        return ProfileState(
            ownership = ownership,
            profile = candidate,
            others = others.filter { it != candidate },
            provisioningAllowed = allowed,
            managedUsersFeature = feature,
            provisioningUiPresent = uiPresent,
            unverified = unverified,
        )
    }

    /** Jeda/lanjutkan profil. Android membatasi ini; kegagalan dilaporkan apa adanya. */
    // Sengaja dicoba: Android hanya mengizinkan launcher default / MODIFY_QUIET_MODE. Penolakan dilaporkan.
    @SuppressLint("MissingPermission")
    fun requestPause(handle: UserHandle, pause: Boolean): PauseResult = try {
        um.requestQuietModeEnabled(pause, handle)
        PauseResult.Requested
    } catch (e: SecurityException) {
        PauseResult.NotPermitted(e.message)
    } catch (e: RuntimeException) {
        PauseResult.Error(e.message)
    }

    /** Membuka ClonApps di profil kerja (activity peluncur milik ClonApps sendiri). */
    fun openClonAppsInProfile(handle: UserHandle): Boolean = runCatching {
        val main = context.packageManager.getLaunchIntentForPackage(context.packageName)?.component ?: return false
        cpa.startMainActivity(main, handle)
        true
    }.getOrDefault(false)

    companion object {
        fun provisioningIntent(context: Context, extras: PersistableBundle?): Intent =
            Intent(DevicePolicyManager.ACTION_PROVISION_MANAGED_PROFILE)
                .putExtra(DevicePolicyManager.EXTRA_PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME, ClonAppsAdminReceiver.component(context))
                // Biarkan aplikasi sistem tetap aktif di profil baru: kamera, pemilih file, browser,
                // dan layanan Google lebih mungkin tersedia bagi aplikasi clone.
                .putExtra(DevicePolicyManager.EXTRA_PROVISIONING_LEAVE_ALL_SYSTEM_APPS_ENABLED, true)
                .apply { if (extras != null) putExtra(DevicePolicyManager.EXTRA_PROVISIONING_ADMIN_EXTRAS_BUNDLE, extras) }

        fun adminExtras(secretHex: String, pairingId: String) = PersistableBundle().apply {
            putString(ProfileProtocol.ADMIN_EXTRA_SECRET, secretHex)
            putString(ProfileProtocol.ADMIN_EXTRA_PAIRING_ID, pairingId)
        }
    }
}
