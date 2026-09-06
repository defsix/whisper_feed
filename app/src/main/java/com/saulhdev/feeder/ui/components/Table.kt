package com.saulhdev.feeder.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp

@Composable
fun Table(
    rows: List<List<TableCell>>
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        shape = MaterialTheme.shapes.small,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier.padding(1.dp)
        ) {
            rows.forEachIndexed { ri, row ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min)
                ) {
                    row.forEachIndexed { ci, cell ->
                        Box(
                            modifier = Modifier
                                .weight(cell.colspan.toFloat())
                                .height(IntrinsicSize.Max),
                        ) {
                            Text(
                                text = cell.content,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                        if (ci < row.size - 1)
                            VerticalDivider(
                                modifier = Modifier
                                    .fillMaxHeight(),
                                thickness = 1.dp,
                                color = MaterialTheme.colorScheme.outline
                            )
                    }
                }
                if (ri < rows.size - 1)
                    HorizontalDivider(
                        modifier = Modifier.fillMaxWidth(),
                        thickness = 1.dp,
                        color = MaterialTheme.colorScheme.outline
                    )
            }
        }
    }
}

data class TableCell(
    val content: AnnotatedString,
    val colspan: Int = 1,
    val rowspan: Int = 1,
)