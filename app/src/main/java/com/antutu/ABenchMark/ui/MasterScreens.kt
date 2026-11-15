package com.antutu.ABenchMark.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.antutu.ABenchMark.RomCompatibility

// ---------------------------------------------------------------- Home ----

@Composable
fun HomeScreen(
    state: MasterState,
    busy: Boolean,
    onActivate: () -> Unit,
    onStop: () -> Unit,
    onApplyGuards: () -> Unit,
    onShizuku: () -> Unit,
    onOpenOverlay: () -> Unit,
    onOpenBackground: () -> Unit,
    onOpenAutostart: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        HeroCard(state = state, onStop = onStop)

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatusTile(
                modifier = Modifier.weight(1f),
                title = "Service",
                ok = state.serviceActive,
                detail = if (state.serviceActive) "running" else "stopped",
            )
            StatusTile(
                modifier = Modifier.weight(1f),
                title = "Overlay",
                ok = state.overlayAttached,
                detail = if (state.overlayAttached) "attached" else "no surface",
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatusTile(
                modifier = Modifier.weight(1f),
                title = "Permissions",
                ok = state.overlayPermission && state.batteryUnrestricted,
                detail = if (state.overlayPermission && state.batteryUnrestricted) {
                    "complete"
                } else {
                    "incomplete"
                },
            )
            StatusTile(
                modifier = Modifier.weight(1f),
                title = "Protections",
                ok = state.guardsApplied,
                detail = state.backend,
            )
        }

        Button(
            onClick = onActivate,
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
        ) {
            Icon(Icons.Default.Star, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(
                text = if (state.serviceActive) "Monster Mode is active" else "Activate Monster Mode",
                fontSize = 16.sp,
            )
        }

        if (busy) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FilledTonalButton(onClick = onApplyGuards, modifier = Modifier.weight(1f)) {
                Text("Apply protections")
            }
            OutlinedButton(onClick = onShizuku, modifier = Modifier.weight(1f)) {
                Text("Shizuku")
            }
        }

        SectionHeader("Permissions")
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PermissionRow(
                    "Display over other apps",
                    state.overlayPermission,
                    onOpenOverlay,
                )
                HorizontalDivider()
                PermissionRow(
                    "Unrestricted battery usage",
                    state.batteryUnrestricted,
                    onOpenBackground,
                )
                HorizontalDivider()
                PermissionRow(
                    "Auto-start / background activity",
                    true,
                    onOpenAutostart,
                    hint = "Vendor screen",
                )
                HorizontalDivider()
                PermissionRow(
                    "Notifications",
                    state.notificationsGranted && state.notificationsEnabled,
                    null,
                )
            }
        }

        SectionHeader("How it works")
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    "This app impersonates the AnTuTu Benchmark package so the " +
                        "OEM performance stack recognises a running benchmark. It " +
                        "keeps a 2 x 2 pixel hardware accelerated overlay alive with " +
                        "a 3 second heartbeat.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    "Detected ROM: ${state.rom.label}\n" +
                        "${state.deviceModel} - ${state.androidVersion}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun HeroCard(state: MasterState, onStop: () -> Unit) {
    val active = state.serviceActive
    val container = if (active) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(containerColor = container),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Monster Mode",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        state.rom.shortLabel,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                StatusPill(active = active)
            }
            Text(
                text = if (active) {
                    "The performance surface is live. Open your game."
                } else {
                    "Activate to hold the benchmark identity open for the OEM."
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            if (active) {
                OutlinedButton(onClick = onStop) {
                    Text("Stop")
                }
            }
        }
    }
}

@Composable
private fun StatusPill(active: Boolean) {
    val color = if (active) Color(0xFF1B873F) else MaterialTheme.colorScheme.outline
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.16f))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = if (active) "ACTIVE" else "IDLE",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = color,
        )
    }
}

