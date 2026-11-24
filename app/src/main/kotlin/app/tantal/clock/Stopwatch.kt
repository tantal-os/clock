package app.tantal.clock

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.DecimalFormatSymbols

@Composable
fun StopwatchScreen() {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    val prefs = remember { context.getSharedPreferences("stopwatch", Context.MODE_PRIVATE) }
    val boot = remember { Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, 0) }
    var watch by remember { mutableStateOf(Stopwatch.decode(prefs.getString("state", null), boot)) }
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    val locale = LocalConfiguration.current.locales[0]
    val separator = remember(locale) { DecimalFormatSymbols.getInstance(locale).decimalSeparator }

    fun update(change: Stopwatch.(Long) -> Stopwatch) {
        now = SystemClock.elapsedRealtime()
        watch = watch.change(now)
        prefs.edit().putString("state", watch.encode(boot)).apply()
    }

    LaunchedEffect(watch.running) {
        while (watch.running) withFrameMillis { now = SystemClock.elapsedRealtime() }
    }

    fun text(millis: Long) = stopwatchText(millis).let { (main, hundredths) -> "$main$separator$hundredths" }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = ScreenGutter, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            StopwatchReadout(elapsed = { watch.elapsed(now) }, separator = separator)
        }
        item {
            val action = if (watch.running) R.string.pause else R.string.start
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                FilledTonalIconButton(
                    onClick = { update { Stopwatch() } },
                    modifier = Modifier.size(56.dp),
                    enabled = watch != Stopwatch(),
                ) {
                    Icon(painterResource(R.drawable.ic_restart_alt), stringResource(R.string.reset))
                }
                FilledTonalIconButton(
                    onClick = { update { lap(it) } },
                    modifier = Modifier.size(56.dp),
                    enabled = watch.running,
                ) {
                    Icon(painterResource(R.drawable.ic_flag), stringResource(R.string.lap))
                }
                Button(
                    onClick = { update { toggle(it) } },
                    modifier = Modifier.weight(1f).heightIn(min = 56.dp),
                    shape = EntryShape,
                ) {
                    Icon(painterResource(if (watch.running) R.drawable.ic_pause else R.drawable.ic_play_arrow), null)
                    Text(stringResource(action), Modifier.padding(start = 8.dp))
                }
            }
        }
        if (watch.laps.isNotEmpty()) {
            item { ListHeading(stringResource(R.string.laps)) }
            item {
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                    Text(
                        stringResource(R.string.lap),
                        Modifier.weight(0.9f),
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.onSurfaceVariant,
                    )
                    for (label in listOf(R.string.split_time, R.string.total_time)) {
                        Text(
                            stringResource(label),
                            Modifier.weight(1f).wrapContentWidth(Alignment.End),
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.onSurfaceVariant,
                        )
                    }
                }
            }
            items(watch.laps.size, key = { watch.laps.size - it }) { row ->
                val index = watch.laps.lastIndex - row
                val total = watch.laps[index]
                val split = total - (watch.laps.getOrNull(index - 1) ?: 0)
                Surface(Modifier.animateItem(), shape = EntryShape, color = colors.surfaceContainerLow) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            stringResource(R.string.lap_number, index + 1),
                            Modifier.weight(0.9f),
                            style = MaterialTheme.typography.titleSmall,
                        )
                        ClockDigits(text(split), Modifier.weight(1f).wrapContentWidth(Alignment.End), min = 10.sp, max = 20.sp)
                        ClockDigits(
                            text(total),
                            Modifier.weight(1f).wrapContentWidth(Alignment.End),
                            color = colors.onSurfaceVariant,
                            min = 10.sp,
                            max = 20.sp,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StopwatchReadout(elapsed: () -> Long, separator: Char) {
    ReadoutPanel {
        ReadoutLabel(stringResource(R.string.elapsed_time), R.drawable.ic_timer)
        StopwatchDigits(elapsed, separator)
    }
}

@Composable
private fun StopwatchDigits(elapsed: () -> Long, separator: Char) {
    val (main, hundredths) = stopwatchText(elapsed())
    ClockDigits(main, Modifier.fillMaxWidth(), max = 80.sp, fraction = "$separator$hundredths")
}
