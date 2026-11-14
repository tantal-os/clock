package app.tantal.clock

import android.content.SharedPreferences
import android.icu.util.TimeZone
import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
fun rememberTimeFormat(locale: Locale): DateTimeFormatter {
    val hours24 = DateFormat.is24HourFormat(LocalContext.current)
    return remember(locale, hours24) { DateTimeFormatter.ofPattern(if (hours24) "H:mm" else "h:mm a", locale) }
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
    val time = rememberTimeFormat(locale)
    val date = remember(locale) {
        DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "EEEdMMM"), locale)
    }
    var editing by remember { mutableStateOf<Zone?>(null) }

    Box(Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            GridCells.Fixed(2),
            // The bottom padding keeps the last row clear of the add button.
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 88.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(Modifier.padding(start = 8.dp, end = 8.dp, bottom = 4.dp)) {
                    Text(
                        time.format(now.atZone(home)),
                        autoSize = TextAutoSize.StepBased(40.sp, 96.sp, 1.sp),
                        lineHeight = 104.sp,
                        maxLines = 1,
                    )
                    Text(
                        "${date.format(now.atZone(home))} · ${zoneName(home.id, locale)}",
                        color = colors.onSurfaceVariant,
                    )
                }
            }
            items(zones.list, key = { it.id }) { zone ->
                Column(
                    Modifier
                        .animateItem()
                        .clip(RoundedCornerShape(28.dp))
                        .background(colors.surfaceContainerLow)
                        .clickable { editing = zone }
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(time.format(now.atZone(ZoneId.of(zone.id))), fontSize = 32.sp, lineHeight = 40.sp, maxLines = 1)
                    Text(
                        zone.tag.ifEmpty { zoneName(zone.id, locale) },
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        difference(offsetMinutes(ZoneId.of(zone.id), home, now)),
                        color = colors.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
        FloatingActionButton(onAdd, Modifier.align(Alignment.BottomEnd).padding(16.dp)) {
            Icon(painterResource(R.drawable.ic_add), stringResource(R.string.add_zone))
        }
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
    val time = rememberTimeFormat(locale)
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
    val added = zones.list.map { it.id }
    val shown = all.filter {
        it.id !in added && (it.name.contains(query.trim(), ignoreCase = true) || it.gmt.contains(query.trim(), ignoreCase = true))
    }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    Column(Modifier.safeDrawingPadding()) {
        Bar(stringResource(R.string.add_zone), R.drawable.ic_close, R.string.close, onClose)
        LazyColumn(Modifier.weight(1f)) {
            items(shown, key = { it.id }) { choice ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable {
                            zones.add(choice.id)
                            onClose()
                        }
                        .heightIn(min = 56.dp)
                        .padding(horizontal = 24.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(choice.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(choice.gmt, color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                    }
                    Text(
                        time.format(now.atZone(ZoneId.of(choice.id))),
                        color = colors.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
        // Search sits at the bottom, under the thumb and right above the keyboard.
        Row(
            Modifier
                .padding(start = 16.dp, top = 4.dp, end = 16.dp, bottom = 8.dp)
                .fillMaxWidth()
                .height(56.dp)
                .clip(CircleShape)
                .background(colors.surfaceContainerHigh)
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(painterResource(R.drawable.ic_search), null, tint = colors.onSurfaceVariant)
            BasicTextField(
                query,
                { query = it },
                Modifier.weight(1f).focusRequester(focus),
                textStyle = LocalTextStyle.current.copy(color = colors.onSurface),
                singleLine = true,
                cursorBrush = SolidColor(colors.primary),
            ) { field ->
                Box {
                    if (query.isEmpty()) Text(stringResource(R.string.search_zones), color = colors.onSurfaceVariant)
                    field()
                }
            }
        }
    }
}
