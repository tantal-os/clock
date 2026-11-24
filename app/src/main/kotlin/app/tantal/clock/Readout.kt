package app.tantal.clock

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.tantal.theme.ClockNumerals
import java.time.LocalTime
import java.util.Locale

internal val ScreenWidth = 640.dp
internal val ScreenGutter = 16.dp
internal val EntryShape = RoundedCornerShape(18.dp)

@Composable
internal fun ReadoutPanel(
    modifier: Modifier = Modifier,
    emphasized: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier.fillMaxWidth(),
        shape = RoundedCornerShape(32.dp),
        color = if (emphasized) colors.primaryContainer else colors.surfaceContainerLow,
        contentColor = if (emphasized) colors.onPrimaryContainer else colors.onSurface,
    ) {
        Column(
            Modifier.padding(horizontal = 20.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = content,
        )
    }
}

@Composable
internal fun ReadoutLabel(text: String, icon: Int) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(icon), null, Modifier.size(20.dp))
        Text(text, style = MaterialTheme.typography.titleSmall)
    }
}

@Composable
internal fun ClockDigits(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = LocalContentColor.current,
    min: TextUnit = 16.sp,
    max: TextUnit = 72.sp,
    fraction: String = "",
) {
    val hasPeriod = text.endsWith(" AM") || text.endsWith(" PM")
    val main = if (hasPeriod) text.dropLast(3) else text
    val reading = buildAnnotatedString {
        append(main)
        if (hasPeriod) {
            withStyle(SpanStyle(fontSize = 0.46.em)) { append(text.takeLast(3)) }
        }
        if (fraction.isNotEmpty()) {
            withStyle(SpanStyle(fontSize = 0.48.em)) { append(fraction) }
        }
    }
    Text(
        reading,
        modifier,
        color = color,
        style = MaterialTheme.typography.displayLarge.copy(
            fontFamily = if (main.any(Char::isLetter)) FontFamily.Default else ClockNumerals,
            fontWeight = FontWeight.Medium,
            fontFeatureSettings = "tnum",
            lineHeight = TextUnit.Unspecified,
            letterSpacing = 0.sp,
        ),
        autoSize = TextAutoSize.StepBased(min, max, 1.sp),
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
internal fun DayScale(time: LocalTime, locale: Locale) {
    val ink = LocalContentColor.current
    val labels = remember(locale) { listOf(0, 6, 12, 18, 24).map { String.format(locale, "%02d", it) } }
    Column {
        Canvas(Modifier.fillMaxWidth().height(26.dp)) {
            val inset = 8.dp.toPx()
            val width = (size.width - inset * 2).coerceAtLeast(0f)
            val bottom = size.height - 3.dp.toPx()
            for (hour in 0..24) {
                val x = inset + hour / 24f * width
                val major = hour % 6 == 0
                val height = if (major) 12.dp else 6.dp
                drawLine(
                    ink.copy(alpha = if (major) 0.48f else 0.27f),
                    Offset(x, bottom - height.toPx()),
                    Offset(x, bottom),
                    strokeWidth = if (major) 1.5.dp.toPx() else 1.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }
            val marker = inset + time.toSecondOfDay() / 86_400f * width
            drawLine(
                ink,
                Offset(marker, 5.dp.toPx()),
                Offset(marker, bottom),
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round,
            )
            drawCircle(ink, 3.dp.toPx(), Offset(marker, 5.dp.toPx()))
        }
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                for (label in labels) ClockDigits(label, min = 8.sp, max = 11.sp)
            }
        }
    }
}

@Composable
internal fun ListHeading(text: String) {
    Text(
        text,
        Modifier.padding(start = 4.dp, top = 8.dp).semantics { heading() },
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
internal fun AddAction(label: Int, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = ScreenGutter, vertical = 6.dp),
        horizontalArrangement = Arrangement.End,
    ) {
        AddButton(label, onClick)
    }
}

@Composable
internal fun AddButton(label: Int, onClick: () -> Unit) {
    Button(onClick, Modifier.widthIn(min = 48.dp).heightIn(min = 52.dp), shape = EntryShape) {
        Icon(painterResource(R.drawable.ic_add), null, Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(stringResource(label))
    }
}
