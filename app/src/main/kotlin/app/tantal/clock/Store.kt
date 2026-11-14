package app.tantal.clock

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.provider.AlarmClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.File
import java.time.ZonedDateTime

const val ACTION_FIRE = "app.tantal.clock.FIRE"

// Alarms, timers and their settings, shared by the screens, the receiver and the ringing service.
// Everything is read and changed on the main thread.
class Store private constructor(private val app: Context) {
    // Device-protected storage: after a reboot alarms must ring before the first unlock.
    private val safe = app.createDeviceProtectedStorageContext()
    private val alarmFile = File(safe.filesDir, "alarms.tsv")
    private val timerFile = File(safe.filesDir, "timers.tsv")
    private val manager = app.getSystemService(AlarmManager::class.java)

    val settings: SharedPreferences = safe.getSharedPreferences("settings", Context.MODE_PRIVATE)
    val alarms = mutableStateListOf<Alarm>().apply { addAll(read(alarmFile) { Alarm.decode(it) }) }
    val timers = mutableStateListOf<Timer>().apply { addAll(read(timerFile) { Timer.decode(it) }) }

    // What is ringing now: `clock://alarm/3` or `clock://timer/1`.
    var ringing by mutableStateOf<Uri?>(null)

    val snoozeMinutes get() = settings.getInt("snooze", 10)
    val silenceMinutes get() = settings.getInt("silence", 10)
    val timerSound get() = settings.getString("timer_sound", "").orEmpty()
    val timerVibrate get() = settings.getBoolean("timer_vibrate", true)

    private fun <T : Any> read(file: File, decode: (String) -> T?) =
        if (file.exists()) file.readLines().mapNotNull(decode) else emptyList()

    fun newAlarmId() = (alarms.maxOfOrNull { it.id } ?: 0) + 1

    fun newTimerId() = (timers.maxOfOrNull { it.id } ?: 0) + 1

    fun put(alarm: Alarm) {
        val index = alarms.indexOfFirst { it.id == alarm.id }
        if (index < 0) alarms += alarm else alarms[index] = alarm
        save()
    }

    fun put(timer: Timer) {
        val index = timers.indexOfFirst { it.id == timer.id }
        if (index < 0) timers += timer else timers[index] = timer
        save()
    }

    fun remove(alarm: Alarm) {
        alarms.removeAll { it.id == alarm.id }
        manager.cancel(fire(target("alarm", alarm.id)))
        save()
    }

    fun remove(timer: Timer) {
        timers.removeAll { it.id == timer.id }
        manager.cancel(fire(target("timer", timer.id)))
        save()
    }

    private fun save() {
        alarmFile.writeText(alarms.joinToString("") { it.encode() + "\n" })
        timerFile.writeText(timers.joinToString("") { it.encode() + "\n" })
        schedule()
    }

    // Hands every pending alarm and running timer to the system again. It is cheap, so it runs after
    // each change, at boot and when the clock or the time zone changes.
    fun schedule() {
        val now = ZonedDateTime.now()
        val show = PendingIntent.getActivity(
            app,
            0,
            Intent(app, MainActivity::class.java).setAction(AlarmClock.ACTION_SHOW_ALARMS),
            PendingIntent.FLAG_IMMUTABLE,
        )
        for (alarm in alarms) {
            val fire = fire(target("alarm", alarm.id))
            val at = alarm.trigger(now)
            if (at == null) manager.cancel(fire) else manager.setAlarmClock(AlarmManager.AlarmClockInfo(at, show), fire)
        }
        for (timer in timers) {
            // A timer that is ringing is still running and overdue: setting it again would fire it again.
            if (target("timer", timer.id) == ringing) continue
            val fire = fire(target("timer", timer.id))
            val at = timer.endAt
            if (at == null) manager.cancel(fire) else manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, fire)
        }
    }

    private fun fire(target: Uri) = PendingIntent.getBroadcast(
        app,
        0,
        Intent(app, Receiver::class.java).setAction(ACTION_FIRE).setData(target),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    companion object {
        private var instance: Store? = null

        fun get(context: Context) = instance ?: Store(context.applicationContext).also { instance = it }

        fun target(kind: String, id: Int): Uri = Uri.parse("clock://$kind/$id")
    }
}
