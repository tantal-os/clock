package app.tantal.clock

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import android.text.format.DateFormat
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.tantal.theme.TantalTheme
import java.time.LocalTime

private const val ACTION_STOP = "app.tantal.clock.STOP"

// For an alarm this is snooze; for a timer, one more minute.
private const val ACTION_LATER = "app.tantal.clock.LATER"
private const val CHANNEL = "ring"
private const val NOTIFICATION = 1
private const val RISE_SECONDS = 30

class Receiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_FIRE) {
            context.startForegroundService(Intent(context, RingService::class.java).setData(intent.data))
        } else {
            Store.get(context).schedule()
        }
    }
}

class RingService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ALARM)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()
    private val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
        .setAudioAttributes(attributes)
        .build()
    private var player: MediaPlayer? = null
    private var lock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?) = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> finish(later = false)
            ACTION_LATER -> finish(later = true)
            else -> ring(intent?.data)
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() = hush()

    private fun ring(target: Uri?) {
        val store = Store.get(this)
        val id = target?.lastPathSegment?.toIntOrNull()
        val alarm = store.alarms.find { target?.host == "alarm" && it.id == id }
        val timer = store.timers.find { target?.host == "timer" && it.id == id && it.running }
        val title = (alarm?.label ?: timer?.label).orEmpty()
            .ifEmpty { getString(if (timer != null) R.string.timer else R.string.alarm) }
        val text = if (alarm != null) {
            clockTimeFormat(resources.configuration.locales[0], DateFormat.is24HourFormat(this))
                .format(LocalTime.of(alarm.hour, alarm.minute))
        } else {
            getString(R.string.time_is_up)
        }
        // Started with startForegroundService, so this has to come before anything that may stop the service.
        startForeground(
            NOTIFICATION,
            notification(title, text, alarm != null),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED,
        )
        if (alarm == null && timer == null) {
            if (store.ringing == null) finish(later = false)
            return
        }
        if (target == store.ringing) return
        if (store.ringing != null) quiet(later = false)

        store.ringing = target
        if (alarm != null) store.put(alarm.copy(snoozedUntil = 0))
        val silence = store.silenceMinutes * 60_000L
        lock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "clock:ring")
            .apply { acquire(silence + 10_000) }
        play(alarm?.sound ?: store.timerSound, rising = alarm?.rising == true)
        if (alarm?.vibrate ?: store.timerVibrate) {
            getSystemService(Vibrator::class.java).vibrate(
                VibrationEffect.createWaveform(longArrayOf(0, 600, 600), 0),
                VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM),
            )
        }
        handler.postDelayed({ finish(later = false) }, silence)
    }

    private fun play(sound: String, rising: Boolean) {
        // A picked sound can be gone, or unreadable before the first unlock; the system alarm sound is always there.
        val sources =
            listOfNotNull(sound.takeIf { it.isNotEmpty() }?.let(Uri::parse), Settings.System.DEFAULT_ALARM_ALERT_URI)
        player = sources.firstNotNullOfOrNull { source ->
            val player = MediaPlayer()
            try {
                player.setAudioAttributes(attributes)
                player.setDataSource(this, source)
                player.isLooping = true
                player.prepare()
                player
            } catch (e: Exception) {
                player.release()
                null
            }
        }
        getSystemService(AudioManager::class.java).requestAudioFocus(focus)
        if (rising) rise(1)
        player?.start()
    }

    private fun rise(second: Int) {
        val volume = second / RISE_SECONDS.toFloat()
        player?.setVolume(volume, volume)
        if (second < RISE_SECONDS) handler.postDelayed({ rise(second + 1) }, 1000)
    }

    private fun hush() {
        handler.removeCallbacksAndMessages(null)
        player?.release()
        player = null
        getSystemService(Vibrator::class.java).cancel()
        getSystemService(AudioManager::class.java).abandonAudioFocusRequest(focus)
        lock?.takeIf { it.isHeld }?.release()
        lock = null
    }

    // Ends the current ring and records what became of the alarm or the timer.
    private fun quiet(later: Boolean) {
        hush()
        val store = Store.get(this)
        val target = store.ringing ?: return
        val id = target.lastPathSegment?.toIntOrNull()
        val now = System.currentTimeMillis()
        store.ringing = null
        if (target.host == "alarm") {
            val alarm = store.alarms.find { it.id == id } ?: return
            store.put(
                when {
                    later -> alarm.copy(snoozedUntil = now + store.snoozeMinutes * 60_000L)
                    alarm.days.isEmpty() -> alarm.copy(enabled = false)
                    else -> alarm
                },
            )
        } else {
            val timer = store.timers.find { it.id == id } ?: return
            store.put(if (later) timer.addMinute(now) else timer.reset())
        }
    }

    private fun finish(later: Boolean) {
        quiet(later)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun notification(title: String, text: String, alarm: Boolean): Notification {
        val channel =
            NotificationChannel(CHANNEL, getString(R.string.channel_ring), NotificationManager.IMPORTANCE_HIGH)
        // The service plays the sound and vibrates itself, so that it can loop and rise.
        channel.setSound(null, null)
        channel.enableVibration(false)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)

        val screen = PendingIntent.getActivity(
            this,
            0,
            Intent(this, RingActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE,
        )

        fun action(action: String, label: Int) = Notification.Action.Builder(
            null,
            getString(label),
            PendingIntent.getService(
                this,
                0,
                Intent(this, RingService::class.java).setAction(action),
                PendingIntent.FLAG_IMMUTABLE,
            ),
        ).build()
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(if (alarm) R.drawable.ic_alarm else R.drawable.ic_hourglass)
            .setContentTitle(title)
            .setContentText(text)
            .setCategory(Notification.CATEGORY_ALARM)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setShowWhen(false)
            .setContentIntent(screen)
            .setFullScreenIntent(screen, true)
            .setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
            .addAction(action(ACTION_LATER, if (alarm) R.string.snooze else R.string.add_minute))
            .addAction(action(ACTION_STOP, R.string.stop))
            .build()
    }
}

class RingActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        enableEdgeToEdge()
        setContent { TantalTheme { Ringing(::finish) } }
    }
}

@Composable
private fun Ringing(onDone: () -> Unit) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    val time = rememberTimeFormat()
    val store = remember { Store.get(context) }
    val target = store.ringing
    val id = target?.lastPathSegment?.toIntOrNull()
    val alarm = store.alarms.find { target?.host == "alarm" && it.id == id }
    val timer = store.timers.find { target?.host == "timer" && it.id == id }
    if (alarm == null && timer == null) {
        LaunchedEffect(Unit) { onDone() }
        return
    }

    fun send(action: String) {
        context.startService(Intent(context, RingService::class.java).setAction(action))
    }

    val kind = stringResource(if (alarm != null) R.string.alarm else R.string.timer)
    val label = (alarm?.label ?: timer?.label).orEmpty()
    val reading = when {
        alarm != null -> time.format(LocalTime.of(alarm.hour, alarm.minute))
        timer != null -> timerText(timer.total)
        else -> return
    }

    Surface(Modifier.fillMaxSize(), color = colors.primaryContainer, contentColor = colors.onPrimaryContainer) {
        Box(Modifier.safeDrawingPadding(), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier.widthIn(max = ScreenWidth).fillMaxSize().padding(horizontal = 24.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Spacer(Modifier.weight(0.65f))
                ReadoutLabel(
                    if (label.isEmpty()) kind else "$kind · $label",
                    if (alarm != null) R.drawable.ic_alarm else R.drawable.ic_hourglass,
                )
                ClockDigits(reading, Modifier.fillMaxWidth(), max = 96.sp)
                if (timer != null) Text(stringResource(R.string.time_is_up), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.weight(1f))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        { send(ACTION_LATER) },
                        Modifier.weight(1f).heightIn(min = 72.dp),
                        shape = EntryShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.surfaceContainerLow,
                            contentColor = colors.onSurface,
                        ),
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            if (alarm != null) {
                                Text(stringResource(R.string.snooze))
                                Text(
                                    stringResource(R.string.minutes, store.snoozeMinutes),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            } else {
                                Text(stringResource(R.string.add_minute))
                            }
                        }
                    }
                    Button(
                        { send(ACTION_STOP) },
                        Modifier.weight(1f).heightIn(min = 72.dp),
                        shape = EntryShape,
                    ) {
                        Text(stringResource(R.string.stop), style = MaterialTheme.typography.titleLarge)
                    }
                }
            }
        }
    }
}
