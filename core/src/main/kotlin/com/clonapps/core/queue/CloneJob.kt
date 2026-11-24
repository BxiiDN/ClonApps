package com.clonapps.core.queue

import com.clonapps.core.model.Failure

enum class JobKind { INSTALL, UNINSTALL }

/** Fase pekerjaan dalam antrean persisten. */
enum class JobPhase {
    QUEUED,
    CHECKING,
    AWAITING_USER,
    INSTALLING,
    VERIFYING,
    SUCCEEDED,
    FAILED,
    CANCELLED,

    /** Proses ClonApps terputus saat pekerjaan berjalan; harus dicocokkan ulang dulu. */
    INTERRUPTED,
    ;

    val isTerminal: Boolean get() = this == SUCCEEDED || this == CANCELLED
    val isRunning: Boolean get() = this == CHECKING || this == AWAITING_USER || this == INSTALLING || this == VERIFYING

    /** Pekerjaan yang masih "memegang" paket: mencegah pekerjaan ganda. */
    val blocksDuplicate: Boolean get() = this == QUEUED || isRunning || this == INTERRUPTED
}

data class CloneJob(
    val id: String,
    val kind: JobKind,
    val packageName: String,
    val label: String,
    val profileSerial: Long,
    val phase: JobPhase,
    val attempts: Int = 0,
    val maxAttempts: Int = DEFAULT_MAX_ATTEMPTS,
    /** ID permintaan lintas profil yang sedang menunggu hasil. */
    val requestId: String? = null,
    /** ID session PackageInstaller di profil tujuan, bila sudah dibuat. */
    val sessionId: Int? = null,
    val expectedVersionCode: Long? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val failure: Failure? = null,
    val note: String? = null,
) {
    companion object {
        const val DEFAULT_MAX_ATTEMPTS = 3
    }
}

/** Aturan transisi fase. Satu-satunya tempat yang memutuskan transisi sah. */
object JobStateMachine {
    private val allowed: Map<JobPhase, Set<JobPhase>> = mapOf(
        JobPhase.QUEUED to setOf(JobPhase.CHECKING, JobPhase.CANCELLED, JobPhase.INTERRUPTED),
        JobPhase.CHECKING to setOf(
            JobPhase.INSTALLING, JobPhase.AWAITING_USER, JobPhase.VERIFYING,
            JobPhase.FAILED, JobPhase.CANCELLED, JobPhase.INTERRUPTED,
        ),
        JobPhase.AWAITING_USER to setOf(
            JobPhase.INSTALLING, JobPhase.CHECKING, JobPhase.VERIFYING,
            JobPhase.FAILED, JobPhase.CANCELLED, JobPhase.INTERRUPTED,
        ),
        JobPhase.INSTALLING to setOf(
            JobPhase.AWAITING_USER, JobPhase.VERIFYING, JobPhase.FAILED,
            JobPhase.CANCELLED, JobPhase.INTERRUPTED,
        ),
        JobPhase.VERIFYING to setOf(JobPhase.SUCCEEDED, JobPhase.FAILED, JobPhase.INTERRUPTED),
        JobPhase.INTERRUPTED to setOf(JobPhase.QUEUED, JobPhase.SUCCEEDED, JobPhase.FAILED, JobPhase.CANCELLED),
        JobPhase.FAILED to setOf(JobPhase.QUEUED, JobPhase.CANCELLED),
        JobPhase.SUCCEEDED to emptySet(),
        JobPhase.CANCELLED to emptySet(),
    )

    fun canTransition(from: JobPhase, to: JobPhase): Boolean = allowed[from]?.contains(to) == true
}

class IllegalJobTransition(val from: JobPhase, val to: JobPhase, jobId: String) :
    IllegalStateException("Transisi tidak sah $from -> $to untuk job $jobId")
