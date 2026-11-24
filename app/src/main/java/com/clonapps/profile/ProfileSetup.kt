package com.clonapps.profile

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.PersistableBundle
import android.os.UserManager
import android.util.Log
import com.clonapps.admin.ClonAppsAdminReceiver
import com.clonapps.data.PairingStore

/**
 * Penyiapan yang dijalankan oleh ClonApps DI DALAM profil kerja, setelah Android menjadikannya
 * profile owner. Idempoten: aman dipanggil berkali-kali dari beberapa jalur sistem.
 */
object ProfileSetup {
    private const val TAG = "ClonAppsSetup"

    fun isProfileOwnerHere(context: Context): Boolean =
        context.getSystemService(DevicePolicyManager::class.java).isProfileOwnerApp(context.packageName)

    /** @return true bila ClonApps adalah profile owner dan langkah wajib berhasil. */
    fun finish(context: Context, adminExtras: PersistableBundle?, source: String): Boolean {
        val dpm = context.getSystemService(DevicePolicyManager::class.java)
        val admin = ClonAppsAdminReceiver.component(context)
        if (!dpm.isProfileOwnerApp(context.packageName)) {
            Log.w(TAG, "finish($source): bukan profile owner, tidak melakukan apa pun")
            return false
        }
        val store = PairingStore(context)
        val secret = adminExtras?.getString(ProfileProtocol.ADMIN_EXTRA_SECRET)
        if (secret != null && !store.hasSecret()) {
            runCatching { store.save(secret, adminExtras.getString(ProfileProtocol.ADMIN_EXTRA_PAIRING_ID)) }
                .onFailure { Log.w(TAG, "kunci dari admin extras ditolak", it) }
        }
        runCatching { dpm.setProfileName(admin, "ClonApps") }
        // Wajib bagi DPC: profil baru aktif setelah profile owner menyatakan siap.
        runCatching { dpm.setProfileEnabled(admin) }.onFailure { Log.w(TAG, "setProfileEnabled", it) }

        // Izinkan HANYA action perintah ClonApps menyeberang dari profil utama ke profil ini.
        runCatching {
            // CATEGORY_DEFAULT wajib: startActivity me-resolve dengan MATCH_DEFAULT_ONLY, termasuk
            // untuk cross-profile intent filter.
            val filter = IntentFilter(ProfileProtocol.ACTION).apply { addCategory(Intent.CATEGORY_DEFAULT) }
            dpm.addCrossProfileIntentFilter(admin, filter, DevicePolicyManager.FLAG_PARENT_CAN_ACCESS_MANAGED)
        }.onFailure { Log.w(TAG, "addCrossProfileIntentFilter", it) }

        // Komponen penerima perintah hanya aktif di profil kerja, agar di profil utama intent tidak
        // terselesaikan ke salinan lokal ClonApps (yang akan memunculkan pemilih aplikasi).
        context.packageManager.setComponentEnabledSetting(
            ComponentName(context, ProfileCommandActivity::class.java),
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            PackageManager.DONT_KILL_APP,
        )

        // Beberapa ROM memasang pembatasan sumber tidak dikenal di profil baru. Profile owner
        // hanya dapat menghapus pembatasan yang berada di bawah kewenangannya; hasilnya dicatat.
        runCatching { dpm.clearUserRestriction(admin, UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES) }
        if (Build.VERSION.SDK_INT >= 29) {
            runCatching { dpm.clearUserRestriction(admin, UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES_GLOBALLY) }
        }
        Log.i(TAG, "finish($source) selesai, paired=${store.hasSecret()}")
        return true
    }
}
