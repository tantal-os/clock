package app.tantal.clock

import android.content.SharedPreferences
import android.icu.util.TimeZone
import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.Collator
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlinx.coroutines.delay

// A time zone on the world tab, with the tag the user gave it.
data class Zone(val id: String, val tag: String = "")

class Zones(private val prefs: SharedPreferences) {
    val list = mutableStateListOf<Zone>().apply {
        val known = ZoneId.getAvailableZoneIds()
        val lines = prefs.getString("zones", "").orEmpty().lines().map { it.split('\t') }
        addAll(lines.filter { it[0] in known }.map { Zone(it[0], it.getOrElse(1) { "" }) })
    }

    fun add(id: String) {
        list += Zone(id)
        save()
    }

    fun tag(zone: Zone, tag: String) {
        val index = list.indexOf(zone)
        if (index >= 0) list[index] = zone.copy(tag = tag.replace('\t', ' '))
        save()
    }

    fun remove(zone: Zone) {
        list -= zone
        save()
    }

    private fun save() = prefs.edit().putString("zones", list.joinToString("\n") { "${it.id}\t${it.tag}" }).apply()
}

// "Central European Time", "Moscow Time": the name of the zone, not of a city in it.
private fun zoneName(id: String, locale: Locale): String =
    TimeZone.getTimeZone(id).getDisplayName(false, TimeZone.LONG_GENERIC, locale)

private fun gmt(id: String, at: Instant, locale: Locale): String =
    DateTimeFormatter.ofPattern("O", locale).format(at.atZone(ZoneId.of(id)))

// The current time, refreshed at the start of every minute.
@Composable
fun rememberNow(): Instant {
    val now by produceState(Instant.now()) {
        while (true) {
            delay(60_000 - System.currentTimeMillis() % 60_000)
            value = Instant.now()
        }
    }
    return now
}

@Composable
private fun difference(minutes: Int): String {
    if (minutes == 0) return stringResource(R.string.same_time)
    val hours = abs(minutes) / 60
    val rest = abs(minutes) % 60
    val span = when {
        rest == 0 -> stringResource(R.string.hours, hours)
        hours == 0 -> stringResource(R.string.minutes, rest)
        else -> stringResource(R.string.hours_minutes, hours, rest)
    }
    return stringResource(if (minutes > 0) R.string.ahead else R.string.behind, span)
}

@Composable
fun World(zones: Zones, onAdd: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val locale = LocalConfiguration.current.locales[0]
    val now = rememberNow()
    val home = ZoneId.systemDefault()
    val time = rememberTimeFormat()
    val date = remember(locale) {
        DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "EEEdMMM"), locale)
    }
    var editing by remember { mutableStateOf<Zone?>(null) }

    Column(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = ScreenGutter, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                val local = now.atZone(home)
                ReadoutPanel {
                    ReadoutLabel(stringResource(R.string.local_time), R.drawable.ic_public)
                    ClockDigits(time.format(local), Modifier.fillMaxWidth(), max = 80.sp)
                    Text(date.format(local), style = MaterialTheme.typography.titleMedium)
                    Text(
                        "${zoneName(home.id, locale)} · ${gmt(home.id, now, locale)}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            if (zones.list.isNotEmpty()) item { ListHeading(stringResource(R.string.saved_zones)) }
            items(zones.list, key = { it.id }) { zone ->
                val remote = now.atZone(ZoneId.of(zone.id))
                val title = zone.tag.ifEmpty { zoneName(zone.id, locale) }
                val delta = difference(offsetMinutes(remote.zone, home, now))
                val detail = if (remote.toLocalDate() == now.atZone(home).toLocalDate()) delta
                else "${date.format(remote)} · $delta"
                Surface(
                    { editing = zone },
                    Modifier.animateItem().fillMaxWidth(),
                    shape = EntryShape,
                    color = colors.surfaceContainerLow,
                ) {
                    Row(
                        Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1.35f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(title, style = MaterialTheme.typography.titleSmall)
                            Text(detail, color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                        }
                        ClockDigits(time.format(remote), Modifier.weight(1f).wrapContentWidth(Alignment.End), max = 34.sp)
                    }
                }
            }
        }
        AddAction(R.string.add_zone, onAdd)
    }

    editing?.let { zone ->
        var tag by remember(zone) { mutableStateOf(zone.tag) }
        AlertDialog(
            onDismissRequest = { editing = null },
            confirmButton = {
                TextButton({
                    zones.tag(zone, tag.trim())
                    editing = null
                }) { Text(stringResource(android.R.string.ok)) }
            },
            dismissButton = {
                TextButton(
                    {
                        zones.remove(zone)
                        editing = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = colors.error),
                ) { Text(stringResource(R.string.remove)) }
            },
            title = { Text(zoneName(zone.id, locale)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(gmt(zone.id, now, locale))
                    OutlinedTextField(
                        tag,
                        { tag = it.take(24) },
                        label = { Text(stringResource(R.string.tag)) },
                        singleLine = true,
                    )
                }
            },
        )
    }
}

private class Choice(val id: String, val name: String, val offset: Int, val gmt: String)

@Composable
fun ZonePicker(zones: Zones, onClose: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val locale = LocalConfiguration.current.locales[0]
    val now = rememberNow()
    val time = rememberTimeFormat()
    var query by rememberSaveable { mutableStateOf("") }
    // Many places share a zone, so each name is listed once for each offset it has now.
    val all = remember(locale) {
        val known = ZoneId.getAvailableZoneIds()
        val at = Instant.now()
        TimeZone.getAvailableIDs(TimeZone.SystemTimeZoneType.CANONICAL_LOCATION, null, null)
            .filter { it in known }
            .map { Choice(it, zoneName(it, locale), ZoneId.of(it).rules.getOffset(at).totalSeconds, gmt(it, at, locale)) }
            .distinctBy { it.name to it.offset }
            .sortedWith(compareBy<Choice> { it.offset }.thenBy(Collator.getInstance(locale)) { it.name })
    }
    val added = zones.list.map { it.id }.toSet()
    val search = query.trim()
    val shown = all.filter {
        it.id !in added && (it.name.contains(search, ignoreCase = true) || it.gmt.contains(search, ignoreCase = true))
    }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    Column(Modifier.safeDrawingPadding().fillMaxSize()) {
        Bar(stringResource(R.string.add_zone), R.drawable.ic_close, R.string.close, onClose)
        OutlinedTextField(
            query,
            { query = it },
            Modifier.fillMaxWidth().padding(horizontal = ScreenGutter).focusRequester(focus),
            label = { Text(stringResource(R.string.search_zones)) },
            leadingIcon = { Icon(painterResource(R.drawable.ic_search), null) },
            singleLine = true,
            shape = EntryShape,
        )
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = ScreenGutter, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (shown.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.no_matching_zones),
                        Modifier.padding(12.dp),
                        color = colors.onSurfaceVariant,
                    )
                }
            }
            items(shown, key = { it.id }) { choice ->
                Surface(
                    onClick = {
                        zones.add(choice.id)
                        onClose()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = EntryShape,
                    color = colors.surfaceContainerLow,
                ) {
                    Row(
                        Modifier.heightIn(min = 60.dp).padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1.4f)) {
                            Text(choice.name, style = MaterialTheme.typography.titleSmall)
                            Text(choice.gmt, color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                        }
                        ClockDigits(
                            time.format(now.atZone(ZoneId.of(choice.id))),
                            Modifier.weight(1f).wrapContentWidth(Alignment.End),
                            min = 12.sp,
                            max = 22.sp,
                        )
                    }
                }
            }
        }
    }
}
