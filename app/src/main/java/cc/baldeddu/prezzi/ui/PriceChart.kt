package cc.baldeddu.prezzi.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import cc.baldeddu.prezzi.domain.PricePoint
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
internal fun PriceChart(points: List<PricePoint>, basket: Boolean = false) {
    if (points.isEmpty()) {
        Text("Nessun prezzo da mostrare.", style = MaterialTheme.typography.bodySmall)
        return
    }
    var percentage by rememberSaveable { mutableStateOf(false) }
    var selection by rememberSaveable { mutableIntStateOf(-1) }
    val index = if (selection < 0) points.lastIndex else selection.coerceIn(points.indices)
    val selected = points[index]
    val accent = MaterialTheme.colorScheme.primary
    val grid = MaterialTheme.colorScheme.outlineVariant
    val values = remember(points, percentage) { points.map { if (percentage) it.changePercent else it.priceCents } }
    val low = values.min()
    val high = values.max()
    val range = (high - low).takeIf { it > 0 } ?: maxOf(abs(high) * 0.1, 1.0)
    val min = low - range * 0.12
    val max = high + range * 0.12
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = !percentage, onClick = { percentage = false }, label = { Text("Euro") })
            FilterChip(selected = percentage, onClick = { percentage = true }, label = { Text("Variazione %") })
        }
        Text(if (percentage) "Da ${percent(min)} a ${percent(max)}" else "Da ${euro(min)} a ${euro(max)}",
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        val firstDay = points.first().date.toEpochDay()
        val duration = (points.last().date.toEpochDay() - firstDay).coerceAtLeast(1).toFloat()
        Canvas(Modifier.fillMaxWidth().height(160.dp).semantics {
            contentDescription = "Grafico ${if (percentage) "della variazione percentuale" else "del costo in euro"}. ${points.size} date. Usa il selettore sotto per leggere ogni punto."
        }.pointerInput(points) {
            detectTapGestures { position ->
                val fraction = (position.x / size.width).coerceIn(0f, 1f)
                selection = points.indices.minBy { abs((points[it].date.toEpochDay() - firstDay) / duration - fraction) }
            }
        }) {
            val inset = 8.dp.toPx()
            val plotWidth = size.width - inset * 2
            val plotHeight = size.height - inset * 2
            repeat(4) { row ->
                val y = inset + row * plotHeight / 3
                drawLine(grid, Offset(inset, y), Offset(size.width - inset, y), 1.dp.toPx())
            }
            fun position(i: Int) = Offset(inset + (points[i].date.toEpochDay() - firstDay) / duration * plotWidth,
                inset + (1 - (values[i] - min) / (max - min)).toFloat() * plotHeight)
            val path = Path().apply {
                points.indices.forEach { i ->
                    val point = position(i)
                    if (i == 0) moveTo(point.x, point.y) else lineTo(point.x, point.y)
                }
            }
            drawPath(path, accent, style = Stroke(width = 2.5.dp.toPx()))
            val selectedPosition = position(index)
            drawLine(grid, Offset(selectedPosition.x, inset), Offset(selectedPosition.x, size.height - inset))
            drawCircle(accent, 5.dp.toPx(), selectedPosition)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(day(points.first().date), style = MaterialTheme.typography.labelSmall)
            if (points.size > 1) Text(day(points.last().date), style = MaterialTheme.typography.labelSmall)
        }
        if (points.size > 1) Slider(value = index.toFloat(), onValueChange = { selection = it.roundToInt() },
            valueRange = 0f..points.lastIndex.toFloat(), steps = (points.size - 2).coerceIn(0, 1000),
            modifier = Modifier.semantics { contentDescription = "Data del grafico" })
        Text("${day(selected.date)} · ${euro(selected.priceCents)} · ${percent(selected.changePercent)}",
            style = MaterialTheme.typography.bodyMedium)
        Text("${if (basket) "Effetto prezzi" else "Differenza dall’inizio"}: ${signedEuro(if (basket) selected.priceChangeCents else selected.differenceCents)}" +
            if (basket) "\n${selected.productCount} prodotti inclusi · ${selected.addedProductCount} nuovi in questa data" else "",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
