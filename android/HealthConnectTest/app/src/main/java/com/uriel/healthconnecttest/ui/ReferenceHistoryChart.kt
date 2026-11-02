package com.uriel.healthconnecttest.ui

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.ceil

@Composable
internal fun ReferenceHistoryChart(points: List<Pair<LocalDate, Double?>>, unit: String, goal: Double = 0.0) {
    val primary = MaterialTheme.colorScheme.primary
    val grid = MaterialTheme.colorScheme.outlineVariant
    val labels = MaterialTheme.colorScheme.onSurfaceVariant
    val highest = maxOf(points.maxOfOrNull { it.second ?: 0.0 } ?: 0.0, goal, 1.0)
    val step = when { highest > 1000 -> 500.0; highest > 100 -> 50.0; highest > 10 -> 10.0; else -> 1.0 }
    val maximum = ceil(highest * 1.12 / step) * step
    Canvas(Modifier.fillMaxWidth().height(200.dp).semantics {
        contentDescription = "Daily $unit chart across ${points.size} completed days. Missing data is marked separately. Values are available below."
    }) {
        val left = 38.dp.toPx(); val bottom = 25.dp.toPx()
        val chartWidth = size.width - left; val height = size.height - bottom - 8.dp.toPx()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = labels.toArgb(); textSize = 10.sp.toPx() }
        for (index in 0..5) {
            val value = maximum * index / 5
            val y = height * (1 - index / 5f) + 5.dp.toPx()
            drawLine(grid, Offset(left, y), Offset(size.width, y), 1.dp.toPx())
            val text = if (maximum >= 100) value.toInt().toString() else statisticAmount(value)
            drawContext.canvas.nativeCanvas.drawText(text, 0f, y + 3.dp.toPx(), paint)
        }
        if (goal > 0) {
            val y = height * (1 - goal / maximum).toFloat() + 5.dp.toPx()
            drawLine(primary, Offset(left, y), Offset(size.width, y), 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10.dp.toPx(), 6.dp.toPx())))
        }
        val width = chartWidth / points.size.coerceAtLeast(1)
        points.forEachIndexed { index, point ->
            val value = point.second
            val barHeight = if (value == null) 2.dp.toPx() else maxOf((value / maximum * height).toFloat(), 2.dp.toPx())
            val x = left + index * width + 3.dp.toPx()
            drawRoundRect(if (value == null) SolidColor(grid) else Brush.verticalGradient(listOf(primary.copy(alpha = .55f), primary)),
                Offset(x, height + 5.dp.toPx() - barHeight), Size(maxOf(width - 6.dp.toPx(), 1f), barHeight), CornerRadius(3.dp.toPx()))
            if (points.size <= 7 || index % maxOf(points.size / 5, 1) == 0 || index == points.lastIndex) {
                val text = point.first.format(DateTimeFormatter.ofPattern(if (points.size <= 7) "EEE" else "M/d"))
                val measured = paint.measureText(text)
                drawContext.canvas.nativeCanvas.drawText(text, (left + index * width + width / 2 - measured / 2).coerceIn(left, size.width - measured), size.height - 4.dp.toPx(), paint)
            }
        }
    }
}
