package app.tantal.clock

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.tantal.theme.ClockNumerals

internal fun timeFieldValue(value: Int, selectAll: Boolean = false): TextFieldValue {
    val text = value.toString().padStart(2, '0')
    val start = if (selectAll) 0 else text.length
    return TextFieldValue(text, TextRange(start, text.length))
}

@Composable
internal fun TwelveHourTimeInput(
    hour: TextFieldValue,
    onHourChange: (TextFieldValue) -> Unit,
    minute: TextFieldValue,
    onMinuteChange: (TextFieldValue) -> Unit,
    isPm: Boolean,
    onPeriodChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    Column(
        modifier.widthIn(max = 290.dp).fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TimeField(
                hour, onHourChange, R.string.hour_field, 1..12, ImeAction.Next,
                Modifier.weight(1f).focusRequester(focus),
            )
            TimeField(
                minute, onMinuteChange, R.string.minute_field, 0..59, ImeAction.Done, Modifier.weight(1f),
            )
        }
        SingleChoiceSegmentedButtonRow {
            for ((index, period) in listOf("AM", "PM").withIndex()) {
                val pm = index == 1
                SegmentedButton(
                    selected = isPm == pm,
                    onClick = { onPeriodChange(pm) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = 2),
                    icon = {},
                ) {
                    Text(period, fontFamily = ClockNumerals, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

@Composable
private fun TimeField(
    value: TextFieldValue,
    onChange: (TextFieldValue) -> Unit,
    label: Int,
    range: IntRange,
    imeAction: ImeAction,
    modifier: Modifier,
) {
    val number = value.text.toIntOrNull()
    val invalid = value.text.isNotEmpty() && (number == null || number !in range)
    OutlinedTextField(
        value = value,
        onValueChange = { input ->
            if (input.text.length <= 2 && input.text.all(Char::isDigit)) onChange(input)
        },
        modifier = modifier,
        label = { Text(stringResource(label)) },
        supportingText = if (invalid) {
            { Text(stringResource(R.string.time_field_range, range.first, range.last)) }
        } else null,
        isError = invalid,
        singleLine = true,
        textStyle = MaterialTheme.typography.headlineMedium.copy(
            fontFamily = ClockNumerals,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
        ),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = imeAction),
        shape = EntryShape,
    )
}
