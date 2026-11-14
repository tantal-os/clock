package app.tantal.clock

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

private const val MINUTE = 60_000L

private fun String.oneLine() = replace('\t', ' ').replace('\n', ' ')

// `days` is empty for an alarm that rings once. `sound` is a ringtone URI, empty for the system alarm sound.
data class Alarm(
    val id: Int,
    val hour: Int,
    val minute: Int,
    val days: Set<DayOfWeek> = emptySet(),
    val label: String = "",
    val enabled: Boolean = true,
    val sound: String = "",
    val vibrate: Boolean = true,
    val rising: Boolean = true,
    val snoozedUntil: Long = 0,
) {
    // The next time of day the alarm is set for, on a day it repeats on.
    fun next(after: ZonedDateTime): ZonedDateTime = (0L..7L).firstNotNullOf { ahead ->
        ZonedDateTime.of(after.toLocalDate().plusDays(ahead), LocalTime.of(hour, minute), after.zone)
            .takeIf { it.isAfter(after) && (days.isEmpty() || it.dayOfWeek in days) }
    }

    // When to ring, in epoch milliseconds: a pending snooze comes before the regular time.
    fun trigger(now: ZonedDateTime): Long? {
        if (!enabled) return null
        val regular = next(now).toInstant().toEpochMilli()
        return if (snoozedUntil > now.toInstant().toEpochMilli()) minOf(snoozedUntil, regular) else regular
    }

    fun encode() = listOf(
        id,
        hour,
        minute,
        days.sumOf { 1 shl it.ordinal },
        enabled,
        vibrate,
        rising,
        snoozedUntil,
        sound,
        label.oneLine(),
    ).joinToString("\t")

    companion object {
        fun decode(line: String): Alarm? {
            val fields = line.split('\t')
            if (fields.size != 10) return null
            val days = fields[3].toIntOrNull() ?: return null
            return Alarm(
                id = fields[0].toIntOrNull() ?: return null,
                hour = fields[1].toIntOrNull()?.takeIf { it in 0..23 } ?: return null,
                minute = fields[2].toIntOrNull()?.takeIf { it in 0..59 } ?: return null,
                days = DayOfWeek.entries.filter { days and (1 shl it.ordinal) != 0 }.toSet(),
                enabled = fields[4].toBoolean(),
                vibrate = fields[5].toBoolean(),
                rising = fields[6].toBoolean(),
                snoozedUntil = fields[7].toLongOrNull() ?: 0,
                sound = fields[8],
                label = fields[9],
            )
        }
    }
}

data class Timer(
    val id: Int,
    val length: Long,
    val label: String = "",
    val extra: Long = 0,
    val left: Long = length,
    val endAt: Long? = null,
) {
    val running get() = endAt != null
    val total get() = length + extra

    fun remaining(now: Long) = endAt?.let { it - now } ?: left

    fun start(now: Long) = copy(endAt = now + left)

    fun pause(now: Long) = copy(left = remaining(now).coerceAtLeast(0), endAt = null)

    fun reset() = copy(extra = 0, left = length, endAt = null)

    fun addMinute(now: Long) =
        if (endAt != null) copy(extra = extra + MINUTE, endAt = maxOf(endAt, now) + MINUTE)
        else copy(extra = extra + MINUTE, left = left + MINUTE)

    fun encode() = listOf(id, length, extra, left, endAt ?: "", label.oneLine()).joinToString("\t")

    companion object {
        fun decode(line: String): Timer? {
            val fields = line.split('\t')
            if (fields.size != 6) return null
            return Timer(
                id = fields[0].toIntOrNull() ?: return null,
                length = fields[1].toLongOrNull()?.takeIf { it > 0 } ?: return null,
                extra = fields[2].toLongOrNull() ?: return null,
                left = fields[3].toLongOrNull() ?: return null,
                endAt = fields[4].toLongOrNull(),
                label = fields[5],
            )
        }
    }
}

// A countdown rounds up, so it reaches zero exactly when the time is out.
fun timerText(millis: Long, padded: Boolean = false): String {
    val seconds = (millis.coerceAtLeast(0) + 999) / 1000
    return when {
        seconds >= 3600 -> "%d:%02d:%02d".format(seconds / 3600, seconds / 60 % 60, seconds % 60)
        padded -> "%02d:%02d".format(seconds / 60, seconds % 60)
        else -> "%d:%02d".format(seconds / 60, seconds % 60)
    }
}

// Times are SystemClock.elapsedRealtime() milliseconds. A lap holds the total at the moment it was taken.
data class Stopwatch(val startedAt: Long? = null, val banked: Long = 0, val laps: List<Long> = emptyList()) {
    val running get() = startedAt != null

    fun elapsed(now: Long) = banked + (startedAt?.let { now - it } ?: 0)

    fun toggle(now: Long) =
        if (startedAt == null) copy(startedAt = now) else copy(startedAt = null, banked = elapsed(now))

    fun lap(now: Long) = copy(laps = laps + elapsed(now))

    fun encode(boot: Int) = "$boot;${startedAt ?: ""};$banked;${laps.joinToString(",")}"

    companion object {
        // elapsedRealtime restarts with the device, so a start time from another boot means nothing:
        // the stopwatch comes back paused at what it had banked.
        fun decode(text: String?, boot: Int): Stopwatch {
            val fields = text?.split(';')
            if (fields?.size != 4) return Stopwatch()
            return Stopwatch(
                startedAt = fields[1].toLongOrNull().takeIf { fields[0].toIntOrNull() == boot },
                banked = fields[2].toLongOrNull() ?: 0,
                laps = fields[3].split(',').mapNotNull { it.toLongOrNull() },
            )
        }
    }
}

// Minutes and seconds (hours once there are any), then the hundredths.
fun stopwatchText(millis: Long): Pair<String, String> {
    val seconds = millis / 1000
    val main = if (seconds >= 3600) {
        "%d:%02d:%02d".format(seconds / 3600, seconds / 60 % 60, seconds % 60)
    } else {
        "%02d:%02d".format(seconds / 60, seconds % 60)
    }
    return main to "%02d".format(millis / 10 % 100)
}

fun offsetMinutes(zone: ZoneId, home: ZoneId, at: Instant) =
    (zone.rules.getOffset(at).totalSeconds - home.rules.getOffset(at).totalSeconds) / 60
