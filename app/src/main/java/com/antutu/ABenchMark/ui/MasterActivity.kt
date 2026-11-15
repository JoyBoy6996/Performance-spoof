package com.antutu.ABenchMark.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import com.antutu.ABenchMark.PerformanceService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** The four sections of the dashboard. */
enum class MasterTab(val label: String) {
    HOME("Home"),
    BOOST("Boost"),
    SETUP("Setup"),
    LOGS("Logs")
}

class MasterActivity : ComponentActivity() {

    private val scope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())

    private var state by mutableStateOf(
        MasterState(rom = RomFamily.STANDARD_ANDROID)
    )
    private var tab by mutableStateOf(MasterTab.HOME)
    private var busy by mutableStateOf(false)
    private var report by mutableStateOf("")
    private var logs by mutableStateOf(emptyList<String>())
    private val snackbarHostState = SnackbarHostState()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        refresh()
        scope.launch {
            while (isActive) {
                delay(1500L)
                refresh()
            }
        }

        setContent {
            MasterTheme {
                MasterScaffold(
                    state = state,
                    tab = tab,
                    busy = busy,
                    report = report,
                    logs = logs,
                    snackbarHostState = snackbarHostState,
                    onTabChange = { tab = it },
                    onActivate = { activate() },
                    onStop = {
                        MasterBridge.stop(this@MasterActivity)
                        refresh()
                        toast("Monster Mode stopped")
                    },
                    onApplyGuards = { applyGuards() },
                    onShizuku = {
                        MasterBridge.requestShizuku(this@MasterActivity)
                        toast("If Shizuku is running, accept the request")
                    },
                    onBoost = { pkg -> boost(pkg) },
                    onResetBoost = { pkg -> resetBoost(pkg) },
                    onForegroundApp = {
                        val pkg = MasterBridge.foregroundPackage(this@MasterActivity)
                        if (pkg.isEmpty()) {
                            toast("Could not resolve the foreground app")
                        }
                        pkg
                    },
                    onOpenOverlay = { open(MasterBridge::openOverlaySettings) },
                    onOpenAutostart = { open(MasterBridge::openAutostartSettings) },
                    onOpenBackground = { open(MasterBridge::openBackgroundSettings) },
                    onOpenBatteryMode = { open(MasterBridge::openBatteryMode) },
                    onOpenGameCenter = { open(MasterBridge::openGameCenter) },
                    onOpenAppSettings = { open(MasterBridge::openAppSettings) },
                    onOpenIgnoreBattery = {
                        open { MasterBridge.openIgnoreBatteryOptimizations(it) }
                    },
                    onRefresh = {
                        refresh()
                        toast("Refreshed")
                    },
                    onClearLogs = {
                        MasterBridge.clearLogs()
                        logs = emptyList()
                    },
                )
            }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    // ---- actions --------------------------------------------------------

    private fun refresh() {
        state = MasterBridge.snapshot(this)
        logs = MasterBridge.logs()
    }

    private fun activate() {
        val overlayOk = state.overlayPermission
        val batteryOk = state.batteryUnrestricted
        if (!overlayOk || !batteryOk) {
            // Hand the missing permission to the user, then start on the next
            // tap once it has been granted.
            if (!overlayOk) {
                MasterBridge.openOverlaySettings(this)
                toast("Grant 'Display over other apps'")
            } else {
                MasterBridge.openIgnoreBatteryOptimizations(this)
                toast("Set battery usage to 'No restrictions'")
            }
            return
        }
        val started = MasterBridge.activate(this)
        refresh()
        toast(if (started) "Monster Mode active" else "Could not start the service")
    }

    private fun applyGuards() {
        if (busy) return
        busy = true
        MasterBridge.applyGuards(this) { summary ->
            busy = false
            report = summary
            refresh()
            scope.launch { snackbarHostState.showSnackbar("Protections applied") }
        }
    }

    private fun boost(packageName: String) {
        if (packageName.isBlank()) {
            toast("Enter a package name first")
            return
        }
        busy = true
        MasterBridge.boost(this, packageName) { result ->
            busy = false
            report = result
            refresh()
        }
    }

    private fun resetBoost(packageName: String) {
        if (packageName.isBlank()) {
            toast("Enter a package name first")
            return
        }
        busy = true
        MasterBridge.resetBoost(this, packageName) { result ->
            busy = false
            report = result
            refresh()
        }
    }

    private fun open(action: (android.content.Context) -> Boolean) {
        if (!action(this)) {
            toast("That screen is not available on this ROM")
        }
    }

    private fun toast(message: String) {
        scope.launch { snackbarHostState.showSnackbar(message) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MasterScaffold(
    state: MasterState,
    tab: MasterTab,
    busy: Boolean,
    report: String,
    logs: List<String>,
    snackbarHostState: SnackbarHostState,
    onTabChange: (MasterTab) -> Unit,
    onActivate: () -> Unit,
    onStop: () -> Unit,
    onApplyGuards: () -> Unit,
    onShizuku: () -> Unit,
    onBoost: (String) -> Unit,
    onResetBoost: (String) -> Unit,
    onForegroundApp: () -> String,
    onOpenOverlay: () -> Unit,
    onOpenAutostart: () -> Unit,
    onOpenBackground: () -> Unit,
    onOpenBatteryMode: () -> Unit,
    onOpenGameCenter: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onOpenIgnoreBattery: () -> Unit,
    onRefresh: () -> Unit,
    onClearLogs: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            onActivate()
        } else {
            scope.launch {
                snackbarHostState.showSnackbar(
                    "Notifications are required for the foreground service"
                )
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = when (tab) {
                            MasterTab.HOME -> "Performance Spoof"
                            MasterTab.BOOST -> "Game boost"
                            MasterTab.SETUP -> "Setup"
                            MasterTab.LOGS -> "Diagnostics"
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(),
            )
        },
        bottomBar = {
            NavigationBar {
                MasterTab.entries.forEach { entry ->
                    NavigationBarItem(
                        selected = tab == entry,
                        onClick = { onTabChange(entry) },
                        icon = {
                            Icon(
                                imageVector = when (entry) {
                                    MasterTab.HOME -> Icons.Default.Home
                                    MasterTab.BOOST -> Icons.Default.Star
                                    MasterTab.SETUP -> Icons.Default.Settings
                                    MasterTab.LOGS -> Icons.Default.Info
                                },
                                contentDescription = entry.label,
                            )
                        },
                        label = { Text(entry.label) },
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            AnimatedContent(
                targetState = tab,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "tab",
            ) { current ->
                when (current) {
                    MasterTab.HOME -> HomeScreen(
                        state = state,
                        busy = busy,
                        onActivate = {
                            val needsNotification =
                                Build.VERSION.SDK_INT >= 33 &&
                                    context.checkSelfPermission(
                                        Manifest.permission.POST_NOTIFICATIONS
                                    ) != PackageManager.PERMISSION_GRANTED
                            if (needsNotification) {
                                notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                onActivate()
                            }
                        },
                        onStop = onStop,
                        onApplyGuards = onApplyGuards,
                        onShizuku = onShizuku,
                        onOpenOverlay = onOpenOverlay,
                        onOpenBackground = onOpenBackground,
                        onOpenAutostart = onOpenAutostart,
                    )

                    MasterTab.BOOST -> BoostScreen(
                        state = state,
                        busy = busy,
                        report = report,
                        onBoost = onBoost,
                        onResetBoost = onResetBoost,
                        onForegroundApp = onForegroundApp,
                        onOpenGameCenter = onOpenGameCenter,
                        onOpenBatteryMode = onOpenBatteryMode,
                    )

                    MasterTab.SETUP -> SetupScreen(
                        state = state,
                        busy = busy,
                        report = report,
                        onApplyGuards = onApplyGuards,
                        onShizuku = onShizuku,
                        onOpenOverlay = onOpenOverlay,
                        onOpenAutostart = onOpenAutostart,
                        onOpenBackground = onOpenBackground,
                        onOpenBatteryMode = onOpenBatteryMode,
                        onOpenIgnoreBattery = onOpenIgnoreBattery,
                        onOpenAppSettings = onOpenAppSettings,
                    )

                    MasterTab.LOGS -> LogsScreen(
                        state = state,
                        logs = logs,
                        report = report,
                        onRefresh = onRefresh,
                        onClearLogs = onClearLogs,
                    )
                }
            }
        }
    }
}
