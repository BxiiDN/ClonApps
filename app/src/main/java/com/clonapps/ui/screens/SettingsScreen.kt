package com.clonapps.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.clonapps.R
import com.clonapps.core.model.ProfileOwnership
import com.clonapps.core.store.ThemeMode
import com.clonapps.domain.CloneCoordinator
import com.clonapps.ui.components.ConfirmDialog
import com.clonapps.ui.components.SectionCard
import com.clonapps.ui.components.enumText

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    c: CloneCoordinator,
    deviceSecure: Boolean,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onOpenSystemSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val settings by c.settings.collectAsState()
    val profile by c.profile.collectAsState()
    var confirmWipe by rememberSaveable { mutableStateOf(false) }
    val paired = profile.ownership == ProfileOwnership.OURS_PAIRED

    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionCard(stringResource(R.string.settings_theme)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ThemeMode.values().forEach { m ->
                    FilterChip(selected = settings.themeMode == m, onClick = { c.updateSettings { it.copy(themeMode = m) } }, label = { Text(enumText("theme", m)) })
                }
            }
        }
        SectionCard(stringResource(R.string.settings_apps)) {
            SwitchRow(stringResource(R.string.settings_show_system), settings.showSystemApps) { v -> c.updateSettings { it.copy(showSystemApps = v) } }
        }
        SectionCard(stringResource(R.string.settings_lock)) {
            SwitchRow(stringResource(R.string.settings_dashboard_lock), settings.dashboardLock, enabled = deviceSecure || settings.dashboardLock) { v ->
                c.updateSettings { it.copy(dashboardLock = v) }
            }
            if (!deviceSecure) Text(stringResource(R.string.settings_lock_needs_secure), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            Text(stringResource(R.string.settings_lock_scope), style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = { c.requestProfileLock() }, enabled = paired) { Text(stringResource(R.string.action_set_profile_lock)) }
            Text(stringResource(R.string.settings_profile_lock_note), style = MaterialTheme.typography.bodySmall)
        }
        SectionCard(stringResource(R.string.settings_profile)) {
            val paused = profile.profile?.quietMode == true
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { c.pauseProfile(!paused) }, enabled = profile.profile != null) {
                    Text(stringResource(if (paused) R.string.action_resume_profile else R.string.action_pause_profile))
                }
                OutlinedButton(onClick = onOpenSystemSettings) { Text(stringResource(R.string.action_open_system_settings)) }
                OutlinedButton(onClick = { c.repairPairing() }, enabled = profile.profile != null) { Text(stringResource(R.string.action_repair_pairing)) }
            }
            Text(stringResource(R.string.settings_pause_note), style = MaterialTheme.typography.bodySmall)
        }
        SectionCard(stringResource(R.string.settings_backup)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onExport) { Text(stringResource(R.string.action_export)) }
                OutlinedButton(onClick = onImport) { Text(stringResource(R.string.action_import)) }
            }
            Text(stringResource(R.string.settings_backup_note), style = MaterialTheme.typography.bodySmall)
        }
        SectionCard(stringResource(R.string.settings_privacy)) {
            Text(stringResource(R.string.settings_privacy_body), style = MaterialTheme.typography.bodySmall)
        }
        SectionCard(stringResource(R.string.settings_danger)) {
            Text(stringResource(R.string.settings_wipe_note), style = MaterialTheme.typography.bodySmall)
            Button(
                onClick = { confirmWipe = true },
                enabled = paired,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError),
            ) { Text(stringResource(R.string.action_remove_profile)) }
        }
    }

    if (confirmWipe) {
        ConfirmDialog(
            title = stringResource(R.string.wipe_title),
            body = stringResource(R.string.wipe_body),
            confirmLabel = stringResource(R.string.action_remove_profile),
            requireTyping = stringResource(R.string.wipe_keyword),
            onConfirm = { confirmWipe = false; c.removeWholeProfile() },
            onDismiss = { confirmWipe = false },
        )
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onChange).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}