@Composable
private fun StatusTile(
    modifier: Modifier,
    title: String,
    ok: Boolean,
    detail: String,
) {
    Card(modifier = modifier) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (ok) Icons.Default.Check else Icons.Default.Close,
                    contentDescription = null,
                    tint = if (ok) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(title, style = MaterialTheme.typography.titleSmall)
            }
            Text(
                detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PermissionRow(
    label: String,
    granted: Boolean,
    onClick: (() -> Unit)?,
    hint: String? = null,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = if (granted) Icons.Default.Check else Icons.Default.Warning,
            contentDescription = null,
            tint = if (granted) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.error
            },
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            if (hint != null) {
                Text(
                    hint,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (onClick != null) {
            TextButton(onClick = onClick) { Text("Open") }
        }
    }
}

// --------------------------------------------------------------- Boost ----

@Composable
fun BoostScreen(
    state: MasterState,
    busy: Boolean,
    report: String,
    onBoost: (String) -> Unit,
    onResetBoost: (String) -> Unit,
    onForegroundApp: () -> String,
    onOpenGameCenter: () -> Unit,
    onOpenBatteryMode: () -> Unit,
) {
    val context = LocalContext.current
    var packageName by remember { mutableStateOf("") }
    var showPicker by remember { mutableStateOf(false) }
    var apps by remember { mutableStateOf(emptyList<InstalledApp>()) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer
            ),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    "How the boost works on ${state.rom.shortLabel}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    boostExplanation(state.rom),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        SectionHeader("Target game")
        OutlinedTextField(
            value = packageName,
            onValueChange = { packageName = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Package name") },
            placeholder = { Text("com.tencent.tmgp.pubgmhd") },
            singleLine = true,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FilledTonalButton(
                onClick = {
                    val pkg = onForegroundApp()
                    if (pkg.isNotEmpty()) packageName = pkg
                },
                modifier = Modifier.weight(1f),
            ) {
                Text("Use app on screen")
            }
            OutlinedButton(
                onClick = {
                    apps = MasterBridge.installedApps(context)
                    showPicker = true
                },
                modifier = Modifier.weight(1f),
            ) {
                Text("Pick installed")
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = { onBoost(packageName.trim()) },
                enabled = !busy,
                modifier = Modifier.weight(1f),
            ) {
                Text("Apply performance mode")
            }
            OutlinedButton(
                onClick = { onResetBoost(packageName.trim()) },
                enabled = !busy,
                modifier = Modifier.weight(1f),
            ) {
                Text("Reset")
            }
        }

        if (busy) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        if (report.isNotBlank()) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Text(
                    report,
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }

        SectionHeader("Vendor boost")
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FilledTonalButton(
                onClick = onOpenGameCenter,
                modifier = Modifier.weight(1f),
            ) {
                Text("Game Center")
            }
            FilledTonalButton(
                onClick = onOpenBatteryMode,
                modifier = Modifier.weight(1f),
            ) {
                Text("Battery mode")
            }
        }
    }

    if (showPicker) {
        AppPickerDialog(
            apps = apps,
            onDismiss = { showPicker = false },
            onPick = {
                packageName = it
                showPicker = false
            },
        )
    }
}

