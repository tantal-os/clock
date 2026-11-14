package app.tantal.clock

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

private val snoozeChoices = listOf(5, 10, 15, 20, 30)
private val silenceChoices = listOf(1, 5, 10, 15, 30)

@Composable
fun SettingsScreen(store: Store, onClose: () -> Unit) {
    val context = LocalContext.current
    var snooze by remember { mutableIntStateOf(store.snoozeMinutes) }
    var silence by remember { mutableIntStateOf(store.silenceMinutes) }
    var timerSound by remember { mutableStateOf(store.timerSound) }
    var timerVibrate by remember { mutableStateOf(store.timerVibrate) }
    // The key of the minutes setting whose choices are open.
    var choosing by remember { mutableStateOf<String?>(null) }
    val pickSound = rememberSoundPicker(timerSound) {
        timerSound = it
        store.settings.edit().putString("timer_sound", it).apply()
    }

    Column(Modifier.safeDrawingPadding()) {
        Bar(stringResource(R.string.settings), R.drawable.ic_arrow_back, R.string.back, onClose)
        Section(stringResource(R.string.alarms))
        Item(
            stringResource(R.string.snooze_length),
            stringResource(R.string.minutes, snooze),
            R.drawable.ic_snooze,
            onClick = { choosing = "snooze" },
        )
        Item(
            stringResource(R.string.silence_after),
            stringResource(R.string.minutes, silence),
            R.drawable.ic_alarm_off,
            onClick = { choosing = "silence" },
        )
        Section(stringResource(R.string.timers))
        Item(
            stringResource(R.string.sound),
            remember(timerSound) { soundTitle(context, timerSound) },
            R.drawable.ic_music_note,
            onClick = pickSound,
        )
        SwitchItem(stringResource(R.string.vibrate), icon = R.drawable.ic_vibration, checked = timerVibrate) {
            timerVibrate = it
            store.settings.edit().putBoolean("timer_vibrate", it).apply()
        }
    }

    choosing?.let { key ->
        val current = if (key == "snooze") snooze else silence
        AlertDialog(
            onDismissRequest = { choosing = null },
            confirmButton = { TextButton({ choosing = null }) { Text(stringResource(android.R.string.cancel)) } },
            title = { Text(stringResource(if (key == "snooze") R.string.snooze_length else R.string.silence_after)) },
            text = {
                Column {
                    for (minutes in if (key == "snooze") snoozeChoices else silenceChoices) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .selectable(minutes == current, role = Role.RadioButton) {
                                    if (key == "snooze") snooze = minutes else silence = minutes
                                    store.settings.edit().putInt(key, minutes).apply()
                                    choosing = null
                                }
                                .heightIn(min = 48.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(minutes == current, null)
                            Text(stringResource(R.string.minutes, minutes))
                        }
                    }
                }
            },
        )
    }
}
