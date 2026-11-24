package com.clonapps.core

import com.clonapps.core.model.CloneRecord
import com.clonapps.core.model.CloneStatus
import com.clonapps.core.model.FailureCause
import com.clonapps.core.model.InstallMethod
import com.clonapps.core.model.LaunchState
import com.clonapps.core.model.ProfileOwnership
import com.clonapps.core.model.StatusResolver
import com.clonapps.core.queue.CloneJob
import com.clonapps.core.queue.JobKind
import com.clonapps.core.queue.JobPhase
import com.clonapps.core.validate.Abis
import com.clonapps.core.validate.ApkFile
import com.clonapps.core.validate.ArchiveIdentity
import com.clonapps.core.validate.CheckId
import com.clonapps.core.validate.CheckOutcome
import com.clonapps.core.validate.InstallCapability
import com.clonapps.core.validate.PackageSnapshot
import com.clonapps.core.validate.PackageValidator
import com.clonapps.core.validate.Precheck
import com.clonapps.core.validate.PrecheckInput
import com.clonapps.core.validate.ProfileView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ValidationTest {
    private val arm64 = listOf("arm64-v8a", "armeabi-v7a", "armeabi")

    private fun apk(name: String, ok: Boolean = true, size: Long = 10_000_000) =
        ApkFile("/data/app/~~x/com.a-1/$name", exists = ok, readable = ok, sizeBytes = size)

    private fun snap(
        splits: List<String> = listOf("config.arm64_v8a", "config.xxhdpi"),
        files: List<ApkFile> = splits.map { apk("split_$it.apk") },
        identity: ArchiveIdentity? = ArchiveIdentity("com.a.app", 42),
        abis: Set<String> = setOf("arm64-v8a"),
        system: Boolean = false,
        shared: List<String> = emptyList(),
    ) = PackageSnapshot("com.a.app", 42, system, true, apk("base.apk"), splits, files, identity, abis, shared)

    private fun outcome(items: List<com.clonapps.core.validate.CheckItem>, id: CheckId) = items.first { it.id == id }

    @Test
    fun completeSplitApkPasses() {
        val items = PackageValidator.validate(snap(), arm64)
        items.forEach { assertEquals("${it.id}", CheckOutcome.PASS, it.outcome) }
    }

    @Test
    fun missingSplitDetected() {
        val s = snap(files = listOf(apk("split_config.arm64_v8a.apk")))
        val c = outcome(PackageValidator.validate(s, arm64), CheckId.SPLITS_COMPLETE)
        assertEquals(CheckOutcome.FAIL, c.outcome)
        assertEquals(FailureCause.SPLIT_MISSING, c.cause)
    }

    @Test
    fun unreadableSplitDetected() {
        val s = snap(files = listOf(apk("split_config.arm64_v8a.apk"), apk("split_config.xxhdpi.apk", ok = false)))
        assertEquals(CheckOutcome.FAIL, outcome(PackageValidator.validate(s, arm64), CheckId.SPLITS_COMPLETE).outcome)
    }

    @Test
    fun archiveMismatchMeansAppUpdatedMidway() {
        val s = snap(identity = ArchiveIdentity("com.a.app", 43))
        val c = outcome(PackageValidator.validate(s, arm64), CheckId.ARCHIVE_IDENTITY)
        assertEquals(FailureCause.ARCHIVE_MISMATCH, c.cause)
    }

    @Test
    fun abiMismatch() {
        val c = PackageValidator.abiCheck(snap(abis = setOf("x86_64")), arm64)
        assertEquals(CheckOutcome.FAIL, c.outcome)
        assertEquals(FailureCause.NO_MATCHING_ABI, c.cause)
        assertEquals(CheckOutcome.PASS, PackageValidator.abiCheck(snap(abis = emptySet()), arm64).outcome)
    }

    @Test
    fun abiParsing() {
        assertEquals("arm64-v8a", Abis.fromSplitName("config.arm64_v8a"))
        assertNull(Abis.fromSplitName("config.xxhdpi"))
        assertEquals("armeabi-v7a", Abis.fromZipEntry("lib/armeabi-v7a/libfoo.so"))
        assertNull(Abis.fromZipEntry("lib/arm64-v8a/readme.txt"))
        assertNull(Abis.fromZipEntry("assets/lib/arm64-v8a/x.so"))
    }

    @Test
    fun sharedLibraryDependencyWarns() {
        val c = outcome(PackageValidator.validate(snap(shared = listOf("/data/app/~~y/com.google.android.trichromelibrary_1/base.apk")), arm64), CheckId.SHARED_LIBRARIES)
        assertEquals(CheckOutcome.WARN, c.outcome)
    }

    private fun input(
        s: PackageSnapshot = snap(),
        owner: ProfileOwnership = ProfileOwnership.OURS_PAIRED,
        quiet: Boolean = false,
        free: Long? = 10L shl 30,
        profileVersion: Long? = null,
        known: Boolean = true,
        cap: InstallCapability = InstallCapability(false, true),
        sysAvail: Boolean? = null,
    ) = PrecheckInput("com.clonapps", s, arm64, ProfileView(owner, quiet, true), cap, free, profileVersion, known, sysAvail)

    @Test
    fun precheckReady() {
        val r = Precheck.evaluate(input())
        assertEquals(CloneStatus.READY, r.overall)
        assertEquals(InstallMethod.PACKAGE_INSTALLER_COPY, r.plannedMethod)
    }

    @Test
    fun insufficientStorage() {
        val r = Precheck.evaluate(input(free = 50L * 1024 * 1024))
        assertEquals(CloneStatus.NEEDS_USER_ACTION, r.overall)
        assertEquals(FailureCause.INSUFFICIENT_STORAGE, r.blocking.single().cause)
    }

    @Test
    fun noProfileAndPausedProfile() {
        assertEquals(CloneStatus.NEEDS_USER_ACTION, Precheck.evaluate(input(owner = ProfileOwnership.NONE)).overall)
        val paused = Precheck.evaluate(input(quiet = true))
        assertEquals(FailureCause.PROFILE_PAUSED, paused.blocking.single().cause)
        assertEquals(CloneStatus.NEEDS_USER_ACTION, paused.overall)
    }

    @Test
    fun foreignProfileIsFailureNotTakeover() {
        val r = Precheck.evaluate(input(owner = ProfileOwnership.FOREIGN))
        assertEquals(CloneStatus.FAILED, r.overall)
        assertEquals(FailureCause.FOREIGN_PROFILE_EXISTS, r.blocking.first().cause)
    }

    @Test
    fun alreadyPresentAndUpdated() {
        assertEquals(CloneStatus.INSTALLED, Precheck.evaluate(input(profileVersion = 42)).overall)
        val older = Precheck.evaluate(input(profileVersion = 40))
        assertEquals(CloneStatus.READY, older.overall)
        assertEquals(CheckOutcome.WARN, older.items.first { it.id == CheckId.ALREADY_IN_PROFILE }.outcome)
    }

    @Test
    fun systemAppsOnlyViaEnable() {
        val r = Precheck.evaluate(input(s = snap(system = true), sysAvail = false))
        assertEquals(CloneStatus.FAILED, r.overall)
        assertEquals(InstallMethod.ENABLE_SYSTEM_APP, r.plannedMethod)
        assertEquals(CloneStatus.READY, Precheck.evaluate(input(s = snap(system = true), sysAvail = true)).overall)
    }

    @Test
    fun selfIsRejected() {
        val s = snap().copy(packageName = "com.clonapps")
        assertEquals(CloneStatus.FAILED, Precheck.evaluate(input(s = s)).overall)
    }

    @Test
    fun unknownSourcesRestricted() {
        val r = Precheck.evaluate(input(cap = InstallCapability(true, false)))
        assertEquals(FailureCause.UNKNOWN_SOURCES_BLOCKED, r.blocking.single().cause)
    }

    @Test
    fun statusResolverNeverClaimsRunningWithoutConfirmation() {
        val rec = CloneRecord("com.a.app", 10, "A", launchState = LaunchState.REQUESTED)
        assertEquals(CloneStatus.LAUNCH_REQUESTED, StatusResolver.resolve(rec, null, true, null))
        assertEquals(CloneStatus.VERIFIED_RUNNING, StatusResolver.resolve(rec.copy(launchState = LaunchState.USER_CONFIRMED_RUNNING), null, true, null))
        assertEquals(CloneStatus.INSTALLED, StatusResolver.resolve(null, null, true, null))
        assertEquals(CloneStatus.NOT_CHECKED, StatusResolver.resolve(null, null, false, null))
        val job = CloneJob("j", JobKind.INSTALL, "com.a.app", "A", 10, JobPhase.AWAITING_USER, createdAt = 0, updatedAt = 0)
        assertEquals(CloneStatus.NEEDS_USER_ACTION, StatusResolver.resolve(null, job, false, null))
        assertEquals(CloneStatus.IN_PROGRESS, StatusResolver.resolve(null, job.copy(phase = JobPhase.INSTALLING), false, null))
    }
}
