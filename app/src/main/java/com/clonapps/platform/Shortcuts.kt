package com.clonapps.platform

import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.Bitmap
import android.graphics.drawable.Icon
import com.clonapps.LaunchCloneActivity

sealed class ShortcutResult {
    object Requested : ShortcutResult()
    object NotSupported : ShortcutResult()
    data class Error(val message: String?) : ShortcutResult()
}

/**
 * Shortcut membuka [LaunchCloneActivity] milik ClonApps, yang lalu meluncurkan aplikasi di profil kerja.
 * Label/ikon shortcut hanya tampilan di launcher; identitas paket aplikasi target tidak berubah.
 */
object Shortcuts {
    fun requestPin(context: Context, pkg: String, profileSerial: Long, label: String, icon: Bitmap?): ShortcutResult {
        val sm = context.getSystemService(ShortcutManager::class.java)
        if (!sm.isRequestPinShortcutSupported) return ShortcutResult.NotSupported
        val intent = Intent(context, LaunchCloneActivity::class.java)
            .setAction(LaunchCloneActivity.ACTION_LAUNCH_CLONE)
            .putExtra(LaunchCloneActivity.EXTRA_PACKAGE, pkg)
            .putExtra(LaunchCloneActivity.EXTRA_PROFILE_SERIAL, profileSerial)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        val builder = ShortcutInfo.Builder(context, "clone:$profileSerial:$pkg")
            .setShortLabel(label.take(24))
            .setLongLabel(label)
            .setIntent(intent)
        icon?.let { builder.setIcon(Icon.createWithBitmap(it)) }
        return try {
            if (sm.requestPinShortcut(builder.build(), null)) ShortcutResult.Requested else ShortcutResult.Error("ditolak launcher")
        } catch (e: RuntimeException) {
            ShortcutResult.Error(e.message)
        }
    }
}
