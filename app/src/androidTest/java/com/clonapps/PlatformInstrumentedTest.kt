package com.clonapps

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.clonapps.core.model.ProfileOwnership
import com.clonapps.data.JsonFile
import com.clonapps.data.PairingStore
import com.clonapps.data.Stores
import com.clonapps.platform.AppDiscovery
import com.clonapps.platform.DeviceProbe
import com.clonapps.platform.ProfileEnvironment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Dijalankan di emulator/perangkat. Tidak membuat profil kerja (butuh persetujuan pengguna). */
@RunWith(AndroidJUnit4::class)
class PlatformInstrumentedTest {
    private val ctx = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun deviceProbeReadsRealValues() {
        val d = DeviceProbe.probe(ctx)
        assertEquals(Build.VERSION.SDK_INT, d.sdkInt)
        assertTrue(d.supportedAbis.isNotEmpty())
        assertTrue(d.dataTotalBytes > 0)
    }

    @Test
    fun snapshotOfOwnPackageIsConsistent() {
        val s = AppDiscovery(ctx).snapshot(ctx.packageName)
        assertNotNull(s)
        s!!
        assertTrue(s.base!!.readable)
        assertEquals(s.packageName, s.archiveIdentity!!.packageName)
        assertEquals(s.versionCode, s.archiveIdentity!!.versionCode)
        assertEquals(s.declaredSplitNames.size, s.splitFiles.size)
    }

    @Test
    fun listAppsExcludesSelfAndSystemComponentsWithoutLauncher() {
        val apps = AppDiscovery(ctx).listApps(includeSystem = true)
        assertTrue(apps.none { it.packageName == ctx.packageName })
        assertTrue(apps.filter { it.isSystem }.all { it.hasLauncher })
    }

    @Test
    fun profileEnvironmentDoesNotCrashAndNeverClaimsOwnershipWithoutKey() {
        val pairing = PairingStore(ctx).also { it.clear() }
        val state = ProfileEnvironment(ctx, pairing, Stores(ctx)).read()
        assertTrue(state.ownership != ProfileOwnership.OURS_PAIRED)
    }

    @Test
    fun atomicJsonFileSurvivesRewrite() {
        val dir = File(ctx.cacheDir, "atomic-test").apply { deleteRecursively(); mkdirs() }
        val f = JsonFile(dir, "x.json")
        f.write("""{"a":1}""")
        f.write("""{"a":2}""")
        assertEquals("""{"a":2}""", JsonFile(dir, "x.json").read())
    }
}
