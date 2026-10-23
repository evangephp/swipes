package com.swipes.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.swipes.R
import com.swipes.capture.CaptureEngine
import com.swipes.data.AppDatabase
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.ZoneId

private enum class Screen { HOME, ANALYTICS, REPLAY, MILESTONES, SETUP, VALIDATE }

private val TABS = listOf(Screen.HOME, Screen.ANALYTICS, Screen.REPLAY, Screen.MILESTONES)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        CaptureEngine.start(this)
        setContent {
            SwipesTheme {
                Surface(Modifier.fillMaxSize()) {
                    App(Modifier)
                }
            }
        }
    }
}

@Composable
private fun App(modifier: Modifier) {
    val context = LocalContext.current
    val engine by CaptureEngine.state.collectAsStateWithLifecycle()
    // Settings can change while we're in the background; re-read them every second.
    var checks by remember { mutableStateOf(SetupChecks.read(context, engine)) }
    LaunchedEffect(engine) {
        while (true) {
            checks = SetupChecks.read(context, engine)
            delay(1_000)
        }
    }
    val setupDone = SetupChecks.complete(checks)
    var screen by rememberSaveable { mutableStateOf(Screen.HOME) }
    BackHandler(enabled = screen != Screen.HOME) { screen = Screen.HOME }

    val today = LocalDate.now()
    val topApps by remember(today) {
        val start = today.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        AppDatabase.get(context).captureDao().observeTopApps(start, 5)
    }.collectAsStateWithLifecycle(emptyList())

    Scaffold(
        modifier,
        bottomBar = {
            NavigationBar {
                TABS.forEach { tab ->
                    NavigationBarItem(
                        selected = screen == tab,
                        onClick = { screen = tab },
                        icon = { TabIcon(tab) },
                        label = { Text(tab.label()) },
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding)) {
            when (screen) {
                Screen.HOME -> HomeScreen(engine, setupDone, topApps, onSetup = { screen = Screen.SETUP }, onValidate = { screen = Screen.VALIDATE })
                Screen.ANALYTICS -> AnalyticsScreen()
                Screen.REPLAY -> ReplayScreen()
                Screen.MILESTONES -> MilestonesScreen(engine)
                Screen.SETUP -> SetupScreen(checks, engine, onValidate = { screen = Screen.VALIDATE })
                Screen.VALIDATE -> ValidationScreen()
            }
        }
    }
}

private fun Screen.label() = when (this) {
    Screen.HOME -> "Home"
    Screen.ANALYTICS -> "Analytics"
    Screen.REPLAY -> "Replay"
    Screen.MILESTONES -> "Milestones"
    else -> ""
}

@Composable
private fun TabIcon(tab: Screen) = when (tab) {
    Screen.HOME -> Icon(Icons.Filled.Home, null)
    Screen.ANALYTICS -> Icon(painterResource(R.drawable.ic_heatmap), null)
    Screen.REPLAY -> Icon(Icons.Filled.PlayArrow, null)
    else -> Icon(Icons.Filled.Star, null)
}
