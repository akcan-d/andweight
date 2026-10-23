package com.measured.app.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import com.measured.app.domain.WeightUnit
import com.measured.app.ui.theme.themedFilterChipColors

/** Metric vs Imperial is the one real choice (it also decides cm vs ft/in for height) - stones is
 * just a different way to show an Imperial weight, not a third system standing alongside Metric
 * and Imperial, so it only appears as a sub-choice once Imperial is picked rather than sitting in
 * the main row as if it were equivalent to the other two. Picking Imperial for the first time
 * defaults to pounds; switching back to pounds or stones later doesn't touch the Metric/Imperial
 * choice itself. */
@Composable
fun WeightUnitPicker(selected: WeightUnit, onChange: (WeightUnit) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = selected == WeightUnit.KG,
                onClick = { onChange(WeightUnit.KG) },
                label = { Text("Metric (kg, cm)") },
                colors = themedFilterChipColors(),
            )
            FilterChip(
                selected = selected != WeightUnit.KG,
                onClick = { if (selected == WeightUnit.KG) onChange(WeightUnit.LB) },
                label = { Text("Imperial (lb/st, ft/in)") },
                colors = themedFilterChipColors(),
            )
        }
        if (selected != WeightUnit.KG) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = selected == WeightUnit.LB,
                    onClick = { onChange(WeightUnit.LB) },
                    label = { Text("Pounds (lb)") },
                    colors = themedFilterChipColors(),
                )
                FilterChip(
                    selected = selected == WeightUnit.ST,
                    onClick = { onChange(WeightUnit.ST) },
                    label = { Text("Stones (st)") },
                    colors = themedFilterChipColors(),
                )
            }
        }
    }
}
