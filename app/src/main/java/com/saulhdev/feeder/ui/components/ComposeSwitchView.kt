package com.saulhdev.feeder.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

@Composable
fun ComposeSwitchView(
    @StringRes titleId: Int,
    modifier: Modifier = Modifier,
    @StringRes summaryId: Int = -1,
    icon: ImageVector? = null,
    onCheckedChange: ((Boolean) -> Unit),
    isChecked: Boolean = false,
    isEnabled: Boolean = true,
    index: Int = -1,
    groupSize: Int = -1,
) {
    val (checked, check) = remember(isChecked) { mutableStateOf(isChecked) }
    BasePreference(
        modifier = modifier,
        titleId = titleId,
        summaryId = summaryId,
        index = index,
        groupSize = groupSize,
        startWidget = icon?.let {
            {
                Icon(
                    imageVector = icon,
                    contentDescription = stringResource(id = titleId),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
        },
        isEnabled = isEnabled,
        onClick = {
            check(!checked)
            onCheckedChange(!checked)
        },
        endWidget = {
            Switch(
                modifier = Modifier
                    .height(24.dp),
                checked = checked,
                onCheckedChange = {
                    check(it)
                    onCheckedChange(it)
                },
                enabled = isEnabled,
            )
        }
    )
}