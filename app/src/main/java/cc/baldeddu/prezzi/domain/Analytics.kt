package cc.baldeddu.prezzi.domain

import java.time.LocalDate

data class ComparisonWindow(val days: Int, val label: String)
val comparisonWindows = listOf(
    ComparisonWindow(30, "1 mese"), ComparisonWindow(90, "3 mesi"), ComparisonWindow(180, "6 mesi"),
    ComparisonWindow(365, "12 mesi"), ComparisonWindow(730, "24 mesi"),
    ComparisonWindow(1095, "36 mesi"), ComparisonWindow(1825, "60 mesi"),
)

data class WindowMetric(
    val window: ComparisonWindow, val anchor: LocalDate?, val matched: Int,
    val currentCost: Double, val priorCost: Double,
) {
    val rate: Double? get() = if (matched > 0 && priorCost > 0) currentCost / priorCost - 1 else null
    val differenceCents: Double? get() = if (matched > 0) currentCost - priorCost else null
    val currentStart: LocalDate? get() = anchor?.plusDays(1)?.minusDays(window.days.toLong())
    val priorStart: LocalDate? get() = currentStart?.minusDays(window.days.toLong())
    val priorEnd: LocalDate? get() = currentStart?.minusDays(1)
}

// Adjacent, half-open windows include the anchor day. Each observation has equal weight.
fun inflationFor(data: TrackerData, window: ComparisonWindow): WindowMetric {
    val anchor = data.anchorDate ?: return WindowMetric(window, null, 0, 0.0, 0.0)
    val end = anchor.plusDays(1)
    val start = end.minusDays(window.days.toLong())
    val priorStart = start.minusDays(window.days.toLong())
    var currentCost = 0.0
    var priorCost = 0.0
    var matched = 0
    val grouped = data.observations.groupBy { it.productId }
    for (product in data.products) {
        val entries = grouped[product.id].orEmpty()
        val current = entries.filter { it.observedOn >= start && it.observedOn < end }
        val prior = entries.filter { it.observedOn >= priorStart && it.observedOn < start }
        if (current.isEmpty() || prior.isEmpty()) continue
        currentCost += current.map { it.unitPriceCents }.average() * product.basketQuantity
        priorCost += prior.map { it.unitPriceCents }.average() * product.basketQuantity
        matched++
    }
    return WindowMetric(window, anchor, matched, currentCost, priorCost)
}

data class PricePoint(
    val date: LocalDate, val priceCents: Double, val changePercent: Double, val differenceCents: Double,
    val productCount: Int = 1, val addedProductCount: Int = 0, val priceIndex: Double = 1.0,
    val priceChangeCents: Double = differenceCents,
)

private fun dailyPrices(productId: Int, observations: List<Observation>): List<Observation> = observations
    .filter { it.productId == productId }.groupBy { it.observedOn }
    .values.map { day -> day.maxBy { it.id } }.sortedBy { it.observedOn }

fun productHistory(product: Product, observations: List<Observation>, unitBasis: Boolean = false): List<PricePoint> {
    val entries = dailyPrices(product.id, observations)
    val quantity = if (unitBasis) 1.0 else product.basketQuantity
    val baseline = (entries.firstOrNull()?.unitPriceCents ?: return emptyList()) * quantity
    return entries.map {
        val price = it.unitPriceCents * quantity
        PricePoint(it.observedOn, price, if (baseline > 0) (price / baseline - 1) * 100 else 0.0, price - baseline)
    }
}

// Port of lib/price-history.ts. A new product enters the next price-index link.
fun basketHistory(data: TrackerData): List<PricePoint> {
    val histories = data.products.map { it to dailyPrices(it.id, data.observations) }.filter { it.second.isNotEmpty() }
    val dates = histories.flatMap { it.second.map(Observation::observedOn) }.distinct().sorted()
    val positions = IntArray(histories.size) { -1 }
    var index = 1.0
    var priceEffect = 0.0
    var baseline = 0.0
    return dates.mapIndexed { dayIndex, date ->
        var total = 0.0
        var prior = 0.0
        var current = 0.0
        var count = 0
        var added = 0
        histories.forEachIndexed { i, (product, entries) ->
            val previous = positions[i]
            while (positions[i] + 1 < entries.size && entries[positions[i] + 1].observedOn <= date) positions[i]++
            if (positions[i] >= 0) {
                val cost = entries[positions[i]].unitPriceCents * product.basketQuantity
                total += cost
                count++
                if (previous < 0) {
                    if (dayIndex > 0) added++
                } else {
                    prior += entries[previous].unitPriceCents * product.basketQuantity
                    current += cost
                }
            }
        }
        if (dayIndex == 0) baseline = total
        if (prior > 0) index *= current / prior
        priceEffect += current - prior
        PricePoint(date, total, (index - 1) * 100, total - baseline, count, added, index, priceEffect)
    }
}

data class AnnualChange(val target: LocalDate, val priorDate: LocalDate, val percent: Double, val differenceCents: Double)

fun yearOverYear(points: List<PricePoint>, basket: Boolean = false): AnnualChange? {
    val latest = points.lastOrNull() ?: return null
    // LocalDate.minusYears maps February 29 to February 28 automatically.
    val target = latest.date.minusYears(1)
    val prior = points.lastOrNull { it.date <= target } ?: return null
    if (prior.priceCents <= 0) return null
    return AnnualChange(
        target, prior.date,
        if (basket) (latest.priceIndex / prior.priceIndex - 1) * 100 else (latest.priceCents / prior.priceCents - 1) * 100,
        if (basket) latest.priceChangeCents - prior.priceChangeCents else latest.priceCents - prior.priceCents,
    )
}
