package com.clonapps

import com.clonapps.core.json.jObj
import com.clonapps.core.json.jStr
import com.clonapps.core.model.CloneStatus
import com.clonapps.core.model.FailureCause
import com.clonapps.core.model.FailureStage
import com.clonapps.core.model.InstallMethod
import com.clonapps.core.model.RecoveryAction
import com.clonapps.core.validate.CheckId
import com.clonapps.core.validate.CheckItem
import com.clonapps.core.validate.CheckOutcome
import com.clonapps.core.validate.PrecheckResult
import com.clonapps.domain.Failures
import com.clonapps.profile.ProfileProtocol
import com.clonapps.profile.ProfileProtocol.ResultCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProtocolAndFailuresTest {
    @Test
    fun installPayloadRoundTrip() {
        val p = ProfileProtocol.InstallPayload("/data/app/~~a/com.x-1/base.apk", listOf("/data/app/~~a/com.x-1/split_config.arm64_v8a.apk"), 42, false, 1234)
        assertEquals(p, ProfileProtocol.InstallPayload.decode(p.encode()))
        assertNull(ProfileProtocol.InstallPayload.decode("{}"))
        assertNull(ProfileProtocol.InstallPayload.decode("not json"))
    }

    @Test
    fun resultRoundTrip() {
        val r = ProfileProtocol.Result(
            ResultCode.INSTALLER_FAILED, "INSTALL_FAILED_NO_MATCHING_ABIS", installerStatus = 7,
            method = InstallMethod.PACKAGE_INSTALLER_COPY, versionCode = 5, sessionId = 9, steps = listOf("a", "b"),
            info = jObj("k" to jStr("v")),
        )
        assertEquals(r, ProfileProtocol.Result.decode(r.encode()))
        assertEquals(ResultCode.INTERNAL_ERROR, ProfileProtocol.Result.decode("""{"code":"???"}""")!!.code)
    }

    @Test
    fun profileResultsMapToDistinctFailures() {
        fun f(code: ResultCode, status: Int? = null, msg: String? = null) =
            Failures.fromResult(ProfileProtocol.Result(code, msg, installerStatus = status), FailureStage.INSTALL, 1)
        assertEquals(FailureStage.ABI, f(ResultCode.INSTALLER_FAILED, 7, "INSTALL_FAILED_NO_MATCHING_ABIS").stage)
        assertEquals(FailureCause.AUTH_FAILED, f(ResultCode.AUTH_FAILED).cause)
        assertEquals(RecoveryAction.REPAIR_PAIRING, f(ResultCode.NOT_PAIRED).recovery)
        assertEquals(FailureStage.PACKAGE_INCOMPLETE, f(ResultCode.FILES_UNREADABLE).stage)
        assertEquals(FailureCause.USER_CANCELLED, f(ResultCode.USER_DECLINED).cause)
        assertEquals(FailureCause.NOT_PRESENT_AFTER_INSTALL, f(ResultCode.NOT_INSTALLED).cause)
        assertTrue(f(ResultCode.TIMEOUT).retryable)
        // Pesan dari sistem disensor
        val token = f(ResultCode.INTERNAL_ERROR, msg = "token=abcdef0123456789 user a@b.co")
        assertTrue(token.detail!!, !token.detail!!.contains("abcdef0123456789") && !token.detail!!.contains("a@b.co"))
    }

    @Test
    fun precheckFailureUsesFirstBlockingItem() {
        val pre = PrecheckResult(
            "com.x.y",
            listOf(
                CheckItem(CheckId.STORAGE, CheckOutcome.FAIL, FailureCause.INSUFFICIENT_STORAGE, "bebas=1"),
                CheckItem(CheckId.ABI, CheckOutcome.FAIL, FailureCause.NO_MATCHING_ABI),
            ),
            CloneStatus.FAILED, InstallMethod.PACKAGE_INSTALLER_COPY, 0,
        )
        val f = Failures.fromPrecheck(pre, 1)
        assertEquals(FailureCause.INSUFFICIENT_STORAGE, f.cause)
        assertEquals(RecoveryAction.FREE_STORAGE, f.recovery)
        assertTrue(f.detail!!.contains("ABI"))
        assertTrue(f.retryable)
    }
}
