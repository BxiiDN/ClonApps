package com.clonapps.admin

import android.app.Activity
import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.PersistableBundle
import android.util.Log
import com.clonapps.profile.ProfileSetup

/**
 * Device admin receiver. ClonApps hanya menjadi profile owner untuk profil kerja yang dibuatnya
 * sendiri melalui layar provisioning Android — tidak pernah device owner.
 */
class ClonAppsAdminReceiver : DeviceAdminReceiver() {

    /** Jalur lama (dan sebagian ROM): broadcast PROFILE_PROVISIONING_COMPLETE di dalam profil. */
    override fun onProfileProvisioningComplete(context: Context, intent: Intent) {
        Log.i(TAG, "onProfileProvisioningComplete")
        ProfileSetup.finish(context, adminExtras(intent), source = "broadcast")
    }

    companion object {
        const val TAG = "ClonAppsAdmin"
        fun component(context: Context) = ComponentName(context, ClonAppsAdminReceiver::class.java)

        fun adminExtras(intent: Intent?): PersistableBundle? = intent?.let {
            if (Build.VERSION.SDK_INT >= 33) {
                it.getParcelableExtra(DevicePolicyManager.EXTRA_PROVISIONING_ADMIN_EXTRAS_BUNDLE, PersistableBundle::class.java)
            } else {
                @Suppress("DEPRECATION")
                it.getParcelableExtra(DevicePolicyManager.EXTRA_PROVISIONING_ADMIN_EXTRAS_BUNDLE)
            }
        }
    }
}

/**
 * ACTION_GET_PROVISIONING_MODE (Android 10+). Wajib ada untuk DPC modern. ClonApps hanya
 * mendukung profil kerja (bukan device owner), jadi selalu mengembalikan mode managed profile.
 */
class GetProvisioningModeActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val allowed = if (Build.VERSION.SDK_INT >= 31) {
            intent.getIntegerArrayListExtra(DevicePolicyManager.EXTRA_PROVISIONING_ALLOWED_PROVISIONING_MODES)
        } else {
            null
        }
        val profileMode = if (Build.VERSION.SDK_INT >= 31) DevicePolicyManager.PROVISIONING_MODE_MANAGED_PROFILE else 2
        if (allowed != null && profileMode !in allowed) {
            setResult(RESULT_CANCELED)
        } else {
            val result = Intent().putExtra(DevicePolicyManager.EXTRA_PROVISIONING_MODE, profileMode)
            ClonAppsAdminReceiver.adminExtras(intent)?.let {
                result.putExtra(DevicePolicyManager.EXTRA_PROVISIONING_ADMIN_EXTRAS_BUNDLE, it)
            }
            setResult(RESULT_OK, result)
        }
        finish()
    }
}

/**
 * ACTION_ADMIN_POLICY_COMPLIANCE — dipanggil sistem di dalam profil kerja setelah provisioning
 * (Android 10+, wajib untuk DPC target Android 12+). Menyelesaikan penyiapan profil.
 */
class PolicyComplianceActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val ok = ProfileSetup.finish(this, ClonAppsAdminReceiver.adminExtras(intent), source = "policy_compliance")
        setResult(if (ok) RESULT_OK else RESULT_CANCELED)
        finish()
    }
}

/** ACTION_PROVISIONING_SUCCESSFUL (Android 8+) — dikirim ke DPC di dalam profil kerja. */
class ProvisioningSuccessActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ProfileSetup.finish(this, ClonAppsAdminReceiver.adminExtras(intent), source = "provisioning_successful")
        finish()
    }
}
