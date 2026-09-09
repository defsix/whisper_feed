package com.saulhdev.feeder.ui.components

import androidx.compose.foundation.layout.height
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.saulhdev.feeder.R
import com.saulhdev.feeder.data.content.BooleanPref
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun SwitchPreference(
    modifier: Modifier = Modifier,
    pref: BooleanPref,
    index: Int = 1,
    groupSize: Int = 1,
    isEnabled: Boolean = true,
    onCheckedChange: ((Boolean) -> Unit) = {},
) {
    val (checked, check) = remember(pref) { mutableStateOf(pref.getValue()) }
    val scope = rememberCoroutineScope()

    val set: (Boolean) -> Unit = { value ->
        onCheckedChange(value)
        check(value)
        // Off the main thread: setValue is a blocking DataStore write and this
        // is called straight from a tap.
        scope.launch(Dispatchers.IO) { pref.setValue(value) }
    }

    BasePreference(
        modifier = modifier,
        titleId = pref.titleId,
        summaryId = pref.summaryId,
        index = index,
        groupSize = groupSize,
        startWidget = {
            PreferenceIcon(
                icon = pref.icon,
                contentDescription = null,
            )
        },
        isEnabled = isEnabled,
        onClick = { set(!checked) },
        // One target, not two. The row was clickable and the switch inside it
        // was independently clickable, so a screen reader found two controls
        // for one setting: a "button" with no state and a bare "switch" with
        // no name. The row is the switch now, and it says which way it is set.
        role = Role.Switch,
        stateDescription = stringResource(
            if (checked) R.string.state_on else R.string.state_off
        ),
        endWidget = {
            Switch(
                modifier = Modifier.height(24.dp),
                checked = checked,
                // Not independently focusable; the row carries the action.
                onCheckedChange = null,
                enabled = isEnabled,
            )
        }
    )
}
