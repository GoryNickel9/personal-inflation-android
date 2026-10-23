package cc.baldeddu.prezzi.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import cc.baldeddu.prezzi.TrackerState
import cc.baldeddu.prezzi.domain.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PurchaseScreen(state: TrackerState, purchase: (PurchaseDraft) -> Unit, refresh: () -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var brand by rememberSaveable { mutableStateOf("") }
    var unit by rememberSaveable { mutableStateOf("kg") }
    var quantity by rememberSaveable { mutableStateOf("1") }
    var price by rememberSaveable { mutableStateOf("") }
    var date by rememberSaveable { mutableStateOf("") }
    var location by rememberSaveable { mutableStateOf("") }
    var note by rememberSaveable { mutableStateOf("") }
    var showErrors by rememberSaveable { mutableStateOf(false) }
    var pickerOpen by rememberSaveable { mutableStateOf(false) }
    var lastSaved by rememberSaveable { mutableIntStateOf(state.savedPurchases) }
    var today by remember { mutableStateOf(purchaseToday()) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        while (true) { today = purchaseToday(); delay(30_000) }
    }
    LaunchedEffect(state.savedPurchases) {
        if (state.savedPurchases > 0 && state.savedPurchases != lastSaved) {
            name = ""; brand = ""; unit = "kg"; quantity = "1"; price = ""; date = ""; location = ""; note = ""
            showErrors = false
            listState.scrollToItem(0)
        }
        lastSaved = state.savedPurchases
    }
    val matching = remember(state.data, name, brand) { state.data.findProduct(name, brand) }
    val draft = PurchaseDraft(name, brand, unit, quantity, price, date.ifEmpty { today.toString() }, location, note)
    val errors = if (showErrors) draft.errors(state.data, today) else emptyMap()
    val enabled = !state.busy
    if (pickerOpen) {
        val initial = runCatching { LocalDate.parse(draft.date) }.getOrDefault(today)
        val picker = rememberDatePickerState(initialSelectedDateMillis = initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                    Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneOffset.UTC).toLocalDate() <= purchaseToday()
                override fun isSelectableYear(year: Int): Boolean = year <= purchaseToday().year
            })
        DatePickerDialog(onDismissRequest = { pickerOpen = false }, confirmButton = {
            TextButton(onClick = {
                picker.selectedDateMillis?.let { date = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString() }
                pickerOpen = false
            }) { Text("Conferma") }
        }, dismissButton = { TextButton(onClick = { pickerOpen = false }) { Text("Annulla") } }) { DatePicker(picker) }
    }
    LazyColumn(state = listState, modifier = Modifier.widthIn(max = 760.dp).fillMaxSize(),
        contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { PageHeading("Registra un acquisto", "Il prodotto viene riconosciuto dal nome e dalla marca.", "UN PREZZO ALLA VOLTA") }
        item { StatusMessages(state, refresh) }
        if (errors.isNotEmpty()) item {
            Text("Correggi i campi indicati: ${errors.values.joinToString(" ")}", color = MaterialTheme.colorScheme.error)
        }
        item {
            SuggestionField("Prodotto", name, { name = it }, state.data.products.map { it.name }.distinct(),
                enabled, errors["name"], "Es. Latte intero")
        }
        item {
            SuggestionField("Marca (facoltativa)", brand, { brand = it }, state.data.products
                .filter { normalized(it.name) == normalized(name) }.map { it.brand }.filter { it.isNotBlank() }.distinct(),
                enabled, errors["brand"], "Es. Granarolo")
            Text("Marche diverse hanno storici separati. Lascia vuoto per i prodotti salvati senza marca.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (matching != null) item {
            Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.small) {
                Text("${matching.label} è già nel paniere. Aggiungo il prezzo per ${matching.unit} allo storico. Quantità di riferimento: ${quantity(matching.basketQuantity)} ${matching.unit}.", Modifier.padding(16.dp))
            }
        } else {
            item {
                Text("Unità di misura", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    listOf("kg", "L").forEach { option ->
                        FilterChip(selected = unit == option, onClick = { unit = option }, enabled = enabled, label = { Text("€/$option") })
                    }
                }
                errors["unit"]?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
            item {
                InputField("Quantità di riferimento ($unit)", quantity, { quantity = it }, enabled, errors["quantity"], KeyboardType.Decimal)
                Text("È il peso nel paniere, non la quantità di questo acquisto.", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item { InputField("Prezzo (€/${matching?.unit ?: unit})", price, { price = it }, enabled, errors["price"], KeyboardType.Decimal) }
        item {
            OutlinedTextField(value = draft.date, onValueChange = { date = it }, label = { Text("Giorno dell’acquisto") },
                modifier = Modifier.fillMaxWidth(), singleLine = true, enabled = enabled, isError = errors.containsKey("date"),
                supportingText = { Text(errors["date"] ?: "AAAA-MM-GG · fino a ${day(today)}, fuso Europe/Rome") },
                trailingIcon = { IconButton(onClick = { pickerOpen = true }, enabled = enabled) { Icon(Icons.Default.DateRange, "Scegli data") } })
        }
        item { InputField("Luogo (facoltativo)", location, { location = it }, enabled, errors["location"]) }
        item { InputField("Nota (facoltativa)", note, { note = it }, enabled, errors["note"]) }
        item {
            Button(onClick = {
                today = purchaseToday()
                val current = draft.copy(date = date.ifEmpty { today.toString() })
                showErrors = true
                if (current.errors(state.data, today).isEmpty()) {
                    keyboard?.hide()
                    purchase(current)
                } else scope.launch { listState.scrollToItem(0) }
            }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp), enabled = enabled && state.hasData) {
                if (state.busy) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(12.dp))
                }
                Text(if (state.busy) "Salvo l’acquisto…" else "Salva acquisto")
            }
        }
        item { Text("Il salvataggio richiede Internet e aggiorna anche il sito. Il prezzo deve essere già espresso per ${matching?.unit ?: unit}.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
private fun SuggestionField(label: String, value: String, onChange: (String) -> Unit, suggestions: List<String>,
    enabled: Boolean, error: String?, placeholder: String) {
    var expanded by remember { mutableStateOf(false) }
    val filtered = suggestions.filter { it.contains(value.trim(), ignoreCase = true) }
    Box {
        OutlinedTextField(value = value, onValueChange = onChange, label = { Text(label) },
            placeholder = { Text(placeholder) }, singleLine = true, enabled = enabled, isError = error != null,
            supportingText = error?.let { { Text(it) } }, modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            trailingIcon = { IconButton(onClick = { expanded = true }, enabled = enabled) {
                Icon(Icons.Default.KeyboardArrowDown, "Suggerimenti per $label")
            } })
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            if (filtered.isEmpty()) DropdownMenuItem(text = { Text("Nessun suggerimento. Puoi scrivere un valore nuovo.") }, onClick = {}, enabled = false)
            filtered.forEach { item -> DropdownMenuItem(text = { Text(item) }, onClick = { onChange(item); expanded = false }) }
        }
    }
}

@Composable
private fun InputField(label: String, value: String, onChange: (String) -> Unit, enabled: Boolean, error: String?,
    keyboardType: KeyboardType = KeyboardType.Text) {
    OutlinedTextField(value = value, onValueChange = onChange, label = { Text(label) }, enabled = enabled,
        singleLine = true, isError = error != null, supportingText = error?.let { { Text(it) } },
        modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = keyboardType,
            capitalization = if (keyboardType == KeyboardType.Text) KeyboardCapitalization.Sentences else KeyboardCapitalization.None))
}