@Composable
private fun AppPickerDialog(
    apps: List<InstalledApp>,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Pick a game") },
        text = {
            if (apps.isEmpty()) {
                Text("No launchable apps found.")
            } else {
                LazyColumn(modifier = Modifier.height(360.dp)) {
                    items(apps) { app ->
                        TextButton(
                            onClick = { onPick(app.packageName) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Text(app.label, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    app.packageName,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
    )
}

private fun boostExplanation(rom: RomFamily): String = when (rom) {
    RomFamily.ORIGIN_OS ->
        "OriginOS grants the performance profile on its own when it sees a " +
            "running benchmark, so the spoof itself is the boost. Nothing else " +
            "is needed once the service is alive."
    RomFamily.OPLUS_FAMILY ->
        "ColorOS has no benchmark trigger. Its boost is a per game mode, so the " +
            "performance game mode is applied to your game instead. Use Game " +
            "Center for Pro Gamer mode and Battery mode for the global high " +
            "performance mode."
    RomFamily.HYPER_OS ->
        "HyperOS boosts through Game Turbo. The performance game mode is applied " +
            "to your game, and the vendor performance profile is available in " +
            "Game Turbo."
    RomFamily.STANDARD_ANDROID ->
        "On stock Android the boost is the platform performance game mode, " +
            "applied to your game directly."
}

// --------------------------------------------------------------- Setup ----

@Composable
fun SetupScreen(
    state: MasterState,
    busy: Boolean,
    report: String,
    onApplyGuards: () -> Unit,
    onShizuku: () -> Unit,
    onOpenOverlay: () -> Unit,
    onOpenAutostart: () -> Unit,
    onOpenBackground: () -> Unit,
    onOpenBatteryMode: () -> Unit,
    onOpenIgnoreBattery: () -> Unit,
    onOpenAppSettings: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            ),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    "${state.rom.label} checklist",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "Every ROM needs a different set of switches. The ones that " +
                        "can be scripted are applied automatically, the rest are " +
                        "one tap away.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        val steps = RomCompatibility.guidance(RomCompatibility.detect())
            .lines()
            .filter { it.isNotBlank() }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                steps.forEach { step ->
                    Row(verticalAlignment = Alignment.Top) {
                        Text(
                            "•",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(step, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }

        SectionHeader("Privileged access")
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Info, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Backend: ${state.backend}",
                        style = MaterialTheme.typography.titleSmall,
                    )
                }
                Text(
                    "Root or a Shizuku session lets the app re-apply the " +
                        "exemptions on its own and restart the service when the " +
                        "ROM stops it.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    FilledTonalButton(
                        onClick = onApplyGuards,
                        enabled = !busy,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Apply protections")
                    }
                    OutlinedButton(
                        onClick = onShizuku,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Authorize Shizuku")
                    }
                }
            }
        }

        SectionHeader("Vendor screens")
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FilledTonalButton(
                    onClick = onOpenAutostart,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Auto-start")
                }
                FilledTonalButton(
                    onClick = onOpenBackground,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Background")
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FilledTonalButton(
                    onClick = onOpenOverlay,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Overlay")
                }
                FilledTonalButton(
                    onClick = onOpenIgnoreBattery,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Battery")
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FilledTonalButton(
                    onClick = onOpenBatteryMode,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Battery mode")
                }
                FilledTonalButton(
                    onClick = onOpenAppSettings,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("App info")
                }
            }
        }

        if (report.isNotBlank()) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Text(
                    report,
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
    }
}

// ---------------------------------------------------------------- Logs ----

@Composable
fun LogsScreen(
    state: MasterState,
    logs: List<String>,
    report: String,
    onRefresh: () -> Unit,
    onClearLogs: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FilledTonalButton(onClick = onRefresh, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.Refresh, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Refresh")
            }
            OutlinedButton(onClick = onClearLogs, modifier = Modifier.weight(1f)) {
                Text("Clear log")
            }
        }

        SectionHeader("State")
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                DiagnosticRow("ROM", state.rom.label)
                DiagnosticRow("Service active", state.serviceActive.toString())
                DiagnosticRow("Overlay attached", state.overlayAttached.toString())
                DiagnosticRow("Overlay permission", state.overlayPermission.toString())
                DiagnosticRow("Battery unrestricted", state.batteryUnrestricted.toString())
                DiagnosticRow("Notifications", state.notificationsGranted.toString())
                DiagnosticRow("Guards applied", state.guardsApplied.toString())
                DiagnosticRow("Backend", state.backend)
                DiagnosticRow("Last state", state.lastState)
                if (state.lastError.isNotBlank()) {
                    DiagnosticRow("Last error", state.lastError)
                }
            }
        }

        SectionHeader("Session log")
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (logs.isEmpty()) {
                    Text(
                        "Nothing logged yet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    logs.takeLast(40).reversed().forEach { line ->
                        Text(
                            line,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DiagnosticRow(label: String, value: String) {
    Row {
        Text(
            label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(value, style = MaterialTheme.typography.bodySmall)
    }
}

// -------------------------------------------------------------- shared ----

@Composable
private fun SectionHeader(title: String) {
    Text(
        title,
        modifier = Modifier.padding(top = 4.dp),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
    )
}
