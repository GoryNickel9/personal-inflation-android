package cc.baldeddu.prezzi.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cc.baldeddu.prezzi.TrackerState
import cc.baldeddu.prezzi.R
import cc.baldeddu.prezzi.TrackerViewModel
import cc.baldeddu.prezzi.domain.*
import cc.baldeddu.prezzi.update.UpdateViewModel

private enum class Screen(val label: String) { Overview("Riepilogo"), Basket("Paniere"), Purchase("Acquisto") }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrezziApp(model: TrackerViewModel, updates: UpdateViewModel? = null) = PrezziTheme {
    val state by model.state.collectAsStateWithLifecycle()
    var screen by rememberSaveable { mutableStateOf(Screen.Overview) }
    val stateHolder = rememberSaveableStateHolder()
    val refresh = { if (!state.loading && !state.busy) model.refresh(force = true) }
    val lifecycle = LocalLifecycleOwner.current
    DisposableEffect(lifecycle, model, updates) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) { model.refresh(); updates?.check() } }
        lifecycle.lifecycle.addObserver(observer)
        onDispose { lifecycle.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(screen) { model.refresh() }
    BackHandler(enabled = screen != Screen.Overview) { screen = Screen.Overview }

    Scaffold(
        topBar = {
            Surface {
                Row(Modifier.fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                    .padding(horizontal = 16.dp, vertical = 8.dp).heightIn(min = 64.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.small) {
                        Icon(Icons.Default.ShoppingCart, null, Modifier.padding(10.dp), tint = MaterialTheme.colorScheme.primary)
                    }
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text("IL TUO PANIERE PERSONALE", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (updates != null) UpdateControls(updates)
                }
            }
        },
        bottomBar = {
            NavigationBar {
                Screen.entries.forEach { destination ->
                    NavigationBarItem(selected = screen == destination, onClick = { screen = destination },
                        icon = { Icon(when (destination) {
                            Screen.Overview -> Icons.Default.Home
                            Screen.Basket -> Icons.Default.ShoppingCart
                            Screen.Purchase -> Icons.Default.Add
                        }, null) }, label = { Text(destination.label) })
                }
            }
        },
    ) { padding ->
        PullToRefreshBox(isRefreshing = state.loading, onRefresh = refresh,
            modifier = Modifier.fillMaxSize().padding(padding).imePadding().testTag("pull-refresh").semantics {
                customActions = if (state.loading || state.busy) emptyList()
                else listOf(CustomAccessibilityAction("Aggiorna pagina") { refresh(); true })
            }, contentAlignment = Alignment.TopCenter) {
            stateHolder.SaveableStateProvider(screen.name) {
                when (screen) {
                    Screen.Overview -> Overview(state, { model.refresh(true) }, { screen = Screen.Purchase })
                    Screen.Basket -> BasketScreen(state, { model.refresh(true) }, model::delete, { screen = Screen.Purchase })
                    Screen.Purchase -> PurchaseScreen(state, model::purchase, { model.refresh(true) })
                }
            }
        }
    }
}

@Composable
internal fun PageHeading(title: String, subtitle: String, kicker: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (kicker != null) Text(kicker, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Text(title, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.semantics { heading() })
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun StatusMessages(state: TrackerState, refresh: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (state.loading) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            Text(if (state.hasData) "Aggiorno i prezzi…" else "Carico il tuo paniere…", style = MaterialTheme.typography.bodySmall)
        }
        state.error?.let {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                Column(Modifier.padding(16.dp)) {
                    Text(it, color = MaterialTheme.colorScheme.onErrorContainer)
                    TextButton(onClick = refresh, enabled = !state.loading && !state.busy) { Text("Riprova ad aggiornare") }
                }
            }
        }
        state.notice?.let {
            Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.small) {
                Text(it, Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium)
            }
        }
        if (state.hasData && state.fetchedAt != null) {
            Text((if (state.offline) "Dati salvati sul dispositivo · " else "Ultimo aggiornamento · ") + updatedAt(state.fetchedAt),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun Overview(state: TrackerState, refresh: () -> Unit, add: () -> Unit) {
    val metrics = remember(state.data) { comparisonWindows.map { inflationFor(state.data, it) } }
    LazyColumn(Modifier.widthIn(max = 760.dp).fillMaxSize(), contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { PageHeading("Il tuo costo\ndella vita", "Segui come cambiano i prezzi dei prodotti che acquisti.", "IL RIEPILOGO") }
        item { StatusMessages(state, refresh) }
        if (state.hasData) {
            if (state.data.products.isEmpty()) item { EmptyBasket(add) }
            else {
                item {
                    Text("Ogni scheda confronta il costo medio di due periodi consecutivi. Si parte dall’ultimo acquisto${state.data.anchorDate?.let { " (${day(it)})" } ?: ""}. Sono inclusi solo i prodotti con prezzi in entrambi i periodi.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                items(metrics, key = { it.window.days }) { metric -> MetricCard(metric) }
            }
        }
        item { MethodNote() }
    }
}

@Composable
private fun MetricCard(metric: WindowMetric) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(metric.window.label, style = MaterialTheme.typography.titleMedium)
                Text("${metric.window.days} GIORNI", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            val difference = metric.differenceCents
            Text(difference?.let(::signedEuro) ?: "—", style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.SemiBold, color = if (difference != null && difference > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
            Text((metric.rate?.let { percent(it * 100) } ?: "Percentuale non disponibile") + " · sul paniere", style = MaterialTheme.typography.bodyMedium)
            if (metric.matched > 0) {
                Text("${metric.matched} ${if (metric.matched == 1) "prodotto confrontato" else "prodotti confrontati"} · ${euro(metric.priorCost)} → ${euro(metric.currentCost)}",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else Text("Servono prezzi in entrambi i periodi.", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (metric.anchor != null) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Text("Recente: ${day(metric.currentStart!!)} – ${day(metric.anchor)}\nPrecedente: ${day(metric.priorStart!!)} – ${day(metric.priorEnd!!)}",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
internal fun EmptyBasket(add: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Default.ShoppingCart, null, tint = MaterialTheme.colorScheme.primary)
            Text("Il paniere è ancora vuoto", style = MaterialTheme.typography.titleLarge)
            Text("Registra un prodotto con il prezzo e il giorno del primo acquisto.")
            Button(onClick = add) { Text("Registra un acquisto") }
        }
    }
}

@Composable
internal fun MethodNote() {
    Text("La misura dipende dai prezzi registrati e dalle quantità del tuo paniere. Non è l’indice ufficiale ISTAT.",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
