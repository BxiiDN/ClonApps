package com.clonapps.profile

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.util.Log
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** Peristiwa dari PackageInstaller (install atau uninstall) di profil kerja. */
data class InstallerEvent(
    val opKey: String,
    val status: Int,
    val message: String?,
    val packageName: String?,
    val confirmIntent: Intent?,
)

object InstallerEvents {
    private val _events = MutableSharedFlow<InstallerEvent>(replay = 8, extraBufferCapacity = 32)
    val events: SharedFlow<InstallerEvent> = _events.asSharedFlow()
    fun emit(e: InstallerEvent) { _events.tryEmit(e) }
}

/**
 * Menerima status session PackageInstaller. Tidak diekspor: hanya PendingIntent milik ClonApps
 * yang dapat memicunya. Tidak memulai activity dari latar belakang; konfirmasi pengguna
 * dijalankan oleh [ProfileCommandActivity] yang sedang tampil.
 */
class InstallStatusReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val key = intent.getStringExtra(EXTRA_OP_KEY) ?: return
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, Int.MIN_VALUE)
        val confirm: Intent? = if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            if (Build.VERSION.SDK_INT >= 33) {
                intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(Intent.EXTRA_INTENT)
            }
        } else {
            null
        }
        val msg = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
        Log.i("ClonAppsInstall", "op=$key status=$status")
        InstallerEvents.emit(
            InstallerEvent(key, status, msg, intent.getStringExtra(PackageInstaller.EXTRA_PACKAGE_NAME), confirm),
        )
    }

    companion object {
        const val EXTRA_OP_KEY = "com.clonapps.extra.OP_KEY"
        fun installKey(sessionId: Int) = "install:$sessionId"
        fun uninstallKey(pkg: String) = "uninstall:$pkg"
    }
}
