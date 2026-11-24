package com.clonapps.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.clonapps.R
import com.clonapps.data.PairingStore
import com.clonapps.profile.ProfileSetup
import com.clonapps.ui.components.InfoRow
import com.clonapps.ui.components.SectionCard

/**
 * Tampilan ClonApps saat dibuka DI DALAM profil kerja. Pengelolaan clone dilakukan dari profil utama;
 * layar ini hanya menampilkan status dan menyelesaikan penyiapan bila jalur sistem tidak terpanggil.
 */
@Composable
fun ProfileSideScreen() {
    val context = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    val owner = remember(tick) { ProfileSetup.isProfileOwnerHere(context) }
    val paired = remember(tick) { PairingStore(context).hasSecret() }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(stringResource(R.string.profile_side_title), style = MaterialTheme.typography.headlineSmall)
            SectionCard(stringResource(R.string.profile_side_status)) {
                InfoRow(stringResource(R.string.profile_side_owner), stringResource(if (owner) R.string.yes else R.string.no))
                InfoRow(stringResource(R.string.profile_side_paired), stringResource(if (paired) R.string.yes else R.string.no))
            }
            Text(
                stringResource(if (owner) R.string.profile_side_body_owner else R.string.profile_side_body_foreign),
                style = MaterialTheme.typography.bodyMedium,
            )
            if (owner) {
                Button(onClick = { ProfileSetup.finish(context, null, source = "manual"); tick++ }) {
                    Text(stringResource(R.string.action_finish_setup))
                }
            }
        }
    }
}
