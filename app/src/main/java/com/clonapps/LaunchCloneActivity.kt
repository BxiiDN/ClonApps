package com.clonapps

import android.app.Activity
import android.os.Bundle
import android.widget.Toast
import com.clonapps.core.model.InstallState
import com.clonapps.core.store.PackageNames

/**
 * Tujuan shortcut launcher. Tidak diekspor: hanya launcher (atas nama ClonApps lewat ShortcutManager)
 * dan ClonApps sendiri yang dapat membukanya. Hanya meluncurkan paket yang tercatat sebagai clone.
 */
class LaunchCloneActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as ClonAppsApp
        val pkg = intent.getStringExtra(EXTRA_PACKAGE)
        val serial = intent.getLongExtra(EXTRA_PROFILE_SERIAL, -1L)
        if (app.isProfileSide || intent.action != ACTION_LAUNCH_CLONE || pkg == null || !PackageNames.isValid(pkg)) {
            finish(); return
        }
        val c = app.coordinator
        c.refreshProfile()
        val record = c.records.value[pkg]
        if (record == null || record.installState != InstallState.INSTALLED || c.profile.value.profile?.serial != serial) {
            Toast.makeText(this, R.string.msg_shortcut_stale, Toast.LENGTH_LONG).show()
        } else if (!c.launchSync(pkg)) {
            Toast.makeText(this, R.string.msg_launch_failed, Toast.LENGTH_LONG).show()
        }
        finish()
    }

    companion object {
        const val ACTION_LAUNCH_CLONE = "com.clonapps.action.LAUNCH_CLONE"
        const val EXTRA_PACKAGE = "com.clonapps.extra.PACKAGE"
        const val EXTRA_PROFILE_SERIAL = "com.clonapps.extra.PROFILE_SERIAL"
    }
}
