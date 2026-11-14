package app.tantal.clock

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
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
    val separator = remember { DecimalFormatSymbols.getInstance().decimalSeparator }
    // Fixed-width digits, so the running time does not shake.
    val digits = LocalTextStyle.current.copy(fontFeatureSettings = "tnum")

    fun update(change: Stopwatch.(Long) -> Stopwatch) {
        now = SystemClock.elapsedRealtime()
        watch = watch.change(now)
        prefs.edit().putString("state", watch.encode(boot)).apply()
    }

    LaunchedEffect(watch.running) {
        while (watch.running) withFrameMillis { now = SystemClock.elapsedRealtime() }
    }

    fun text(millis: Long) = stopwatchText(millis).let { (main, hundredths) -> "$main$separator$hundredths" }

    Column {
        // The only part that reads the clock, so nothing else recomposes on every frame.
        Readout({ watch.elapsed(now) }, separator)
        Box(Modifier.weight(1f)) {
            if (watch.laps.isNotEmpty()) {
                LazyColumn(
                    Modifier
                        .padding(horizontal = 16.dp)
                        .clip(RoundedCornerShape(28.dp))
                        .background(colors.surfaceContainerLow)
                        .padding(horizontal = 20.dp, vertical = 4.dp),
                ) {
                    items(watch.laps.size, key = { watch.laps.size - it }) { row ->
                        val index = watch.laps.lastIndex - row
                        val total = watch.laps[index]
                        Column(Modifier.animateItem()) {
                            if (row > 0) HorizontalDivider(color = colors.outlineVariant)
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(stringResource(R.string.lap_number, index + 1), color = colors.onSurfaceVariant)
                                Text(text(total - (watch.laps.getOrNull(index - 1) ?: 0)), style = digits)
                                Text(text(total), color = colors.onSurfaceVariant, style = digits)
                            }
                        }
                    }
                }
            }
        }
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Key(
                Modifier.size(72.dp),
                colors.surfaceContainerHigh,
                colors.onSurface,
                onClick = { update { Stopwatch() } },
                enabled = watch != Stopwatch(),
            ) {
                Icon(painterResource(R.drawable.ic_restart_alt), stringResource(R.string.reset), Modifier.size(28.dp))
            }
            Key(
                Modifier.size(72.dp),
                colors.surfaceContainerHigh,
                colors.onSurface,
                onClick = { update { lap(it) } },
                enabled = watch.running,
            ) {
                Icon(painterResource(R.drawable.ic_flag), stringResource(R.string.lap), Modifier.size(28.dp))
            }
            Spacer(Modifier.weight(1f))
            Key(Modifier.size(128.dp, 96.dp), colors.primary, colors.onPrimary, { update { toggle(it) } }, rest = 0.375f) {
                Icon(
                    painterResource(if (watch.running) R.drawable.ic_pause else R.drawable.ic_play_arrow),
                    stringResource(if (watch.running) R.string.pause else R.string.start),
                    Modifier.size(44.dp),
                )
            }
        }
    }
}

@Composable
private fun Readout(elapsed: () -> Long, separator: Char) {
    val dim = MaterialTheme.colorScheme.onSurfaceVariant
    val millis = elapsed()
    val (main, hundredths) = stopwatchText(millis)
    val long = millis >= 3_600_000
    Text(
        buildAnnotatedString {
            append(main)
            withStyle(SpanStyle(color = dim, fontSize = if (long) 32.sp else 44.sp)) { append("$separator$hundredths") }
        },
        Modifier.padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
        fontSize = if (long) 64.sp else 88.sp,
        lineHeight = 96.sp,
        maxLines = 1,
        style = LocalTextStyle.current.copy(fontFeatureSettings = "tnum"),
    )
}
