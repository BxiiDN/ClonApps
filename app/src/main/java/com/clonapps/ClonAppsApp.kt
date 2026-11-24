package com.clonapps

import android.app.Application
import android.app.admin.DevicePolicyManager
import android.os.Build
import android.os.UserManager
import com.clonapps.domain.CloneCoordinator

class ClonAppsApp : Application() {
    /** True bila proses ini berjalan di dalam profil kerja (sebagai profile owner atau di profil asing). */
    val isProfileSide: Boolean by lazy {
        val owner = getSystemService(DevicePolicyManager::class.java).isProfileOwnerApp(packageName)
        val managed = Build.VERSION.SDK_INT >= 30 && getSystemService(UserManager::class.java).isManagedProfile
        owner || managed
    }

    /** Hanya dibuat di profil utama. */
    val coordinator: CloneCoordinator by lazy {
        check(!isProfileSide) { "Koordinator hanya berjalan di profil utama" }
        CloneCoordinator(this)
    }
}
