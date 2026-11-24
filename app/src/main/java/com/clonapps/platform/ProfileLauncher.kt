package com.clonapps.platform

import android.content.Context
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.UserHandle
import com.clonapps.core.error.ErrorMapper
import com.clonapps.core.model.Failure
import com.clonapps.core.model.FailureCause
import com.clonapps.core.model.FailureStage
import com.clonapps.core.model.RecoveryAction

sealed class Presence {
    object Present : Presence()
    object Absent : Presence()
    data class Unknown(val reason: String) : Presence()
}

sealed class LaunchOutcome {
    /** startMainActivity diterima sistem untuk komponen milik profil tujuan. Bukan bukti aplikasi berjalan. */
    data class Requested(val component: String, val user: String) : LaunchOutcome()
    data class Failed(val failure: Failure) : LaunchOutcome()
}

/**
 * Peluncuran memakai LauncherApps dengan UserHandle profil kerja. Komponen diambil dari daftar
 * activity MILIK profil tujuan, sehingga sistem tidak membuka salinan asli di profil utama.
 */
class ProfileLauncher(context: Context) {
    private val la = context.getSystemService(LauncherApps::class.java)

    fun presence(pkg: String, user: UserHandle): Presence = try {
        la.getApplicationInfo(pkg, 0, user)
        Presence.Present
    } catch (e: PackageManager.NameNotFoundException) {
        Presence.Absent
    } catch (e: RuntimeException) {
        Presence.Unknown("${e.javaClass.simpleName}: ${e.message}")
    }

    fun packagesInProfile(user: UserHandle): Set<String> =
        runCatching { la.getActivityList(null, user).map { it.componentName.packageName }.toSet() }.getOrDefault(emptySet())

    fun launch(pkg: String, user: UserHandle, now: Long): LaunchOutcome {
        val activities = try {
            la.getActivityList(pkg, user)
        } catch (e: RuntimeException) {
            return LaunchOutcome.Failed(ErrorMapper.fromLaunch(e.javaClass.name, e.message, now))
        }
        val target = activities.firstOrNull { it.user == user }
            ?: return LaunchOutcome.Failed(
                Failure(FailureStage.LAUNCH, FailureCause.NO_LAUNCHER_ACTIVITY, "NO_LAUNCHER_ACTIVITY_IN_PROFILE", null, RecoveryAction.RETRY, true, now),
            )
        return try {
            la.startMainActivity(target.componentName, user, null, null)
            LaunchOutcome.Requested(target.componentName.flattenToShortString(), user.toString())
        } catch (e: RuntimeException) {
            LaunchOutcome.Failed(ErrorMapper.fromLaunch(e.javaClass.name, e.message, now))
        }
    }

    /** Ikon dengan lencana profil kerja dari sistem; label tetap milik aplikasi target. */
    fun badgedIcon(pkg: String, user: UserHandle, sizePx: Int): Bitmap? = runCatching {
        la.getActivityList(pkg, user).firstOrNull()?.getBadgedIcon(0)?.toBitmap(sizePx)
    }.getOrNull()
}

fun Drawable.toBitmap(sizePx: Int): Bitmap {
    if (this is BitmapDrawable && bitmap != null && bitmap.width == sizePx) return bitmap
    val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    setBounds(0, 0, sizePx, sizePx)
    draw(c)
    return bmp
}
