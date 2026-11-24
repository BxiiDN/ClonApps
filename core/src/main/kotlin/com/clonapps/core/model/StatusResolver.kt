package com.clonapps.core.model

import com.clonapps.core.queue.CloneJob
import com.clonapps.core.queue.JobPhase
import com.clonapps.core.validate.PrecheckResult

/**
 * Menurunkan [CloneStatus] dari data yang dapat diamati. Tidak pernah memberi
 * VERIFIED_RUNNING tanpa konfirmasi pengguna yang tersimpan.
 */
object StatusResolver {
    fun resolve(
        record: CloneRecord?,
        activeJob: CloneJob?,
        presentInProfile: Boolean?,
        precheck: PrecheckResult?,
    ): CloneStatus {
        if (activeJob != null) {
            when (activeJob.phase) {
                JobPhase.AWAITING_USER, JobPhase.INTERRUPTED -> return CloneStatus.NEEDS_USER_ACTION
                JobPhase.QUEUED, JobPhase.CHECKING, JobPhase.INSTALLING, JobPhase.VERIFYING -> return CloneStatus.IN_PROGRESS
                JobPhase.FAILED -> return CloneStatus.FAILED
                JobPhase.SUCCEEDED, JobPhase.CANCELLED -> Unit
            }
        }
        // Keberadaan nyata di profil mengalahkan catatan lokal.
        if (presentInProfile == true) {
            return when (record?.launchState) {
                LaunchState.USER_CONFIRMED_RUNNING -> CloneStatus.VERIFIED_RUNNING
                LaunchState.REQUESTED -> CloneStatus.LAUNCH_REQUESTED
                LaunchState.FAILED -> CloneStatus.FAILED
                else -> CloneStatus.INSTALLED
            }
        }
        if (record?.installState == InstallState.FAILED && record.lastFailure != null) return CloneStatus.FAILED
        if (precheck != null) return precheck.overall
        return CloneStatus.NOT_CHECKED
    }
}
