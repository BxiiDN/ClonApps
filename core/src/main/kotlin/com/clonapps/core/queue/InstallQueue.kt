package com.clonapps.core.queue

import com.clonapps.core.model.Failure
import com.clonapps.core.model.FailureCause
import com.clonapps.core.model.FailureStage
import com.clonapps.core.model.RecoveryAction

data class EnqueueRequest(
    val kind: JobKind,
    val packageName: String,
    val label: String,
    val profileSerial: Long,
    val expectedVersionCode: Long? = null,
)

data class EnqueueResult(val added: List<CloneJob>, val skippedDuplicates: List<String>)

/** Hasil pengamatan keadaan paket di profil tujuan saat pencocokan ulang. */
sealed class Observation {
    data class Present(val versionCode: Long) : Observation()
    object Absent : Observation()

    /** Profil tidak dapat diperiksa (dijeda, terkunci, belum tersedia). */
    object Unknown : Observation()
}

sealed class RetryDecision {
    object Allowed : RetryDecision()
    data class Rejected(val reason: String) : RetryDecision()
}

/**
 * Antrean persisten yang immutable. Setiap operasi mengembalikan state baru,
 * sehingga mudah disimpan atomik dan diuji.
 *
 * Satu kegagalan hanya mengubah job itu sendiri; job lain tetap berjalan.
 */
data class InstallQueue(val jobs: List<CloneJob> = emptyList()) {

    fun job(id: String): CloneJob? = jobs.firstOrNull { it.id == id }

    fun activeFor(packageName: String, profileSerial: Long, kind: JobKind? = null): CloneJob? =
        jobs.firstOrNull {
            it.packageName == packageName && it.profileSerial == profileSerial &&
                (kind == null || it.kind == kind) && it.phase.blocksDuplicate
        }

    /** Job yang sedang berjalan (paling banyak satu). */
    val running: CloneJob? get() = jobs.firstOrNull { it.phase.isRunning }

    /** Job berikutnya yang boleh diambil. Null bila ada job berjalan (pemrosesan berurutan). */
    fun next(): CloneJob? {
        if (running != null) return null
        return jobs.filter { it.phase == JobPhase.QUEUED }.minByOrNull { it.createdAt }
    }

    fun enqueue(requests: List<EnqueueRequest>, now: Long, newId: () -> String): Pair<InstallQueue, EnqueueResult> {
        val added = mutableListOf<CloneJob>()
        val skipped = mutableListOf<String>()
        var current = this
        for (r in requests) {
            val duplicate = current.activeFor(r.packageName, r.profileSerial)
            if (duplicate != null || added.any { it.packageName == r.packageName && it.profileSerial == r.profileSerial }) {
                skipped += r.packageName
                continue
            }
            val job = CloneJob(
                id = newId(),
                kind = r.kind,
                packageName = r.packageName,
                label = r.label,
                profileSerial = r.profileSerial,
                phase = JobPhase.QUEUED,
                expectedVersionCode = r.expectedVersionCode,
                createdAt = now,
                updatedAt = now,
            )
            added += job
            current = current.copy(jobs = current.jobs + job)
        }
        return current to EnqueueResult(added, skipped)
    }

    /**
     * Ubah fase job. Melempar [IllegalJobTransition] bila transisi tidak sah.
     * Masuk ke CHECKING dari QUEUED menambah hitungan percobaan.
     */
    fun transition(
        id: String,
        to: JobPhase,
        now: Long,
        update: (CloneJob) -> CloneJob = { it },
    ): InstallQueue {
        val job = job(id) ?: throw NoSuchElementException("Job $id tidak ditemukan")
        if (!JobStateMachine.canTransition(job.phase, to)) throw IllegalJobTransition(job.phase, to, id)
        if (to == JobPhase.CHECKING && job.phase == JobPhase.QUEUED) {
            check(running == null) { "Masih ada job berjalan: ${running?.id}" }
        }
        val attempts = if (job.phase == JobPhase.QUEUED && to == JobPhase.CHECKING) job.attempts + 1 else job.attempts
        val updated = update(job)
        val moved = updated.copy(
            phase = to,
            attempts = attempts,
            updatedAt = now,
            failure = if (to == JobPhase.QUEUED || to == JobPhase.SUCCEEDED) null else updated.failure,
            requestId = if (to == JobPhase.QUEUED || to.isTerminal) null else updated.requestId,
        )
        return replace(moved)
    }

    fun fail(id: String, failure: Failure, now: Long): InstallQueue =
        transition(id, JobPhase.FAILED, now) { it.copy(failure = failure.copy(atMillis = now)) }

