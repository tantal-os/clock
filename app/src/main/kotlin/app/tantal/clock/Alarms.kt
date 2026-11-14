package app.tantal.clock

import android.content.Context
import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Locale

private fun week(locale: Locale): List<DayOfWeek> {
    val first = WeekFields.of(locale).firstDayOfWeek
    return (0L..6L).map { first.plus(it) }
}

// "Every day", "Mon to Fri", "Tue, Thu"; for an alarm that rings once, the day it will ring.
private fun daysText(context: Context, alarm: Alarm, now: ZonedDateTime, locale: Locale): String {
    val week = week(locale)
    val days = week.filter { it in alarm.days }
    fun name(day: DayOfWeek) = day.getDisplayName(TextStyle.SHORT_STANDALONE, locale)
    return when {
        days.isEmpty() -> {
            val next = alarm.next(now).toLocalDate()
            when (next) {
                now.toLocalDate() -> context.getString(R.string.today)
                now.toLocalDate().plusDays(1) -> context.getString(R.string.tomorrow)
                else -> DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "EEEdMMM"), locale).format(next)
            }
        }

        days.size == 7 -> context.getString(R.string.every_day)
        days.size >= 3 && week.indexOf(days.last()) - week.indexOf(days.first()) == days.size - 1 ->
            context.getString(R.string.day_range, name(days.first()), name(days.last()))

        else -> days.joinToString { name(it) }
    }
}

@Composable
fun AlarmsTab(store: Store, onEdit: (Int) -> Unit) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    val locale = LocalConfiguration.current.locales[0]
    val now = rememberNow().atZone(ZoneId.systemDefault())
    val next = store.alarms.mapNotNull { alarm -> alarm.trigger(now)?.let { alarm to it } }.minByOrNull { it.second }

    Box(Modifier.fillMaxSize()) {
        if (store.alarms.isEmpty()) {
            Column(
                Modifier.align(Alignment.Center).padding(horizontal = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(
                    painterResource(R.drawable.ic_alarm),
                    null,
                    Modifier.padding(bottom = 12.dp).size(48.dp),
                    tint = colors.onSurfaceVariant,
                )
                Text(stringResource(R.string.no_alarms), style = MaterialTheme.typography.titleLarge)
                Text(
                    stringResource(R.string.no_alarms_hint),
                    color = colors.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
        LazyVerticalGrid(
            GridCells.Fixed(2),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 88.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (next != null) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    val at = ZonedDateTime.ofInstant(Instant.ofEpochMilli(next.second), now.zone)
                    val day = at.dayOfWeek.getDisplayName(TextStyle.SHORT_STANDALONE, locale)
                    Headline(
                        stringResource(R.string.in_span, spanText(next.second - now.toInstant().toEpochMilli())),
                        stringResource(R.string.next_alarm, "$day ${alarmTime(context, locale, at.hour, at.minute)}"),
                    )
                }
            }
            items(store.alarms.sortedBy { it.hour * 60 + it.minute }, key = { it.id }) { alarm ->
                Column(
                    Modifier
                        .animateItem()
                        .clip(RoundedCornerShape(28.dp))
                        .background(colors.surfaceContainerLow)
                        .clickable { onEdit(alarm.id) }
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        alarmTime(context, locale, alarm.hour, alarm.minute),
                        color = if (alarm.enabled) colors.onSurface else colors.onSurfaceVariant,
                        autoSize = TextAutoSize.StepBased(24.sp, 48.sp, 1.sp),
                        lineHeight = 56.sp,
                        maxLines = 1,
                    )
                    Text(
                        alarm.label.ifEmpty { stringResource(R.string.alarm) },
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            daysText(context, alarm, now, locale),
                            Modifier.weight(1f),
                            color = colors.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Switch(alarm.enabled, { store.put(alarm.copy(enabled = it, snoozedUntil = 0)) })
                    }
                }
            }
        }
        FloatingActionButton({ onEdit(0) }, Modifier.align(Alignment.BottomEnd).padding(16.dp)) {
            Icon(painterResource(R.drawable.ic_add), stringResource(R.string.new_alarm))
        }
    }
}

private val AlarmSaver = Saver<Alarm, String>({ it.encode() }, { Alarm.decode(it) })

// `id` is 0 for a new alarm.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlarmEditor(store: Store, id: Int, onSaved: () -> Unit, onClose: () -> Unit) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    val locale = LocalConfiguration.current.locales[0]
    val original = remember { store.alarms.find { it.id == id } }
    var draft by rememberSaveable(stateSaver = AlarmSaver) {
        mutableStateOf(original ?: Alarm(store.newAlarmId(), hour = 7, minute = 0))
    }
    val time = rememberTimePickerState(draft.hour, draft.minute, DateFormat.is24HourFormat(context))
    var labeling by remember { mutableStateOf(false) }
    val pickSound = rememberSoundPicker(draft.sound) { draft = draft.copy(sound = it) }

    Column(Modifier.safeDrawingPadding()) {
        Bar(
            stringResource(if (original == null) R.string.new_alarm else R.string.edit_alarm),
            R.drawable.ic_close,
            R.string.close,
            onClose,
        )
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            TimeInput(time, Modifier.padding(start = 16.dp, top = 12.dp, end = 16.dp))
            Row(
                Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                for (day in week(locale)) {
                    val on = day in draft.days
                    Box(
                        Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .then(
                                if (on) Modifier.background(colors.primary)
                                else Modifier.border(1.dp, colors.outlineVariant, CircleShape),
                            )
                            .toggleable(on, role = Role.Checkbox) {
                                draft = draft.copy(days = if (it) draft.days + day else draft.days - day)
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            day.getDisplayName(TextStyle.NARROW_STANDALONE, locale),
                            color = if (on) colors.onPrimary else colors.onSurfaceVariant,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }
            Item(stringResource(R.string.label), draft.label, R.drawable.ic_label, onClick = { labeling = true })
            Item(
                stringResource(R.string.sound),
                remember(draft.sound) { soundTitle(context, draft.sound) },
                R.drawable.ic_music_note,
                onClick = pickSound,
            )
            SwitchItem(stringResource(R.string.vibrate), icon = R.drawable.ic_vibration, checked = draft.vibrate) {
                draft = draft.copy(vibrate = it)
            }
            SwitchItem(
                stringResource(R.string.rising_volume),
                stringResource(R.string.rising_volume_summary),
                R.drawable.ic_volume_up,
                draft.rising,
            ) { draft = draft.copy(rising = it) }
        }
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (original != null) {
                TextButton(
                    {
                        store.remove(original)
                        onClose()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = colors.error),
                ) {
                    Icon(painterResource(R.drawable.ic_delete), null, Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.delete))
                }
            }
            Spacer(Modifier.weight(1f))
            Button(
                {
                    store.put(draft.copy(hour = time.hour, minute = time.minute, enabled = true, snoozedUntil = 0))
                    onSaved()
                    onClose()
                },
                Modifier.size(168.dp, 56.dp),
            ) {
                Icon(painterResource(R.drawable.ic_check), null, Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.save))
            }
        }
    }

    if (labeling) LabelDialog(draft.label, { labeling = false }) { draft = draft.copy(label = it) }
}
