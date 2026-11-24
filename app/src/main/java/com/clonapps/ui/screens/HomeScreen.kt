package com.clonapps.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.clonapps.R
import com.clonapps.core.backend.Capacity
import com.clonapps.core.model.InstallState
import com.clonapps.core.model.ProfileOwnership
import com.clonapps.domain.CloneCoordinator
import com.clonapps.platform.ProfileState
import com.clonapps.ui.components.AppIcon
import com.clonapps.ui.components.AppTitle
import com.clonapps.ui.components.CenteredState
import com.clonapps.ui.components.FailureDetails
import com.clonapps.ui.components.SectionCard
import com.clonapps.ui.components.StatusChip
import com.clonapps.ui.components.enumText

@Composable
fun HomeScreen(
    c: CloneCoordinator,
    selectedPkg: String?,
    onOpenDetail: (String) -> Unit,
    onPick: () -> Unit,
    onCreateProfile: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val profile by c.profile.collectAsState()
    val records by c.records.collectAsState()
    val presence by c.presence.collectAsState()
    val queue by c.queue.collectAsState()
    val busy by c.busy.collectAsState()
    val provFailure by c.provisioningFailure.collectAsState()

    val clones = records.values
        .filter { it.installState == InstallState.INSTALLED || it.packageName in presence || queue.activeFor(it.packageName, it.profileSerial) != null || it.installState == InstallState.FAILED }
        .sortedWith(compareByDescending<com.clonapps.core.model.CloneRecord> { it.favorite }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.displayLabel })

    LazyColumn(modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { if (busy) LinearProgressIndicator(Modifier.fillMaxWidth()) }
        item { ProfileCard(c, profile, onCreateProfile) }
        provFailure?.let { f ->
            item {
                SectionCard(stringResource(R.string.provisioning_failed_title)) { FailureDetails(f) }
            }
        }
        item {
            val caps = Capacity.workProfile(profile.usable, canPauseDirectly = false)
            SectionCard(stringResource(R.string.capacity_title)) {
                Text(stringResource(R.string.capacity_body, caps.maxProfiles, caps.maxInstancesPerAppPerProfile), style = MaterialTheme.typography.bodyMedium)
            }
        }
        val running = queue.jobs.count { it.phase.blocksDuplicate }
        if (running > 0) {
            item {
                SectionCard(stringResource(R.string.queue_active_title)) {
                    Text(stringResource(R.string.queue_active_body, running))
                    OutlinedButton(onClick = { c.resumeQueue() }) { Text(stringResource(R.string.action_resume_queue)) }
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.home_clones_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                Button(onClick = onPick, enabled = profile.ownership == ProfileOwnership.OURS_PAIRED) { Text(stringResource(R.string.action_pick_apps)) }
            }
        }
        if (clones.isEmpty()) {
            item {
                CenteredState(
                    title = stringResource(R.string.home_empty_title),
                    body = stringResource(if (profile.ownership == ProfileOwnership.OURS_PAIRED) R.string.home_empty_body else R.string.home_empty_need_profile),
                )
            }
        }
        items(clones, key = { it.packageName }) { r ->
            val selected = r.packageName == selectedPkg
            Card(
                Modifier.fillMaxWidth().clickable(role = Role.Button, onClickLabel = stringResource(R.string.action_open_detail)) { onOpenDetail(r.packageName) },
                colors = CardDefaults.cardColors(
                    containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                ),
            ) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    AppIcon(r.packageName, profile.profile?.handle, launcher = c.launcher)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        AppTitle(r.displayLabel, r.packageName)
                        StatusChip(c.statusOf(r.packageName))
                    }
                    if (r.favorite) Icon(Icons.Filled.Star, contentDescription = stringResource(R.string.favorite), tint = MaterialTheme.colorScheme.tertiary)
                    IconButton(
                        onClick = { c.launch(r.packageName) },
                        enabled = r.packageName in presence && profile.usable,
                    ) { Icon(Icons.Filled.PlayArrow, contentDescription = stringResource(R.string.action_open_clone_named, r.displayLabel)) }
                }
            }
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun ProfileCard(c: CloneCoordinator, p: ProfileState, onCreateProfile: () -> Unit) {
    SectionCard(stringResource(R.string.profile_card_title)) {
        Text(enumText("ownership", p.ownership), style = MaterialTheme.typography.bodyLarge)
        p.profile?.let { info ->
            Text(
                stringResource(
                    R.string.profile_card_detail, info.label,
                    stringResource(if (info.quietMode) R.string.profile_paused else R.string.profile_active),
                    stringResource(if (info.unlocked == false) R.string.profile_locked else R.string.profile_unlocked),
                ),
                style = MaterialTheme.typography.bodySmall,
            )
        }
        if (p.others.isNotEmpty()) {
            Text(stringResource(R.string.profile_others, p.others.joinToString { it.label }), style = MaterialTheme.typography.bodySmall)
        }
        if (p.unverified) Text(stringResource(R.string.profile_unverified), style = MaterialTheme.typography.bodySmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            when (p.ownership) {
                ProfileOwnership.NONE -> Button(onClick = onCreateProfile) { Text(stringResource(R.string.action_create_profile)) }
                ProfileOwnership.OURS_UNPAIRED -> {
                    OutlinedButton(onClick = { c.verifyProfile() }) { Text(stringResource(R.string.action_verify_profile)) }
                    Button(onClick = { c.repairPairing() }) { Text(stringResource(R.string.action_repair_pairing)) }
                    OutlinedButton(onClick = { c.openClonAppsInProfile() }) { Text(stringResource(R.string.action_open_clonapps_in_profile)) }
                }
                ProfileOwnership.OURS_PAIRED -> {
                    val paused = p.profile?.quietMode == true
                    OutlinedButton(onClick = { c.pauseProfile(!paused) }) {
                        Text(stringResource(if (paused) R.string.action_resume_profile else R.string.action_pause_profile))
                    }
                    OutlinedButton(onClick = { c.fetchProfileInfo() }) { Text(stringResource(R.string.action_check_profile)) }
                }
                ProfileOwnership.FOREIGN -> Text(stringResource(R.string.profile_foreign_explain), style = MaterialTheme.typography.bodySmall)
            }
            OutlinedButton(onClick = { c.refreshProfile() }) { Text(stringResource(R.string.action_refresh)) }
        }
    }
}
