package com.clonapps.core

import com.clonapps.core.diag.Redactor
import com.clonapps.core.error.ErrorMapper
import com.clonapps.core.error.InstallerStatus
import com.clonapps.core.json.Json
import com.clonapps.core.json.JsonValue
import com.clonapps.core.model.CloneRecord
import com.clonapps.core.model.FailureCause
import com.clonapps.core.model.FailureStage
import com.clonapps.core.model.InstallState
import com.clonapps.core.security.CommandAuth
import com.clonapps.core.security.InMemoryNonceStore
import com.clonapps.core.store.AppSettings
import com.clonapps.core.store.CloneLabelEntry
import com.clonapps.core.store.ImportResult
import com.clonapps.core.store.RecordCodec
import com.clonapps.core.store.SettingsCodec
import com.clonapps.core.store.SettingsExport
import com.clonapps.core.store.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MappingAndSecurityTest {

    @Test
    fun installerErrorsMapToDistinctStages() {
        fun map(status: Int, msg: String?) = ErrorMapper.fromInstaller(status, msg)!!
        assertNull(ErrorMapper.fromInstaller(InstallerStatus.SUCCESS, null))
        assertEquals(FailureStage.ABI, map(7, "INSTALL_FAILED_NO_MATCHING_ABIS: Failed to extract native libraries").stage)
        assertEquals(FailureStage.DEPENDENCY, map(1, "INSTALL_FAILED_MISSING_SHARED_LIBRARY: Package couldn't be installed").stage)
        assertEquals(FailureCause.SPLIT_MISSING, map(4, "INSTALL_FAILED_MISSING_SPLIT: Missing split for com.a").cause)
        assertEquals(FailureCause.INSUFFICIENT_STORAGE, map(6, null).cause)
        assertEquals(FailureCause.USER_CANCELLED, map(3, null).cause)
        assertTrue(map(3, null).retryable)
        assertEquals(FailureCause.SIGNATURE_CONFLICT, map(5, "INSTALL_FAILED_UPDATE_INCOMPATIBLE: signatures do not match").cause)
        assertFalse(map(5, "INSTALL_FAILED_UPDATE_INCOMPATIBLE").retryable)
        assertEquals(FailureStage.TARGET_RESTRICTION, map(1, "INSTALL_FAILED_DEPRECATED_SDK_VERSION: App package must target at least SDK version 23").stage)
        assertEquals(FailureCause.INSTALL_BLOCKED_BY_POLICY, map(2, null).cause)
        // Incompatible tanpa pesan: jangan menebak ABI/SDK
        assertEquals(FailureCause.UNKNOWN, map(7, null).cause)
        assertEquals("PackageInstaller.STATUS_FAILURE_CONFLICT(5)", map(5, null).code)
    }

    @Test
    fun launchErrors() {
        assertEquals(FailureCause.LAUNCH_ACTIVITY_NOT_FOUND, ErrorMapper.fromLaunch("android.content.ActivityNotFoundException", "x").cause)
        assertEquals(FailureCause.LAUNCH_SECURITY_EXCEPTION, ErrorMapper.fromLaunch("java.lang.SecurityException", "not exported").cause)
        assertEquals(FailureStage.LAUNCH, ErrorMapper.fromLaunch("java.lang.RuntimeException", null).stage)
        assertEquals(FailureCause.PROVISIONING_CANCELLED, ErrorMapper.fromProvisioningResult(false)!!.cause)
    }

    @Test
    fun redactorRemovesSecretsButKeepsCodes() {
        val raw = "INSTALL_FAILED_MISSING_SHARED_LIBRARY user=alice@example.com token=abc123def456 " +
            "path=/data/user/10/com.a.app/files/x.db Bearer eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMjM0NTY3ODkwIn0.dozjgNryP4J3jVmNHl0w5N " +
            "ip 192.168.1.20 phone +62 812 3456 7890 android_id 9774d56d682e549c content://media/external/images/1 com.whatsapp v=123456789"
        val out = Redactor.redact(raw)
        assertFalse(out, out.contains("alice@example.com"))
        assertFalse(out, out.contains("abc123def456"))
        assertFalse(out, out.contains("com.a.app/files"))
        assertFalse(out, out.contains("eyJhbGciOiJIUzI1NiJ9"))
        assertFalse(out, out.contains("192.168.1.20"))
        assertFalse(out, out.contains("3456 7890"))
        assertFalse(out, out.contains("9774d56d682e549c"))
        assertFalse(out, out.contains("media/external"))
        assertTrue(out, out.contains("INSTALL_FAILED_MISSING_SHARED_LIBRARY"))
        assertTrue(out, out.contains("com.whatsapp"))
        assertTrue(out, out.contains("123456789"))
    }

    @Test
    fun commandAuthAcceptsValidRejectsTamperReplayAndStale() {
        var now = 1_000_000L
        val secret = CommandAuth.newSecret()
        val sender = CommandAuth(secret) { now }
        val receiver = CommandAuth(secret) { now }
        val store = InMemoryNonceStore()
        val signed = sender.sign("INSTALL", "com.a.app", "paths")
        assertEquals(CommandAuth.Verdict.Ok, receiver.verify(signed, store))
        assertTrue("replay", receiver.verify(signed, store) is CommandAuth.Verdict.Rejected)
        val tampered = sender.sign("INSTALL", "com.a.app").copy(packageName = "com.evil.app")
        assertTrue(receiver.verify(tampered, store) is CommandAuth.Verdict.Rejected)
        val other = CommandAuth(CommandAuth.newSecret()) { now }.sign("UNINSTALL", "com.a.app")
        assertTrue("kunci lain", receiver.verify(other, store) is CommandAuth.Verdict.Rejected)
        val old = sender.sign("PING", "-")
        now += CommandAuth.WINDOW_MS + 1
        assertTrue("kedaluwarsa", receiver.verify(old, store) is CommandAuth.Verdict.Rejected)
        assertEquals(32, CommandAuth.fromHex(CommandAuth.run { secret.toHex() })!!.size)
    }

    @Test
    fun jsonRoundTripAndLimits() {
        val text = """{"a":[1,2.5,-3,true,null,"x\n\"y\" é"],"b":{"c":9007199254740993}}"""
        val v = Json.parse(text) as JsonValue.Obj
        assertEquals(9007199254740993L, v.obj("b")!!.long("c"))
        assertEquals(v, Json.parse(Json.write(v)))
        assertEquals(v, Json.parse(Json.write(v, indent = true)))
        val deep = "[".repeat(100) + "]".repeat(100)
        assertTrue(runCatching { Json.parse(deep) }.isFailure)
        assertTrue(runCatching { Json.parse("{\"a\":1,}") }.isFailure)
        assertTrue(runCatching { Json.parse("[1] x") }.isFailure)
    }

    @Test
    fun settingsExportImport() {
        val e = SettingsExport(
            AppSettings(ThemeMode.DARK, dashboardLock = true, showSystemApps = true),
            listOf(CloneLabelEntry("com.a.app", "Kerja A", true), CloneLabelEntry("com.b.app", null, false)),
        )
        val text = SettingsCodec.export(e, 123, "1.0")
        assertFalse(text.contains("secret"))
        val r = SettingsCodec.import(text) as ImportResult.Ok
        assertEquals(e, r.export)

        assertTrue(SettingsCodec.import("""{"format":"other"}""") is ImportResult.Invalid)
        assertTrue(SettingsCodec.import("""{"format":"clonapps-settings","schema":99}""") is ImportResult.Invalid)
        val messy = """{"format":"clonapps-settings","schema":1,"clones":[{"package":"not a pkg"},{"package":"com.x.y","label":"${"L".repeat(80)}"}]}"""
        val m = SettingsCodec.import(messy) as ImportResult.Ok
        assertEquals(1, m.skipped)
        assertEquals(50, m.export.clones.single().customLabel!!.length)
    }

    @Test
    fun recordRoundTrip() {
        val r = listOf(CloneRecord("com.a.app", 10, "A", "Label", true, InstallState.INSTALLED, installedVersionCode = 5))
        assertEquals(r, RecordCodec.decode(RecordCodec.encode(r)))
        assertEquals(0, RecordCodec.decode("garbage").size)
    }
}
