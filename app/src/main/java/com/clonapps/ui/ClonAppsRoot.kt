package com.clonapps.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import com.clonapps.R
import com.clonapps.domain.CloneCoordinator
import com.clonapps.ui.components.CenteredState
import com.clonapps.ui.screens.ActivityScreen
import com.clonapps.ui.screens.DetailScreen
import com.clonapps.ui.screens.DiagnosticsScreen
import com.clonapps.ui.screens.HomeScreen
import com.clonapps.ui.screens.PickScreen
import com.clonapps.ui.screens.SettingsScreen

enum class Dest(val icon: ImageVector, val label: Int) {
    HOME(Icons.Filled.Home, R.string.nav_home),
    PICK(Icons.Filled.Add, R.string.nav_pick),
    ACTIVITY(Icons.AutoMirrored.Filled.List, R.string.nav_activity),
    DIAGNOSTICS(Icons.Filled.Build, R.string.nav_diagnostics),
    SETTINGS(Icons.Filled.Settings, R.string.nav_settings),
}

/** Tindakan yang membutuhkan Activity (pemilih file sistem, berbagi, provisioning). */
interface RootActions {
    val deviceSecure: Boolean
    fun createProfile()
    fun exportSettings()
    fun importSettings()
    fun shareText(text: String, fileName: String)
    fun saveText(text: String, fileName: String)
    fun openSystemSettings()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClonAppsRoot(c: CloneCoordinator, actions: RootActions) {
    val resources = LocalResources.current
    var destName by rememberSaveable { mutableStateOf(Dest.HOME.name) }
    val dest = Dest.valueOf(destName)
    var detailPkg by rememberSaveable { mutableStateOf<String?>(null) }
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        c.messages.collect { m -> snackbar.showSnackbar(resources.getString(m.res, *m.args.toTypedArray())) }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth.value >= 600f
        val twoPane = maxWidth.value >= 840f
        val showDetailFull = !twoPane && dest == Dest.HOME && detailPkg != null

        BackHandler(enabled = showDetailFull || dest != Dest.HOME || (twoPane && detailPkg != null)) {
            when {
                detailPkg != null && dest == Dest.HOME -> detailPkg = null
                else -> destName = Dest.HOME.name
            }
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(if (showDetailFull) stringResource(R.string.detail_title) else stringResource(dest.label)) },
                    navigationIcon = {
                        if (showDetailFull) {
                            IconButton(onClick = { detailPkg = null }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                            }
                        }
                    },
                )
            },
            bottomBar = {
                if (!wide) {
                    NavigationBar {
                        Dest.values().forEach { d ->
                            NavigationBarItem(
                                selected = d == dest,
                                onClick = { destName = d.name; if (d != Dest.HOME) detailPkg = null },
                                icon = { Icon(d.icon, contentDescription = null) },
                                label = { Text(stringResource(d.label)) },
                            )
                        }
                    }
                }
            },
            snackbarHost = { SnackbarHost(snackbar) },
        ) { padding ->
            Row(Modifier.padding(padding).fillMaxSize()) {
                if (wide) {
                    NavigationRail {
                        Dest.values().forEach { d ->
                            NavigationRailItem(
                                selected = d == dest,
                                onClick = { destName = d.name },
                                icon = { Icon(d.icon, contentDescription = null) },
                                label = { Text(stringResource(d.label)) },
                            )
                        }
                    }
                }
                when (dest) {
                    Dest.HOME -> if (twoPane) {
                        HomeScreen(c, detailPkg, onOpenDetail = { detailPkg = it }, onPick = { destName = Dest.PICK.name }, onCreateProfile = actions::createProfile, modifier = Modifier.weight(0.45f))
                        VerticalDivider()
                        val pkg = detailPkg
                        if (pkg != null) {
                            DetailScreen(c, pkg, Modifier.weight(0.55f).fillMaxHeight())
                        } else {
                            androidx.compose.foundation.layout.Box(Modifier.weight(0.55f).fillMaxHeight()) {
                                CenteredState(stringResource(R.string.detail_placeholder))
                            }
                        }
                    } else if (detailPkg != null) {
                        DetailScreen(c, detailPkg!!)
                    } else {
                        HomeScreen(c, null, onOpenDetail = { detailPkg = it }, onPick = { destName = Dest.PICK.name }, onCreateProfile = actions::createProfile)
                    }
                    Dest.PICK -> PickScreen(c, onDone = { destName = Dest.ACTIVITY.name })
                    Dest.ACTIVITY -> ActivityScreen(c)
                    Dest.DIAGNOSTICS -> DiagnosticsScreen(c, onShareText = actions::shareText, onSaveText = actions::saveText)
                    Dest.SETTINGS -> SettingsScreen(
                        c, actions.deviceSecure,
                        onExport = actions::exportSettings, onImport = actions::importSettings, onOpenSystemSettings = actions::openSystemSettings,
                    )
                }
            }
        }
    }
}
