package com.clonapps

import com.clonapps.core.backend.BackendAvailability
import com.clonapps.core.backend.BackendId
import com.clonapps.core.model.CloneStatus
import com.clonapps.core.model.FailureCause
import com.clonapps.core.model.FailureStage
import com.clonapps.core.model.InstallMethod
import com.clonapps.core.model.InstallState
import com.clonapps.core.model.LaunchState
import com.clonapps.core.model.ProfileOwnership
import com.clonapps.core.model.RecoveryAction
import com.clonapps.core.queue.JobKind
import com.clonapps.core.queue.JobPhase
import com.clonapps.core.store.ThemeMode
import com.clonapps.core.validate.CheckId
import com.clonapps.core.validate.CheckOutcome
import com.clonapps.data.LogCode
import com.clonapps.domain.CloneCoordinator
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Menjamin setiap nilai enum yang ditampilkan punya teks Indonesia dan Inggris (lokalisasi tidak bocor ke nama mentah). */
class StringResourcesTest {
    private fun keys(path: String): Set<String> {
        val f = listOf(File(path), File("app/$path")).first { it.exists() }
        return Regex("<string name=\"([a-z_0-9]+)\"").findAll(f.readText()).map { it.groupValues[1] }.toSet()
    }

    private val id = keys("src/main/res/values/strings.xml")
    private val en = keys("src/main/res/values-en/strings.xml")

    private val enums: Map<String, List<String>> = mapOf(
        "status" to CloneStatus.values().map { it.name },
        "install" to InstallState.values().map { it.name },
        "launch" to LaunchState.values().map { it.name },
        "method" to InstallMethod.values().map { it.name },
        "ownership" to ProfileOwnership.values().map { it.name },
        "stage" to FailureStage.values().map { it.name },
        "unknown" to FailureStage.values().map { it.name },
        "cause" to FailureCause.values().map { it.name },
        "recovery" to RecoveryAction.values().map { it.name },
        "check" to CheckId.values().map { it.name },
        "outcome" to CheckOutcome.values().map { it.name },
        "jobkind" to JobKind.values().map { it.name },
        "phase" to JobPhase.values().map { it.name },
        "phasehint" to JobPhase.values().map { it.name },
        "theme" to ThemeMode.values().map { it.name },
        "backend" to BackendId.values().map { it.name },
        "backendnote" to BackendId.values().map { it.name },
        "availability" to BackendAvailability.values().map { it.name },
        "log" to LogCode.values().map { it.name },
        "problem" to CloneCoordinator.ProblemKind.values().map { it.name },
    )

    @Test
    fun everyEnumValueHasIndonesianAndEnglishText() {
        val missing = mutableListOf<String>()
        enums.forEach { (prefix, names) ->
            names.forEach { n ->
                val key = "${prefix}_${n.lowercase()}"
                if (key !in id) missing += "id:$key"
                if (key !in en) missing += "en:$key"
            }
        }
        assertTrue("Teks hilang: $missing", missing.isEmpty())
    }

    @Test
    fun englishHasEveryTranslatableKey() {
        val diff = (id - "app_name") - en
        assertTrue("Kunci tanpa terjemahan Inggris: $diff", diff.isEmpty())
        assertTrue("Kunci Inggris tanpa padanan Indonesia: ${en - id}", (en - id).isEmpty())
    }
}
