package app.tantal.clock

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TimeTest {
    private val moscow = ZoneId.of("Europe/Moscow")

    // Saturday, 14 November 2026.
    private fun at(day: Int, hour: Int, minute: Int) = ZonedDateTime.of(2026, 11, day, hour, minute, 0, 0, moscow)

    @Test
    fun alarmRingsOnce() {
        val alarm = Alarm(1, hour = 6, minute = 30)
        assertEquals(at(14, 6, 30), alarm.next(at(14, 5, 0)))
        assertEquals(at(15, 6, 30), alarm.next(at(14, 6, 30)))
        assertEquals(at(15, 6, 30), alarm.next(at(14, 22, 0)))
        assertNull(alarm.copy(enabled = false).trigger(at(14, 5, 0)))
    }

    @Test
    fun alarmRepeats() {
        val weekdays = Alarm(1, 6, 30, DayOfWeek.entries.take(5).toSet())
        assertEquals(at(16, 6, 30), weekdays.next(at(14, 5, 0)))
        assertEquals(at(17, 6, 30), weekdays.next(at(16, 6, 30)))
        val saturdays = Alarm(2, 9, 0, setOf(DayOfWeek.SATURDAY))
        assertEquals(at(14, 9, 0), saturdays.next(at(14, 8, 59)))
        assertEquals(at(21, 9, 0), saturdays.next(at(14, 9, 0)))
    }

    @Test
    fun alarmSkipsTheHourThatDoesNotExist() {
        val berlin = ZoneId.of("Europe/Berlin")
        val next = Alarm(1, 2, 30).next(ZonedDateTime.of(2026, 3, 28, 12, 0, 0, 0, berlin))
        assertEquals(ZonedDateTime.of(2026, 3, 29, 3, 30, 0, 0, berlin), next)
    }

    @Test
    fun snoozeComesFirst() {
        val now = at(14, 6, 31)
        val millis = now.toInstant().toEpochMilli()
        val alarm = Alarm(1, 6, 30)
        assertEquals(at(15, 6, 30).toInstant().toEpochMilli(), alarm.trigger(now))
        assertEquals(millis + 600_000, alarm.copy(snoozedUntil = millis + 600_000).trigger(now))
        assertEquals(at(15, 6, 30).toInstant().toEpochMilli(), alarm.copy(snoozedUntil = millis - 1).trigger(now))
    }

    @Test
    fun alarmSurvivesRestart() {
        val alarm = Alarm(
            7, 6, 30, setOf(DayOfWeek.MONDAY, DayOfWeek.SUNDAY), "Work\tearly", enabled = false,
            sound = "content://media/1", vibrate = false, rising = false, snoozedUntil = 5,
        )
        assertEquals(alarm.copy(label = "Work early"), Alarm.decode(alarm.encode()))
        assertEquals(Alarm(1, 0, 0), Alarm.decode(Alarm(1, 0, 0).encode()))
        assertNull(Alarm.decode("garbage"))
        assertNull(Alarm.decode(Alarm(1, 0, 0).encode().replaceFirst("\t0\t", "\t24\t")))
    }

    @Test
    fun timerCounts() {
        var timer = Timer(1, length = 360_000, label = "Tea").start(1_000)
        assertTrue(timer.running)
        assertEquals(300_000, timer.remaining(61_000))

        timer = timer.pause(61_000)
        assertFalse(timer.running)
        assertEquals(300_000, timer.remaining(999_000))

        timer = timer.start(100_000).addMinute(100_000)
        assertEquals(360_000, timer.remaining(100_000))
        assertEquals(420_000, timer.total)

        // A minute added to a timer that has run out counts from now.
        assertEquals(60_000, timer.addMinute(900_000).remaining(900_000))
        assertEquals(Timer(1, 360_000, "Tea"), timer.reset())
        assertEquals(timer, Timer.decode(timer.encode()))
        assertEquals(timer.reset(), Timer.decode(timer.reset().encode()))
        assertNull(Timer.decode("garbage"))
    }

    @Test
    fun twelveHourTimeUsesEnglishPeriodMarkers() {
        for (locale in listOf(Locale.US, Locale.forLanguageTag("ru-RU"), Locale.CHINA)) {
            val format = clockTimeFormat(locale, hours24 = false)
            assertEquals("12:05 AM", format.format(LocalTime.of(0, 5)))
            assertEquals("12:05 PM", format.format(LocalTime.of(12, 5)))
            assertEquals("7:05 PM", format.format(LocalTime.of(19, 5)))
        }
    }

    @Test
    fun timerText() {
        assertEquals("0:00", timerText(0))
        assertEquals("0:00", timerText(-5_000))
        assertEquals("4:32", timerText(271_001))
        assertEquals("04:32", timerText(272_000, padded = true))
        assertEquals("1:00:00", timerText(3_600_000))
    }

    @Test
    fun stopwatchCounts() {
        var watch = Stopwatch().toggle(1_000).lap(3_500)
        assertTrue(watch.running)
        assertEquals(4_000, watch.elapsed(5_000))

        watch = watch.toggle(5_000)
        assertFalse(watch.running)
        assertEquals(4_000, watch.elapsed(60_000))

        watch = watch.toggle(9_000).lap(10_000)
        assertEquals(5_000, watch.elapsed(10_000))
        assertEquals(listOf(2_500L, 5_000L), watch.laps)
    }

    @Test
    fun stopwatchSurvivesRestart() {
        val watch = Stopwatch().toggle(1_000).lap(3_500)
        assertEquals(watch, Stopwatch.decode(watch.encode(boot = 7), boot = 7))
        assertEquals(Stopwatch(), Stopwatch.decode(Stopwatch().encode(boot = 7), boot = 7))

        val paused = watch.toggle(5_000)
        assertEquals(paused, Stopwatch.decode(paused.encode(boot = 7), boot = 8))
        assertEquals(Stopwatch(laps = listOf(2_500L)), Stopwatch.decode(watch.encode(boot = 7), boot = 8))

        assertEquals(Stopwatch(), Stopwatch.decode(null, boot = 7))
        assertEquals(Stopwatch(), Stopwatch.decode("garbage", boot = 7))
    }

    @Test
    fun stopwatchText() {
        assertEquals("00:00" to "00", stopwatchText(0))
        assertEquals("01:24" to "58", stopwatchText(84_589))
        assertEquals("59:59" to "99", stopwatchText(3_599_999))
        assertEquals("1:02:03" to "45", stopwatchText(3_723_450))
    }

    @Test
    fun offsets() {
        val winter = Instant.parse("2026-01-15T12:00:00Z")
        val summer = Instant.parse("2026-07-15T12:00:00Z")
        assertEquals(360, offsetMinutes(ZoneId.of("Asia/Tokyo"), moscow, winter))
        assertEquals(-180, offsetMinutes(ZoneId.of("Europe/London"), moscow, winter))
        assertEquals(-120, offsetMinutes(ZoneId.of("Europe/London"), moscow, summer))
        assertEquals(330, offsetMinutes(ZoneId.of("Asia/Kolkata"), ZoneId.of("UTC"), winter))
        assertEquals(0, offsetMinutes(moscow, moscow, winter))
    }
}
