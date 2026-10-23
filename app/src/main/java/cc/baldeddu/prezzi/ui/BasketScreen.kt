package cc.baldeddu.prezzi.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import cc.baldeddu.prezzi.TrackerState
import cc.baldeddu.prezzi.domain.*

@Composable
internal fun BasketScreen(state: TrackerState, refresh: () -> Unit, delete: (Product) -> Unit, add: () -> Unit) {
    var deleteId by rememberSaveable { mutableStateOf<Int?>(null) }
    val toDelete = state.data.products.firstOrNull { it.id == deleteId }
    if (toDelete != null) AlertDialog(onDismissRequest = { deleteId = null },
        title = { Text("Rimuovere il prodotto?") },
        text = { Text("${toDelete.label} e tutti i suoi prezzi saranno rimossi anche dal sito. L’operazione non si può annullare.") },
        confirmButton = { TextButton(onClick = { delete(toDelete); deleteId = null }, enabled = !state.busy) { Text("Rimuovi") } },
        dismissButton = { TextButton(onClick = { deleteId = null }) { Text("Annulla") } })
    val points = remember(state.data) { basketHistory(state.data) }
    val annual = remember(points) { yearOverYear(points, basket = true) }
    val excluded = remember(state.data) { state.data.products.count { product -> state.data.observations.none { it.productId == product.id } } }
    LazyColumn(Modifier.widthIn(max = 760.dp).fillMaxSize(), contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { PageHeading("Il tuo paniere", "I prodotti che segui e i prezzi registrati nel tempo.", "PREZZI & STORICO") }
        item { StatusMessages(state, refresh) }
        if (state.hasData) {
            if (state.data.products.isEmpty()) item { EmptyBasket(add) }
            else {
                item {
                    Card {
                        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("Andamento del paniere", style = MaterialTheme.typography.titleLarge)
                            points.lastOrNull()?.let { last ->
                                Text(euro(last.priceCents), style = MaterialTheme.typography.headlineLarge)
                                Text("${percent(last.changePercent)} dall’inizio · effetto prezzi ${signedEuro(last.priceChangeCents)}",
                                    color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium)
                                Text("${last.productCount} prodotti con un prezzo noto al ${day(last.date)}",
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(annual?.let { "Su base annua (YoY): ${percent(it.percent)} · ${signedEuro(it.differenceCents)}\nConfronto con il ${day(it.priorDate)}" }
                                ?: "Su base annua (YoY): serve almeno un anno di storico.", style = MaterialTheme.typography.bodySmall)
                            PriceChart(points, basket = true)
                            Text("Il costo include i nuovi prodotti dalla loro prima data. La percentuale e l’effetto prezzi escludono il loro costo d’ingresso. I prezzi noti restano validi fino alla registrazione successiva.",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (excluded > 0) Text("$excluded prodotti senza prezzi esclusi dal grafico.", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                item {
                    Text("${state.data.products.size} prodotti · ${state.data.observations.size} prezzi registrati", style = MaterialTheme.typography.labelLarge)
                }
                items(state.data.products, key = { it.id }) { product ->
                    ProductCard(product, state.data, !state.busy) { deleteId = product.id }
                }
                item { Text("La variazione accanto al prodotto confronta gli ultimi due acquisti, ordinati per data e registrazione.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
        item { MethodNote() }
    }
}

@Composable
private fun ProductCard(product: Product, data: TrackerData, enabled: Boolean, delete: () -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    var historyExpanded by rememberSaveable { mutableStateOf(false) }
    var unitBasis by rememberSaveable { mutableStateOf(false) }
    val entries = remember(data, product.id) { data.entries(product.id) }
    val points = remember(product, entries, unitBasis) { productHistory(product, entries, unitBasis) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text(product.name, style = MaterialTheme.typography.titleMedium)
                    if (product.brand.isNotBlank()) Text(product.brand, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${quantity(product.basketQuantity)} ${product.unit} nel paniere", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        "${if (expanded) "Nascondi" else "Mostra"} grafico di ${product.label}")
                }
                IconButton(onClick = delete, enabled = enabled) {
                    Icon(Icons.Default.Delete, "Rimuovi ${product.label}", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            entries.firstOrNull()?.let { latest ->
                Text("${euro(latest.unitPriceCents.toDouble())}/${product.unit}", style = MaterialTheme.typography.titleLarge)
                Text(day(latest.observedOn) + (latest.location?.let { " · $it" } ?: ""), style = MaterialTheme.typography.bodySmall)
                entries.getOrNull(1)?.let { previous ->
                    val change = (latest.unitPriceCents.toDouble() / previous.unitPriceCents - 1) * 100
                    Text("${percent(change)} rispetto all’acquisto precedente", style = MaterialTheme.typography.bodySmall,
                        color = if (change > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary)
                }
            } ?: Text("Nessun prezzo registrato")
            if (expanded) {
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                Row {
                    Text("Prezzo per ${product.unit}", Modifier.weight(1f).padding(top = 12.dp))
                    Switch(checked = unitBasis, onCheckedChange = { unitBasis = it },
                        modifier = Modifier.semantics { contentDescription = "Usa il prezzo unitario di ${product.label}" })
                }
                Text(if (unitBasis) "Prezzo unitario" else "Costo di ${quantity(product.basketQuantity)} ${product.unit}",
                    style = MaterialTheme.typography.bodySmall)
                PriceChart(points)
            }
            TextButton(onClick = { historyExpanded = !historyExpanded }) {
                Text("${if (historyExpanded) "Nascondi storico" else "Storico prezzi"} (${entries.size})")
            }
            if (historyExpanded) entries.forEach { entry ->
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Text("${day(entry.observedOn)} · ${euro(entry.unitPriceCents.toDouble())}/${product.unit}", style = MaterialTheme.typography.bodyMedium)
                entry.location?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                entry.note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
    }
}
