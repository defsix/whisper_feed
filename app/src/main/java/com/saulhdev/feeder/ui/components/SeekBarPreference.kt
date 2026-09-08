package com.saulhdev.feeder.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.saulhdev.feeder.data.content.FloatPref

@Composable
fun SeekBarPreference(
    modifier: Modifier = Modifier,
    pref: FloatPref,
    index: Int = 1,
    groupSize: Int = 1,
    isEnabled: Boolean = true,
    onValueChange: ((Float) -> Unit) = {},
) {
    var currentValue by remember(pref) { mutableFloatStateOf(pref.getValue()) }

    BasePreference(
        modifier = modifier,
        titleId = pref.titleId,
        summaryId = pref.summaryId,
        index = index,
        groupSize = groupSize,
        isEnabled = isEnabled,
        startWidget = {
            PreferenceIcon(
                icon = pref.icon,
                contentDescription = stringResource(id = pref.titleId),
            )
        },
        bottomWidget = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Slider(
                    modifier = Modifier
                        .requiredHeight(24.dp)
                        .weight(1f),
                    value = currentValue,
                    valueRange = pref.minValue..pref.maxValue,
                    steps = pref.steps,
                    onValueChange = { currentValue = it },
                    onValueChangeFinished = {
                        pref.setValue(currentValue)
                        onValueChange(currentValue)
                    },
                    enabled = isEnabled
                )
                Spacer(Modifier.width(12.dp))
                // Every FloatPref already carried a specialOutputs lambda saying
                // how to write its value down, and nothing read it: the sliders
                // were a bare track with no number anywhere, so "somewhere near
                // the left" was the only reading available. The value is the
                // whole point of a slider whose units are not obvious.
                Text(
                    text = pref.specialOutputs(currentValue),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isEnabled) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.End,
                    modifier = Modifier.widthIn(min = 56.dp),
                )
            }
        }
    )
}