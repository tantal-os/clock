package app.tantal.clock

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.Instant
import java.time.ZoneId

private val presets = listOf(1, 5, 10, 30)

@Composable
fun TimersTab(store: Store, onAdd: () -> Unit, onStarted: () -> Unit) {
    if (store.timers.isEmpty()) {
        TimerPad(store, Modifier.fillMaxSize(), onStarted)
        return
    }

    val colors = MaterialTheme.colorScheme
    val locale = LocalConfiguration.current.locales[0]
    val time = rememberTimeFormat(locale)
    // A timer that has been started stays open as a card, paused or not; reset folds it back into a tile.
    // Both lists come from one read of the timers: the grid must never see a timer in both.
    val (open, folded) = store.timers.partition { it.running || it.left < it.total }
    val soonest = open.filter { it.running }.minByOrNull { it.endAt!! }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var removing by remember { mutableStateOf<Timer?>(null) }

    // The clock is read only where it is drawn, so a frame redraws the rings and the digits that changed.
    LaunchedEffect(soonest != null) {
        if (soonest != null) while (true) withFrameMillis { now = System.currentTimeMillis() }
    }

    fun start(timer: Timer) {
        store.put(timer.start(System.currentTimeMillis()))
        onStarted()
    }

    Box(Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            GridCells.Fixed(2),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 88.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (soonest != null) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    val end = time.format(Instant.ofEpochMilli(soonest.endAt!!).atZone(ZoneId.systemDefault()))
                    Headline(
                        stringResource(R.string.ends_at, end),
                        listOf(soonest.label, timerText(soonest.total)).filter { it.isNotEmpty() }.joinToString(" · "),
                    )
                }
            }
            items(open, key = { it.id }, span = { GridItemSpan(maxLineSpan) }) { timer ->
                Row(
                    Modifier
                        .animateItem()
                        .clip(RoundedCornerShape(28.dp))
                        .background(colors.surfaceContainerLow)
                        .combinedClickable(
                            interactionSource = null,
                            indication = null,
                            onLongClickLabel = stringResource(R.string.remove),
                            onLongClick = { removing = timer },
                            onClick = {},
                        )
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Ring(timer, { now }, Modifier.weight(216f))
                    Column(Modifier.weight(116f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Key(
                            Modifier.fillMaxWidth().height(88.dp),
                            colors.primary,
                            colors.onPrimary,
                            { if (timer.running) store.put(timer.pause(System.currentTimeMillis())) else start(timer) },
                            rest = 0.36f,
                        ) {
                            Icon(
                                painterResource(if (timer.running) R.drawable.ic_pause else R.drawable.ic_play_arrow),
                                stringResource(if (timer.running) R.string.pause else R.string.start),
                                Modifier.size(44.dp),
                            )
                        }
                        Key(
                            Modifier.fillMaxWidth().height(56.dp),
                            colors.surfaceContainerHigh,
                            colors.onSurface,
                            { store.put(timer.addMinute(System.currentTimeMillis())) },
                        ) {
                            Text(stringResource(R.string.add_minute), fontWeight = FontWeight.Medium)
                        }
                        Key(
                            Modifier.fillMaxWidth().height(56.dp),
                            colors.surfaceContainerHigh,
                            colors.onSurface,
                            { store.put(timer.reset()) },
                        ) {
                            Icon(
                                painterResource(R.drawable.ic_restart_alt),
                                stringResource(R.string.reset),
                                Modifier.size(28.dp),
                            )
                        }
                    }
                }
            }
            items(folded, key = { it.id }) { timer ->
                Column(
                    Modifier
                        .animateItem()
                        .clip(RoundedCornerShape(28.dp))
                        .background(colors.surfaceContainerLow)
                        .combinedClickable(
                            interactionSource = null,
                            indication = null,
                            onLongClickLabel = stringResource(R.string.remove),
                            onLongClick = { removing = timer },
                            onClick = {},
                        )
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(timerText(timer.left), fontSize = 32.sp, lineHeight = 40.sp, maxLines = 1)
                    Text(
                        timer.label.ifEmpty { stringResource(R.string.timer) },
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(R.string.not_started),
                            Modifier.weight(1f),
                            color = colors.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Key(Modifier.size(40.dp), colors.secondaryContainer, colors.onSecondaryContainer, { start(timer) }) {
                            Icon(painterResource(R.drawable.ic_play_arrow), stringResource(R.string.start))
                        }
                    }
                }
            }
        }
        FloatingActionButton(onAdd, Modifier.align(Alignment.BottomEnd).padding(16.dp)) {
            Icon(painterResource(R.drawable.ic_add), stringResource(R.string.new_timer))
        }
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

