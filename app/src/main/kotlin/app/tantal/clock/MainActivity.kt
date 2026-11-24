package app.tantal.clock

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.AlarmClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.tantal.theme.TantalTheme

class MainActivity : ComponentActivity() {
    // The intent that opened the app last: another app or the status bar can ask for a tab.
    private var opened by mutableStateOf<Intent?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) opened = intent
        Store.get(this).schedule()
        setContent { TantalTheme { Clock(opened) } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        opened = intent
    }
}

private enum class Tab(val label: Int, val icon: Int, val selectedIcon: Int, val title: Int = label) {
    Alarms(R.string.alarms, R.drawable.ic_alarm, R.drawable.ic_alarm_fill),
    World(R.string.world, R.drawable.ic_public, R.drawable.ic_public, R.string.world_clock),
    Timers(R.string.timer, R.drawable.ic_hourglass, R.drawable.ic_hourglass),
    Stopwatch(R.string.stopwatch, R.drawable.ic_timer, R.drawable.ic_timer_fill),
}

// Screens that open over the tabs.
private enum class Over { Zones, Settings, Alarm, Timer }

@Composable
private fun Clock(opened: Intent?) {
    val context = LocalContext.current
    val store = remember { Store.get(context) }
    val zones = remember { Zones(context.getSharedPreferences("world", Context.MODE_PRIVATE)) }
    var tab by rememberSaveable { mutableStateOf(Tab.Alarms) }
    var over by rememberSaveable { mutableStateOf<Over?>(null) }
    var editing by rememberSaveable { mutableIntStateOf(0) }
    val fade = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    // Without notifications a ringing alarm cannot show its screen or its buttons.
    val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    val askToNotify = { notifications.launch(Manifest.permission.POST_NOTIFICATIONS) }

    LaunchedEffect(opened) {
        val asked = when (opened?.action) {
            AlarmClock.ACTION_SHOW_ALARMS -> Tab.Alarms
            AlarmClock.ACTION_SHOW_TIMERS -> Tab.Timers
            else -> null
        }
        if (asked != null) {
            tab = asked
            over = null
        }
    }
    LaunchedEffect(store.ringing) {
        if (store.ringing != null) context.startActivity(Intent(context, RingActivity::class.java))
    }

    BackHandler(over != null) { over = null }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            AnimatedContent(
                over,
                Modifier.widthIn(max = ScreenWidth).fillMaxSize(),
                transitionSpec = { fadeIn(fade) togetherWith fadeOut(fade) },
            ) { open ->
                when (open) {
                    Over.Zones -> ZonePicker(zones) { over = null }
                    Over.Settings -> SettingsScreen(store) { over = null }
                    Over.Alarm -> AlarmEditor(store, editing, askToNotify) { over = null }
                    Over.Timer -> NewTimer(store, askToNotify) { over = null }
                    null -> Column(
                        Modifier.fillMaxSize()
                            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)),
                    ) {
                        Row(
                            Modifier.fillMaxWidth().statusBarsPadding().heightIn(min = 64.dp)
                                .padding(horizontal = ScreenGutter, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                stringResource(tab.title),
                                Modifier.weight(1f).padding(start = 4.dp, end = 12.dp).semantics { heading() },
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            IconButton({ over = Over.Settings }, Modifier.size(48.dp)) {
                                Icon(painterResource(R.drawable.ic_settings), stringResource(R.string.settings))
                            }
                        }
                        AnimatedContent(
                            tab,
                            Modifier.weight(1f).fillMaxWidth(),
                            transitionSpec = { fadeIn(fade) togetherWith fadeOut(fade) },
                        ) { current ->
                            when (current) {
                                Tab.Alarms -> AlarmsTab(store) {
                                    editing = it
                                    over = Over.Alarm
                                }

                                Tab.World -> World(zones) { over = Over.Zones }
                                Tab.Timers -> TimersTab(store, { over = Over.Timer }, askToNotify)
                                Tab.Stopwatch -> StopwatchScreen()
                            }
                        }
                        NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                            for (item in Tab.entries) {
                                val selected = item == tab
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = { tab = item },
                                    icon = {
                                        Icon(painterResource(if (selected) item.selectedIcon else item.icon), null)
                                    },
                                    label = {
                                        Text(
                                            stringResource(item.label),
                                            fontWeight = if (selected) FontWeight.Bold else null,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                            textAlign = TextAlign.Center,
                                        )
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
