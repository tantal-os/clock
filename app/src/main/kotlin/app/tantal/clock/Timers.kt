package app.tantal.clock

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.WavyProgressIndicatorDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.tantal.theme.ClockNumerals
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.delay

private val presets = listOf(1, 5, 10, 30)

@Composable
fun TimersTab(store: Store, onAdd: () -> Unit, onStarted: () -> Unit) {
    if (store.timers.isEmpty()) {
        TimerPad(store, Modifier.fillMaxSize(), onStarted)
        return
    }

    val colors = MaterialTheme.colorScheme
    val (active, idle) = store.timers.partition { it.running || it.left != it.total }
    val timers = active.sortedBy { it.endAt ?: Long.MAX_VALUE } + idle
    val running = active.any { it.running }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var removing by remember { mutableStateOf<Timer?>(null) }

    LaunchedEffect(running) {
        if (running) while (true) withFrameMillis { now = System.currentTimeMillis() }
    }

    fun start(timer: Timer) {
        now = System.currentTimeMillis()
        store.put(timer.start(now))
        onStarted()
    }

    Column(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = ScreenGutter, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(timers, key = { it.id }) { timer ->
                val remove = { removing = timer }
                if (timer.running || timer.left != timer.total) {
                    ReadoutPanel(Modifier.animateItem(), emphasized = timer.running) {
                        TimerTitle(timer, remove)
                        TimerProgress(timer, { now }, Modifier.align(Alignment.CenterHorizontally))
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            val action = if (timer.running) R.string.pause else R.string.start
                            Button(
                                {
                                    if (timer.running) store.put(timer.pause(System.currentTimeMillis()))
                                    else start(timer)
                                },
                                Modifier.weight(1f).heightIn(min = 48.dp),
                                shape = EntryShape,
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                            ) {
                                Text(stringResource(action))
                            }
                            FilledTonalButton(
                                { store.put(timer.addMinute(System.currentTimeMillis())) },
                                Modifier.heightIn(min = 48.dp),
                                shape = EntryShape,
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                            ) {
                                Text(stringResource(R.string.add_minute))
                            }
                            IconButton({ store.put(timer.reset()) }, Modifier.size(48.dp)) {
                                Icon(painterResource(R.drawable.ic_restart_alt), stringResource(R.string.reset))
                            }
                        }
                    }
                } else {
                    Surface(Modifier.animateItem(), shape = EntryShape, color = colors.surfaceContainerLow) {
                        Row(
                            Modifier.fillMaxWidth().padding(start = 16.dp, top = 8.dp, end = 8.dp, bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f).padding(end = 8.dp)) {
                                ClockDigits(timerText(timer.left), max = 34.sp)
                                Text(
                                    timer.label.ifEmpty { stringResource(R.string.timer) },
                                    style = MaterialTheme.typography.titleSmall,
                                )
                            }
                            FilledTonalIconButton({ start(timer) }) {
                                Icon(painterResource(R.drawable.ic_play_arrow), stringResource(R.string.start))
                            }
                            IconButton(remove) {
                                Icon(painterResource(R.drawable.ic_delete), stringResource(R.string.remove))
                            }
                        }
                    }
                }
            }
        }
        AddAction(R.string.new_timer, onAdd)
    }

    removing?.let { timer ->
        AlertDialog(
            onDismissRequest = { removing = null },
            confirmButton = {
                TextButton({
                    store.remove(timer)
                    removing = null
                }) { Text(stringResource(R.string.remove)) }
            },
            dismissButton = { TextButton({ removing = null }) { Text(stringResource(android.R.string.cancel)) } },
            title = {
                Text(stringResource(R.string.remove_question, timer.label.ifEmpty { stringResource(R.string.timer) }))
            },
        )
    }
}