// What is left of a timer: the wavy part of the ring shrinks as the time runs out.
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun Ring(timer: Timer, now: () -> Long, modifier: Modifier) {
    val dim = MaterialTheme.colorScheme.onSurfaceVariant
    val text by remember(timer) { derivedStateOf { timerText(timer.remaining(now()), padded = true) } }
    val stroke = with(LocalDensity.current) { Stroke(6.dp.toPx(), cap = StrokeCap.Round) }
    Box(modifier.aspectRatio(1f), contentAlignment = Alignment.Center) {
        CircularWavyProgressIndicator(
            progress = { (timer.remaining(now()) / timer.total.toFloat()).coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxSize(),
            stroke = stroke,
            trackStroke = stroke,
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (timer.label.isNotEmpty()) {
                Text(timer.label, color = dim, maxLines = 1, style = MaterialTheme.typography.bodyMedium)
            }
            Text(
                text,
                fontSize = if (text.length > 5) 36.sp else 52.sp,
                lineHeight = 60.sp,
                maxLines = 1,
                style = LocalTextStyle.current.copy(fontFeatureSettings = "tnum"),
            )
            Text(
                stringResource(R.string.of_total, timerText(timer.total)),
                color = dim,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
fun NewTimer(store: Store, onStarted: () -> Unit, onClose: () -> Unit) {
    Column(Modifier.safeDrawingPadding()) {
        Bar(stringResource(R.string.new_timer), R.drawable.ic_close, R.string.close, onClose)
        TimerPad(store, Modifier.weight(1f)) {
            onStarted()
            onClose()
        }
    }
}

// Digits come in from the right, as on a microwave: 5, 0, 0 is five minutes.
@Composable
private fun TimerPad(store: Store, modifier: Modifier, onStarted: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    var digits by rememberSaveable { mutableStateOf("") }
    val padded = digits.padStart(6, '0')
    val length = (padded.take(2).toLong() * 3600 + padded.substring(2, 4).toLong() * 60 + padded.drop(4).toLong()) * 1000

    fun type(digit: Char) {
        if (digits.length < 6 && (digit != '0' || digits.isNotEmpty())) digits += digit
    }

    Column(modifier) {
        Text(
            buildAnnotatedString {
                val units = listOf(R.string.unit_hours, R.string.unit_minutes, R.string.unit_seconds)
                units.forEachIndexed { index, unit ->
                    // A group lights up once a typed digit has reached it.
                    val typed = digits.length > 4 - 2 * index
                    withStyle(SpanStyle(color = if (typed) colors.onSurface else colors.outline)) {
                        append(padded.substring(2 * index, 2 * index + 2))
                    }
                    withStyle(SpanStyle(color = colors.onSurfaceVariant, fontSize = 24.sp)) {
                        append(" ${stringResource(unit)}")
                        if (index < units.lastIndex) append("  ")
                    }
                }
            },
            Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
            fontSize = 64.sp,
            lineHeight = 72.sp,
            maxLines = 1,
            style = LocalTextStyle.current.copy(fontFeatureSettings = "tnum"),
        )
        Spacer(Modifier.weight(1f))
        Row(
            Modifier.padding(start = 16.dp, end = 16.dp, bottom = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            for (minutes in presets) {
                val preset = "${minutes}00"
                FilterChip(digits == preset, { digits = preset }, { Text(stringResource(R.string.minutes, minutes)) })
            }
        }
        BoxWithConstraints(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
            val gap = 8.dp
            val key = (maxWidth - gap * 3) / 4
            val row = 64.dp

            @Composable
            fun Digit(text: String, wide: Boolean = false) {
                Key(
                    Modifier.size(if (wide) key * 2 + gap else key, row),
                    colors.surfaceContainerHigh,
                    colors.onSurface,
                    { text.forEach(::type) },
                ) { Text(text, fontSize = 28.sp) }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                    for (line in listOf("123", "456", "789")) {
                        Row(horizontalArrangement = Arrangement.spacedBy(gap)) { for (digit in line) Digit(digit.toString()) }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                        Digit("0", wide = true)
                        Digit("00")
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                    Key(
                        Modifier.size(key, row * 2 + gap),
                        Color.Transparent,
                        colors.onSurfaceVariant,
                        { digits = digits.dropLast(1) },
                    ) {
                        Icon(painterResource(R.drawable.ic_backspace), stringResource(R.string.delete), Modifier.size(28.dp))
                    }
                    Key(
                        Modifier.size(key, row * 2 + gap),
                        colors.primary,
                        colors.onPrimary,
                        {
                            store.put(Timer(store.newTimerId(), length).start(System.currentTimeMillis()))
                            onStarted()
                        },
                        enabled = length > 0,
                    ) {
                        Icon(painterResource(R.drawable.ic_play_arrow), stringResource(R.string.start), Modifier.size(40.dp))
                    }
                }
            }
        }
    }
}
