package com.saulhdev.feeder.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
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
            Icon(
                imageVector = pref.icon,
                contentDescription = stringResource(id = pref.titleId),
                tint = MaterialTheme.colorScheme.onSurface,
            )
        },
        bottomWidget = {
            Row {
                Slider(
                    modifier = Modifier
                        .requiredHeight(24.dp)
                        .weight(1f),
                    value = currentValue,
                    valueRange = pref.minValue..pref.maxValue,
                    onValueChange = { currentValue = it },
                    onValueChangeFinished = {
                        pref.setValue(currentValue)
                        onValueChange(currentValue)
                    },
                    enabled = isEnabled
                )
            }
        }
    )
}