@Composable
private fun TimerTitle(timer: Timer, onRemove: () -> Unit) {
    val time = rememberTimeFormat()
    val end = timer.endAt?.let { time.format(Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault())) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            ReadoutLabel(timer.label.ifEmpty { stringResource(R.string.timer) }, R.drawable.ic_hourglass)
            val status = if (end == null) stringResource(R.string.timer_paused) else stringResource(R.string.ends_at, end)
            val total = stringResource(R.string.total_duration, timerText(timer.total))
            val detail = buildAnnotatedString {
                append(status)
                if (end != null && (end.endsWith(" AM") || end.endsWith(" PM"))) {
                    val start = status.indexOf(end)
                    if (start >= 0) {
                        addStyle(
                            SpanStyle(fontFamily = ClockNumerals, fontWeight = FontWeight.Medium),
                            start,
                            start + end.length,
                        )
                    }
                }
                append(" · ")
                append(total)
            }
            Text(detail, style = MaterialTheme.typography.bodyMedium)
        }
        IconButton(onRemove) {
            Icon(painterResource(R.drawable.ic_delete), stringResource(R.string.remove))
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun TimerProgress(timer: Timer, now: () -> Long, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    val ink = LocalContentColor.current
    val defaultAmplitude = WavyProgressIndicatorDefaults.indicatorAmplitude
    val waveMoving by produceState(timer.running, timer.running) {
        if (!timer.running && value) {
            // Material ignores new targets during its 500 ms morph. Recheck when wave motion stops.
            val scale = coroutineContext[MotionDurationScale]?.scaleFactor ?: 1f
            delay((500 * scale).toLong() + 50)
        }
        value = timer.running
    }
    Box(
        modifier.widthIn(max = 224.dp).fillMaxWidth().aspectRatio(1f),
        contentAlignment = Alignment.Center,
    ) {
        CircularWavyProgressIndicator(
            progress = { (timer.remaining(now()) / timer.total.toFloat()).coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxSize(),
            color = if (timer.running) ink else colors.primary,
            trackColor = ink.copy(alpha = 0.25f),
            amplitude = { progress -> if (waveMoving && timer.running) defaultAmplitude(progress) else 0f },
            waveSpeed = if (waveMoving) WavyProgressIndicatorDefaults.CircularWavelength else 0.dp,
        )
        TimerDigits(timer, now)
    }
}

@Composable
private fun TimerDigits(timer: Timer, now: () -> Long) {
    val text by remember(timer) { derivedStateOf { timerText(timer.remaining(now()), padded = true) } }
    ClockDigits(
        text,
        Modifier.fillMaxWidth(0.72f).wrapContentWidth(Alignment.CenterHorizontally),
        max = 54.sp,
    )
}

@Composable
fun NewTimer(store: Store, onStarted: () -> Unit, onClose: () -> Unit) {
    Column(Modifier.safeDrawingPadding().fillMaxSize()) {
        Bar(stringResource(R.string.new_timer), R.drawable.ic_close, R.string.close, onClose)
        TimerPad(store, Modifier.weight(1f)) {
            onStarted()
            onClose()
        }
    }
}

@Composable
private fun TimerPad(store: Store, modifier: Modifier, onStarted: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    var digits by rememberSaveable { mutableStateOf("") }
    val padded = digits.padStart(6, '0')
    val hours = padded.take(2).toLong()
    val minutes = padded.substring(2, 4).toLong()
    val seconds = padded.drop(4).toLong()
    val length = (hours * 3600 + minutes * 60 + seconds) * 1000

    fun type(key: String) {
        digits = (digits + key).trimStart('0').take(6)
    }

    BoxWithConstraints(modifier.fillMaxWidth()) {
        val height = maxHeight
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).heightIn(min = height)
                .padding(horizontal = ScreenGutter, vertical = 8.dp),
        ) {
            ReadoutPanel {
                ReadoutLabel(stringResource(R.string.duration), R.drawable.ic_hourglass)
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val units = listOf(R.string.unit_hours, R.string.unit_minutes, R.string.unit_seconds)
                    units.forEachIndexed { index, unit ->
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            ClockDigits(
                                padded.substring(2 * index, 2 * index + 2),
                                Modifier.fillMaxWidth().wrapContentWidth(Alignment.CenterHorizontally),
                                max = 58.sp,
                            )
                            Text(stringResource(unit), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            FlowRow(
                Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                for (minutes in presets) {
                    val preset = "${minutes}00"
                    FilterChip(digits == preset, { digits = preset }, { Text(stringResource(R.string.minutes, minutes)) })
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                for (row in listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("0", "00", ""))) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        for (key in row) {
                            val delete = key.isEmpty()
                            Key(
                                Modifier.weight(1f).height(56.dp),
                                if (delete) colors.surfaceContainerHighest else colors.surfaceContainerHigh,
                                colors.onSurface,
                                { if (delete) digits = digits.dropLast(1) else type(key) },
                                enabled = !delete || digits.isNotEmpty(),
                                rest = 0.32f,
                            ) {
                                if (delete) {
                                    Icon(painterResource(R.drawable.ic_backspace), stringResource(R.string.delete_digit))
                                } else {
                                    ClockDigits(key, max = 30.sp)
                                }
                            }
                        }
                    }
                }
            }
            Button(
                {
                    store.put(Timer(store.newTimerId(), length).start(System.currentTimeMillis()))
                    onStarted()
                },
                Modifier.fillMaxWidth().padding(top = 10.dp).heightIn(min = 54.dp),
                enabled = length > 0,
                shape = EntryShape,
            ) {
                Icon(painterResource(R.drawable.ic_play_arrow), null)
                Text(stringResource(R.string.start), Modifier.padding(start = 8.dp), overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