    /** Pembatalan. Job INSTALLING boleh dibatalkan; pemanggil wajib meng-abandon session. */
    fun cancel(id: String, now: Long): InstallQueue {
        val job = job(id) ?: return this
        if (job.phase.isTerminal) return this
        return transition(id, JobPhase.CANCELLED, now) {
            it.copy(
                failure = Failure(
                    stage = FailureStage.CANCELLED,
                    cause = FailureCause.USER_CANCELLED,
                    code = "CANCELLED_BY_USER",
                    recovery = RecoveryAction.NONE,
                    retryable = false,
                    atMillis = now,
                ),
            )
        }
    }

    fun retryDecision(id: String): RetryDecision {
        val job = job(id) ?: return RetryDecision.Rejected("job tidak ditemukan")
        if (job.phase != JobPhase.FAILED) return RetryDecision.Rejected("job tidak dalam status gagal")
        val f = job.failure
        if (f != null && !f.retryable) return RetryDecision.Rejected("kegagalan ${f.cause} tidak dapat diperbaiki dengan mencoba lagi")
        if (job.attempts >= job.maxAttempts) return RetryDecision.Rejected("batas ${job.maxAttempts} percobaan tercapai")
        return RetryDecision.Allowed
    }

    fun retry(id: String, now: Long): Pair<InstallQueue, RetryDecision> {
        val d = retryDecision(id)
        if (d is RetryDecision.Rejected) return this to d
        return transition(id, JobPhase.QUEUED, now) { it.copy(sessionId = null) } to d
    }

    /**
     * Dipanggil saat ClonApps mulai lagi (setelah rotasi tidak perlu; setelah process death
     * atau reboot wajib). Job yang tercatat sedang berjalan dicocokkan dengan keadaan nyata
     * paket di profil sebelum antrean dilanjutkan.
     *
     * [includeRunning] = false dipakai bila proses masih hidup dan job yang berjalan memang sedang
     * menunggu balasan — hanya job INTERRUPTED yang dicocokkan.
     */
    fun reconcile(now: Long, includeRunning: Boolean = true, observe: (CloneJob) -> Observation): InstallQueue {
        var q = this
        for (job in jobs) {
            val eligible = job.phase == JobPhase.INTERRUPTED || (includeRunning && job.phase.isRunning)
            if (!eligible) continue
            val obs = observe(job)
            val wanted = when (job.kind) {
                JobKind.INSTALL -> obs is Observation.Present &&
                    (job.expectedVersionCode == null || obs.versionCode >= job.expectedVersionCode)
                JobKind.UNINSTALL -> obs is Observation.Absent
            }
            if (job.phase.isRunning) {
                q = q.transition(job.id, JobPhase.INTERRUPTED, now) {
                    it.copy(note = "Proses terputus pada fase ${job.phase}")
                }
            }
            q = when {
                wanted -> q.transition(job.id, JobPhase.SUCCEEDED, now) {
                    it.copy(note = "Dicocokkan ulang: keadaan profil sudah sesuai", sessionId = null)
                }
                obs is Observation.Unknown -> q // tetap INTERRUPTED sampai profil dapat diperiksa
                job.attempts < job.maxAttempts -> q.transition(job.id, JobPhase.QUEUED, now) {
                    it.copy(sessionId = null, note = "Dicocokkan ulang: belum selesai, diantrekan lagi")
                }
                else -> q.transition(job.id, JobPhase.FAILED, now) {
                    it.copy(
                        sessionId = null,
                        failure = Failure(
                            stage = if (job.kind == JobKind.INSTALL) FailureStage.INSTALL else FailureStage.UNINSTALL,
                            cause = FailureCause.INTERRUPTED,
                            code = "INTERRUPTED_MAX_ATTEMPTS",
                            detail = "Terputus ${job.attempts} kali",
                            recovery = RecoveryAction.COLLECT_ADB_LOG,
                            retryable = false,
                            atMillis = now,
                        ),
                    )
                }
            }
        }
        return q
    }

    /** Buang job selesai yang lama, simpan maksimal [keepFinished]. */
    fun prune(keepFinished: Int = 100): InstallQueue {
        val finished = jobs.filter { it.phase.isTerminal || it.phase == JobPhase.FAILED }
        if (finished.size <= keepFinished) return this
        val drop = finished.sortedBy { it.updatedAt }.take(finished.size - keepFinished).map { it.id }.toSet()
        return copy(jobs = jobs.filterNot { it.id in drop })
    }

    private fun replace(job: CloneJob): InstallQueue = copy(jobs = jobs.map { if (it.id == job.id) job else it })
}
