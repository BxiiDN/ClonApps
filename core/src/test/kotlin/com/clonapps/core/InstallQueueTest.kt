package com.clonapps.core

import com.clonapps.core.model.Failure
import com.clonapps.core.model.FailureCause
import com.clonapps.core.model.FailureStage
import com.clonapps.core.model.RecoveryAction
import com.clonapps.core.queue.EnqueueRequest
import com.clonapps.core.queue.IllegalJobTransition
import com.clonapps.core.queue.InstallQueue
import com.clonapps.core.queue.JobKind
import com.clonapps.core.queue.JobPhase
import com.clonapps.core.queue.JobStateMachine
import com.clonapps.core.queue.Observation
import com.clonapps.core.queue.RetryDecision
import com.clonapps.core.store.QueueCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class InstallQueueTest {
    private var seq = 0
    private val ids = { "job-${++seq}" }

    private fun req(pkg: String, kind: JobKind = JobKind.INSTALL, v: Long? = 10) =
        EnqueueRequest(kind, pkg, pkg.substringAfterLast('.'), profileSerial = 10, expectedVersionCode = v)

    private fun failure(retryable: Boolean, cause: FailureCause = FailureCause.UNKNOWN) =
        Failure(FailureStage.INSTALL, cause, "X", null, RecoveryAction.RETRY, retryable)

    @Test
    fun enqueueSkipsDuplicatesFromRepeatedTaps() {
        val (q1, r1) = InstallQueue().enqueue(listOf(req("com.a.app"), req("com.a.app"), req("com.b.app")), 1, ids)
        assertEquals(2, r1.added.size)
        assertEquals(listOf("com.a.app"), r1.skippedDuplicates)
        val (q2, r2) = q1.enqueue(listOf(req("com.a.app")), 2, ids)
        assertEquals(0, r2.added.size)
        assertEquals(q1, q2)
    }

    @Test
    fun processesSequentiallyAndCountsAttempts() {
        val (q, _) = InstallQueue().enqueue(listOf(req("com.a.app"), req("com.b.app")), 1, ids)
        val first = q.next()!!
        assertEquals("com.a.app", first.packageName)
        val q2 = q.transition(first.id, JobPhase.CHECKING, 2)
        assertEquals(1, q2.job(first.id)!!.attempts)
        assertNull("tidak boleh mengambil job kedua saat job pertama berjalan", q2.next())
        try {
            q2.transition(q2.jobs[1].id, JobPhase.CHECKING, 3)
            fail("harus menolak dua job berjalan")
        } catch (e: IllegalStateException) { /* ok */ }
    }

    @Test
    fun oneFailureDoesNotCancelOthers() {
        val (q, _) = InstallQueue().enqueue(listOf(req("com.a.app"), req("com.b.app"), req("com.c.app")), 1, ids)
        val a = q.next()!!
        var s = q.transition(a.id, JobPhase.CHECKING, 2).fail(a.id, failure(false, FailureCause.NO_MATCHING_ABI), 3)
        assertEquals(JobPhase.FAILED, s.job(a.id)!!.phase)
        val b = s.next()!!
        assertEquals("com.b.app", b.packageName)
        s = s.transition(b.id, JobPhase.CHECKING, 4).transition(b.id, JobPhase.INSTALLING, 5)
            .transition(b.id, JobPhase.VERIFYING, 6).transition(b.id, JobPhase.SUCCEEDED, 7)
        assertEquals("com.c.app", s.next()!!.packageName)
    }

    @Test
    fun illegalTransitionsRejected() {
        assertFalse(JobStateMachine.canTransition(JobPhase.QUEUED, JobPhase.SUCCEEDED))
        assertFalse(JobStateMachine.canTransition(JobPhase.SUCCEEDED, JobPhase.QUEUED))
        assertFalse(JobStateMachine.canTransition(JobPhase.CANCELLED, JobPhase.CHECKING))
        assertTrue(JobStateMachine.canTransition(JobPhase.INSTALLING, JobPhase.AWAITING_USER))
        val (q, _) = InstallQueue().enqueue(listOf(req("com.a.app")), 1, ids)
        try {
            q.transition(q.jobs[0].id, JobPhase.SUCCEEDED, 2)
            fail()
        } catch (e: IllegalJobTransition) {
            assertEquals(JobPhase.QUEUED, e.from)
        }
    }

    @Test
    fun cancelFromEachCancellablePhase() {
        for (phaseSteps in listOf(
            emptyList(),
            listOf(JobPhase.CHECKING),
            listOf(JobPhase.CHECKING, JobPhase.AWAITING_USER),
            listOf(JobPhase.CHECKING, JobPhase.INSTALLING),
        )) {
            var (q, _) = InstallQueue().enqueue(listOf(req("com.a.app")), 1, ids)
            val id = q.jobs[0].id
            phaseSteps.forEachIndexed { i, p -> q = q.transition(id, p, 2L + i) }
            q = q.cancel(id, 10)
            assertEquals(JobPhase.CANCELLED, q.job(id)!!.phase)
            assertEquals(FailureCause.USER_CANCELLED, q.job(id)!!.failure!!.cause)
            // Paket boleh diantrekan lagi setelah dibatalkan
            val (_, r) = q.enqueue(listOf(req("com.a.app")), 11, ids)
            assertEquals(1, r.added.size)
        }
    }

    @Test
    fun retryIsBoundedAndRespectsRetryable() {
        var (q, _) = InstallQueue().enqueue(listOf(req("com.a.app")), 1, ids)
        val id = q.jobs[0].id
        q = q.transition(id, JobPhase.CHECKING, 2).fail(id, failure(false), 3)
        assertTrue(q.retryDecision(id) is RetryDecision.Rejected)

        var (r, _) = InstallQueue().enqueue(listOf(req("com.b.app")), 1, ids)
        val rid = r.jobs[0].id
        repeat(3) { n ->
            r = r.transition(rid, JobPhase.CHECKING, 10L + n).fail(rid, failure(true), 11L + n)
            val (next, d) = r.retry(rid, 12L + n)
            if (n < 2) {
                assertEquals(RetryDecision.Allowed, d)
                assertEquals(JobPhase.QUEUED, next.job(rid)!!.phase)
                assertNull(next.job(rid)!!.failure)
            } else {
                assertTrue("percobaan ke-4 harus ditolak", d is RetryDecision.Rejected)
            }
            r = next
        }
        assertEquals(3, r.job(rid)!!.attempts)
    }

    @Test
    fun reconcileAfterProcessDeath() {
        var (q, _) = InstallQueue().enqueue(
            listOf(req("com.present.app", v = 5), req("com.absent.app"), req("com.unknown.app"), req("com.old.app", v = 9)),
            1, ids,
        )
        // Simulasikan empat job yang pernah berjalan. Hanya satu yang boleh berjalan tiap waktu,
        // jadi tandai satu per satu lalu "process death" menyisakan fase masing-masing.
        val idsByPkg = q.jobs.associate { it.packageName to it.id }
        q = q.transition(idsByPkg["com.present.app"]!!, JobPhase.CHECKING, 2)
            .transition(idsByPkg["com.present.app"]!!, JobPhase.INSTALLING, 3) { it.copy(sessionId = 77) }
        // Pulihkan dari disk (process death)
        q = QueueCodec.decode(QueueCodec.encode(q))
        assertEquals(77, q.job(idsByPkg["com.present.app"]!!)!!.sessionId)

        val observed = mapOf(
            "com.present.app" to Observation.Present(5),
            "com.absent.app" to Observation.Absent,
            "com.unknown.app" to Observation.Unknown,
            "com.old.app" to Observation.Present(3),
        )
        q = q.reconcile(100) { observed.getValue(it.packageName) }
        assertEquals(JobPhase.SUCCEEDED, q.job(idsByPkg["com.present.app"]!!)!!.phase)
        assertNull(q.job(idsByPkg["com.present.app"]!!)!!.sessionId)
        // Yang lain masih QUEUED (tidak berjalan) jadi tidak diubah reconcile
        assertEquals(JobPhase.QUEUED, q.job(idsByPkg["com.absent.app"]!!)!!.phase)
    }

    @Test
    fun reconcileInterruptedAbsentRequeuesThenFailsAtLimit() {
        var (q, _) = InstallQueue().enqueue(listOf(req("com.a.app")), 1, ids)
        val id = q.jobs[0].id
        repeat(3) { n ->
            q = q.transition(id, JobPhase.CHECKING, 10L + n).transition(id, JobPhase.INSTALLING, 11L + n)
            q = q.reconcile(12L + n) { Observation.Absent }
        }
        val j = q.job(id)!!
        assertEquals(JobPhase.FAILED, j.phase)
        assertEquals(FailureCause.INTERRUPTED, j.failure!!.cause)
    }

    @Test
    fun reconcileUnknownKeepsInterruptedUntilObservable() {
        var (q, _) = InstallQueue().enqueue(listOf(req("com.a.app")), 1, ids)
        val id = q.jobs[0].id
        q = q.transition(id, JobPhase.CHECKING, 2).transition(id, JobPhase.INSTALLING, 3)
        q = q.reconcile(4) { Observation.Unknown }
        assertEquals(JobPhase.INTERRUPTED, q.job(id)!!.phase)
        assertNull("job terputus tidak boleh diambil otomatis", q.next())
        val (_, dup) = q.enqueue(listOf(req("com.a.app")), 5, ids)
        assertEquals("job terputus tetap mencegah duplikat", 0, dup.added.size)
        q = q.reconcile(6) { Observation.Present(10) }
        assertEquals(JobPhase.SUCCEEDED, q.job(id)!!.phase)
    }

    @Test
    fun uninstallReconcile() {
        var (q, _) = InstallQueue().enqueue(listOf(req("com.a.app", kind = JobKind.UNINSTALL)), 1, ids)
        val id = q.jobs[0].id
        q = q.transition(id, JobPhase.CHECKING, 2).transition(id, JobPhase.AWAITING_USER, 3)
        q = q.reconcile(4) { Observation.Absent }
        assertEquals(JobPhase.SUCCEEDED, q.job(id)!!.phase)
    }

    @Test
    fun corruptedQueueFileDoesNotCrash() {
        assertEquals(0, QueueCodec.decode("{not json").jobs.size)
        assertEquals(0, QueueCodec.decode("""{"jobs":[{"id":"x","package":"bad name"}]}""").jobs.size)
    }

    @Test
    fun pruneKeepsActiveJobs() {
        var (q, _) = InstallQueue().enqueue((1..10).map { req("com.app$it.x") }, 1, ids)
        q.jobs.take(8).forEachIndexed { i, j -> q = q.cancel(j.id, 10L + i) }
        q = q.prune(keepFinished = 3)
        assertEquals(5, q.jobs.size)
        assertEquals(2, q.jobs.count { it.phase == JobPhase.QUEUED })
    }
}

class ReconcileLiveProcessTest {
    @org.junit.Test
    fun liveRunningJobIsNotTouchedWhenIncludeRunningFalse() {
        var n = 0
        var (q, _) = InstallQueue().enqueue(listOf(EnqueueRequest(JobKind.INSTALL, "com.a.app", "a", 10, 1)), 1) { "j${++n}" }
        q = q.transition("j1", JobPhase.CHECKING, 2).transition("j1", JobPhase.AWAITING_USER, 3)
        val after = q.reconcile(4, includeRunning = false) { Observation.Absent }
        org.junit.Assert.assertEquals(JobPhase.AWAITING_USER, after.job("j1")!!.phase)
    }
}
