package cc.baldeddu.prezzi.domain

import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

data class Product(val id: Int, val name: String, val brand: String, val unit: String, val basketQuantity: Double) {
    val label: String get() = if (brand.isBlank()) name else "$name · $brand"
}

data class Observation(
    val id: Int,
    val productId: Int,
    val unitPriceCents: Int,
    val observedOn: LocalDate,
    val location: String? = null,
    val note: String? = null,
)

data class TrackerData(val products: List<Product> = emptyList(), val observations: List<Observation> = emptyList()) {
    val anchorDate: LocalDate? get() = observations.maxOfOrNull { it.observedOn }
    fun entries(productId: Int): List<Observation> = observations.filter { it.productId == productId }
        .sortedWith(compareByDescending<Observation> { it.observedOn }.thenByDescending { it.id })
    fun findProduct(name: String, brand: String): Product? = products.firstOrNull {
        normalized(it.name) == normalized(name) && normalized(it.brand) == normalized(brand)
    }
}

fun normalized(value: String): String = value.trim().lowercase(Locale.ITALIAN)
fun purchaseToday(clock: Clock = Clock.systemUTC()): LocalDate = LocalDate.now(clock.withZone(ZoneId.of("Europe/Rome")))

fun priceInCents(value: String): Int? {
    val raw = value.replace(',', '.')
    if (!Regex("\\d{1,7}(?:\\.\\d{1,2})?").matches(raw)) return null
    val parts = raw.split('.')
    val amount = parts[0].toLong() * 100 + parts.getOrElse(1) { "" }.padEnd(2, '0').toInt()
    return amount.takeIf { it in 1..100_000_000 }?.toInt()
}

data class PurchaseDraft(
    val name: String = "", val brand: String = "", val unit: String = "kg", val quantity: String = "1",
    val price: String = "", val date: String = "", val location: String = "", val note: String = "",
) {
    fun errors(data: TrackerData, today: LocalDate = purchaseToday()): Map<String, String> = buildMap {
        if (name.trim().isEmpty() || name.trim().length > 80) put("name", "Inserisci un nome, massimo 80 caratteri.")
        if (brand.trim().length > 80) put("brand", "Usa al massimo 80 caratteri.")
        val existing = data.findProduct(name, brand)
        if (existing == null) {
            if (unit !in listOf("kg", "L")) put("unit", "Scegli kg o L.")
            val amount = quantity.replace(',', '.').toDoubleOrNull()
            if (amount == null || !amount.isFinite() || amount <= 0 || amount > 100_000) {
                put("quantity", "Inserisci una quantità maggiore di 0 e fino a 100.000.")
            }
        }
        if (priceInCents(price) == null) put("price", "Da 0,01 € a 1.000.000 €, con massimo due decimali.")
        val day = runCatching { LocalDate.parse(date) }.getOrNull()
        if (day == null || !Regex("\\d{4}-\\d{2}-\\d{2}").matches(date)) put("date", "Inserisci una data valida (AAAA-MM-GG).")
        else if (day > today) put("date", "La data non può essere futura.")
        if (location.trim().length > 100) put("location", "Usa al massimo 100 caratteri.")
        if (note.trim().length > 120) put("note", "Usa al massimo 120 caratteri.")
    }
}
