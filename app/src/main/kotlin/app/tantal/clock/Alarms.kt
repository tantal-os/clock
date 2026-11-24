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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.tantal.theme.ClockNumerals
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
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
    val format = rememberTimeFormat()
    val colors = MaterialTheme.colorScheme
    val locale = LocalConfiguration.current.locales[0]
    val now = rememberNow().atZone(ZoneId.systemDefault())
    val next = store.alarms.mapNotNull { alarm -> alarm.trigger(now)?.let { alarm to it } }.minByOrNull { it.second }

    Column(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = ScreenGutter, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                if (store.alarms.isEmpty()) {
                    ReadoutPanel(emphasized = false) {
                        Column(
                            Modifier.fillMaxWidth().padding(vertical = 12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            Box(
                                Modifier.size(64.dp).background(colors.secondaryContainer, CircleShape),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    painterResource(R.drawable.ic_alarm_off),
                                    null,
                                    Modifier.size(32.dp),
                                    tint = colors.onSecondaryContainer,
                                )
                            }
                            Text(stringResource(R.string.no_alarms), style = MaterialTheme.typography.headlineSmall)
                            AddButton(R.string.new_alarm) { onEdit(0) }
                        }
                    }
                } else if (next == null) {
                    ReadoutPanel(emphasized = false) {
                        ReadoutLabel(stringResource(R.string.no_enabled_alarms), R.drawable.ic_alarm_off)
                    }
                } else {
                    val (alarm, timestamp) = next
                    val at = Instant.ofEpochMilli(timestamp).atZone(now.zone)
                    ReadoutPanel {
                        ReadoutLabel(stringResource(R.string.next_alarm), R.drawable.ic_alarm)
                        ClockDigits(format.format(at), Modifier.fillMaxWidth())
                        val date = DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "EEEdMMM"), locale)
                        val until = stringResource(R.string.in_span, spanText(timestamp - now.toInstant().toEpochMilli()))
                        Text(
                            listOf(date.format(at), until, alarm.label).filter { it.isNotEmpty() }.joinToString(" · "),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        DayScale(at.toLocalTime(), locale)
                    }
                }
            }
            if (store.alarms.isNotEmpty()) item { ListHeading(stringResource(R.string.saved_alarms)) }
            items(store.alarms.sortedBy { it.hour * 60 + it.minute }, key = { it.id }) { alarm ->
                val label = alarm.label.ifEmpty { stringResource(R.string.alarm) }
                val time = format.format(LocalTime.of(alarm.hour, alarm.minute))
                Surface(
                    Modifier.animateItem(),
                    shape = EntryShape,
                    color = colors.surfaceContainerLow,
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(end = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(
                            Modifier
                                .weight(1f)
                                .clickable(onClickLabel = stringResource(R.string.edit_alarm)) { onEdit(alarm.id) }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            ClockDigits(
                                time,
                                Modifier.fillMaxWidth(),
                                color = if (alarm.enabled) colors.onSurface else colors.onSurfaceVariant,
                                max = 34.sp,
                            )
                            Text(label, style = MaterialTheme.typography.titleSmall)
                            Text(
                                daysText(context, alarm, now, locale),
                                color = colors.onSurfaceVariant,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                        Switch(
                            alarm.enabled,
                            { store.put(alarm.copy(enabled = it, snoozedUntil = 0)) },
                            Modifier.semantics { contentDescription = "$label, $time" },
                        )
                    }
                }
            }
        }
        if (store.alarms.isNotEmpty()) AddAction(R.string.new_alarm) { onEdit(0) }
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
    val hours24 = DateFormat.is24HourFormat(context)
    val time = rememberTimePickerState(draft.hour, draft.minute, hours24)
    var hourText by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        val hour = (draft.hour + 11) % 12 + 1
        mutableStateOf(timeFieldValue(hour, selectAll = true))
    }
    var minuteText by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(timeFieldValue(draft.minute))
    }
    var isPm by rememberSaveable { mutableStateOf(draft.hour >= 12) }
    val selectedTime = if (hours24) {
        LocalTime.of(time.hour, time.minute)
    } else {
        val hour = hourText.text.toIntOrNull()?.takeIf { it in 1..12 }
        val minute = minuteText.text.toIntOrNull()?.takeIf { it in 0..59 }
        if (hour != null && minute != null) LocalTime.of(hour % 12 + if (isPm) 12 else 0, minute) else null
    }
    var labeling by remember { mutableStateOf(false) }
    val pickSound = rememberSoundPicker(draft.sound) { draft = draft.copy(sound = it) }

    Column(Modifier.safeDrawingPadding().fillMaxSize()) {
        Bar(
            stringResource(if (original == null) R.string.new_alarm else R.string.edit_alarm),
            R.drawable.ic_close,
            R.string.close,
            onClose,
        )
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            ReadoutPanel(Modifier.padding(horizontal = ScreenGutter, vertical = 8.dp), emphasized = false) {
                val centered = Modifier.align(Alignment.CenterHorizontally)
                if (hours24) {
                    val typography = MaterialTheme.typography
                    MaterialTheme(
                        typography = typography.copy(
                            displayLarge = typography.displayLarge.copy(
                                fontFamily = ClockNumerals,
                                fontWeight = FontWeight.Medium,
                            ),
                        ),
                    ) {
                        TimeInput(time, centered)
                    }
                } else {
                    TwelveHourTimeInput(
                        hour = hourText,
                        onHourChange = { hourText = it },
                        minute = minuteText,
                        onMinuteChange = { minuteText = it },
                        isPm = isPm,
                        onPeriodChange = { isPm = it },
                        modifier = centered,
                    )
                }
            }
            Section(stringResource(R.string.repeat))
            Row(
                Modifier.fillMaxWidth().padding(horizontal = ScreenGutter, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                for (day in week(locale)) {
                    val on = day in draft.days
                    Box(
                        Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .clip(CircleShape)
                            .then(
                                if (on) Modifier.background(colors.primary)
                                else Modifier.border(1.dp, colors.outlineVariant, CircleShape),
                            )
                            .semantics { contentDescription = day.getDisplayName(TextStyle.FULL, locale) }
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
            Modifier.fillMaxWidth().padding(horizontal = ScreenGutter, vertical = 8.dp),
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
                    selectedTime?.let {
                        store.put(draft.copy(hour = it.hour, minute = it.minute, enabled = true, snoozedUntil = 0))
                        onSaved()
                        onClose()
                    }
                },
                Modifier.heightIn(min = 52.dp),
                shape = EntryShape,
                enabled = selectedTime != null,
            ) {
                Icon(painterResource(R.drawable.ic_check), null, Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.save))
            }
        }
    }

    if (labeling) LabelDialog(draft.label, { labeling = false }) { draft = draft.copy(label = it) }
}
