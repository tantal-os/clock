package app.tantal.clock

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.provider.Settings
import android.text.format.DateFormat
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.launch

// The corner radius is a fraction of the shorter side: `rest` when idle, squarer while pressed.
@Composable
fun Key(
    modifier: Modifier,
    container: Color,
    color: Color,
    onClick: () -> Unit,
    enabled: Boolean = true,
    rest: Float = 0.5f,
    content: @Composable () -> Unit,
) {
    val motion = MaterialTheme.motionScheme
    val interaction = remember { MutableInteractionSource() }
    val corner = remember { Animatable(rest) }
    LaunchedEffect(interaction) {
        interaction.interactions.collect {
            when (it) {
                // Awaited, so a quick tap plays the whole press before its release is handled.
                is PressInteraction.Press -> corner.animateTo(rest * 0.6f, motion.fastEffectsSpec())
                is PressInteraction.Release, is PressInteraction.Cancel ->
                    launch { corner.animateTo(rest, motion.slowEffectsSpec()) }
            }
        }
    }
    Box(
        modifier
            .graphicsLayer {
                shape = RoundedCornerShape(size.minDimension * corner.value)
                clip = true
                alpha = if (enabled) 1f else 0.38f
            }
            .background(container)
            .clickable(interaction, LocalIndication.current, enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        CompositionLocalProvider(LocalContentColor provides color, content = content)
    }
}

// The top row of a screen that is opened over the tabs.
@Composable
fun Bar(title: String, icon: Int, iconLabel: Int, onClose: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = ScreenGutter, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        FilledTonalIconButton(onClose, Modifier.size(48.dp)) {
            Icon(painterResource(icon), stringResource(iconLabel))
        }
        Text(title, style = MaterialTheme.typography.headlineSmall)
    }
}

@Composable
fun Section(title: String) {
    Text(
        title,
        Modifier.padding(start = 24.dp, top = 16.dp, end = 24.dp, bottom = 8.dp),
        color = MaterialTheme.colorScheme.primary,
        style = MaterialTheme.typography.labelLarge,
    )
}

@Composable
fun Item(
    title: String,
    sub: String = "",
    icon: Int? = null,
    onClick: () -> Unit,
) {
    SettingRow(title, sub, icon, Modifier.clickable(onClick = onClick)) {}
}

@Composable
private fun SettingRow(title: String, sub: String, icon: Int?, action: Modifier, trailing: @Composable () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .then(action)
            .heightIn(min = 56.dp)
            .padding(start = if (icon != null) 8.dp else 24.dp, top = 8.dp, end = 24.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                Icon(painterResource(icon), null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Column(Modifier.weight(1f)) {
            Text(title)
            if (sub.isNotEmpty()) {
                Text(
                    sub,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        trailing()
    }
}

@Composable
fun SwitchItem(title: String, sub: String = "", icon: Int? = null, checked: Boolean, onChange: (Boolean) -> Unit) {
    SettingRow(title, sub, icon, Modifier.toggleable(checked, role = Role.Switch, onValueChange = onChange)) {
        Switch(checked, null)
    }
}

@Composable
fun LabelDialog(label: String, onDismiss: () -> Unit, onDone: (String) -> Unit) {
    var text by rememberSaveable { mutableStateOf(label) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton({
                onDone(text.trim())
                onDismiss()
            }) { Text(stringResource(android.R.string.ok)) }
        },
        dismissButton = { TextButton(onDismiss) { Text(stringResource(android.R.string.cancel)) } },
        title = { Text(stringResource(R.string.label)) },
        text = { OutlinedTextField(text, { text = it.take(40) }, Modifier.focusRequester(focus), singleLine = true) },
    )
}

fun soundTitle(context: Context, sound: String): String =
    sound.takeIf { it.isNotEmpty() }
        ?.let { RingtoneManager.getRingtone(context, Uri.parse(it))?.getTitle(context) }
        ?: context.getString(R.string.default_sound)

// Opens the system sound picker; an empty string stands for the system alarm sound.
@Composable
fun rememberSoundPicker(current: String, onPicked: (String) -> Unit): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val picked = result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
        if (result.resultCode == Activity.RESULT_OK && picked != null) {
            onPicked(picked.toString().takeIf { picked != Settings.System.DEFAULT_ALARM_ALERT_URI }.orEmpty())
        }
    }
    return {
        val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
            .putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
            .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
            .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
            .putExtra(RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI, Settings.System.DEFAULT_ALARM_ALERT_URI)
            .putExtra(
                RingtoneManager.EXTRA_RINGTONE_EXISTING_URI,
                current.takeIf { it.isNotEmpty() }?.let(Uri::parse) ?: Settings.System.DEFAULT_ALARM_ALERT_URI,
            )
        try {
            launcher.launch(intent)
        } catch (e: ActivityNotFoundException) {
            Log.w("Clock", "No activity can open the alarm sound picker", e)
            Toast.makeText(context, R.string.sound_picker_unavailable, Toast.LENGTH_SHORT).show()
        }
    }
}

internal fun clockTimeFormat(locale: Locale, hours24: Boolean): DateTimeFormatter =
    if (hours24) DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "Hm"), locale)
    else DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)

@Composable
internal fun rememberTimeFormat(): DateTimeFormatter {
    val locale = LocalConfiguration.current.locales[0]
    val hours24 = DateFormat.is24HourFormat(LocalContext.current)
    return remember(locale, hours24) { clockTimeFormat(locale, hours24) }
}

// "21 h", "21 h 5 min", "2 d 3 h": a rounded-up span for "in ..." lines.
@Composable
fun spanText(millis: Long): String {
    val minutes = ((millis + 59_999) / 60_000).toInt()
    val days = minutes / 1440
    val hours = minutes % 1440 / 60
    val rest = minutes % 60
    return when {
        days > 0 && hours == 0 -> stringResource(R.string.days, days)
        days > 0 -> stringResource(R.string.days_hours, days, hours)
        hours > 0 && rest == 0 -> stringResource(R.string.hours, hours)
        hours > 0 -> stringResource(R.string.hours_minutes, hours, rest)
        else -> stringResource(R.string.minutes, rest)
    }
}
