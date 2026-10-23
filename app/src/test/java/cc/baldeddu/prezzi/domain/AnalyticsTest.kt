package cc.baldeddu.prezzi.domain

import org.junit.Assert.*
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class AnalyticsTest {
    private val product = Product(1, "Latte", "Marca", "L", 2.0)
    private fun entry(id: Int, date: String, cents: Int, productId: Int = 1) = Observation(id, productId, cents, LocalDate.parse(date))

    @Test fun `all windows include the anchor and have disjoint boundaries`() {
        val anchor = LocalDate.parse("2026-09-30")
        comparisonWindows.forEach { window ->
            val start = anchor.plusDays(1).minusDays(window.days.toLong())
            val priorStart = start.minusDays(window.days.toLong())
            val data = TrackerData(listOf(product), listOf(
                entry(1, priorStart.toString(), 100), entry(2, start.toString(), 200),
                entry(3, anchor.toString(), 300), entry(4, priorStart.minusDays(1).toString(), 99999)))
            val metric = inflationFor(data, window)
            assertEquals(200.0, metric.priorCost, 1e-9)
            assertEquals(500.0, metric.currentCost, 1e-9)
            assertEquals(300.0, metric.differenceCents!!, 1e-9)
            assertEquals(1.5, metric.rate!!, 1e-9)
            assertEquals(start.minusDays(1), metric.priorEnd)
        }
        assertEquals(listOf(30, 90, 180, 365, 730, 1095, 1825), comparisonWindows.map { it.days })
    }

    @Test fun `different brands do not fill missing windows`() {
        val data = TrackerData(listOf(product, product.copy(id = 2, brand = "Altra")), listOf(
            entry(1, "2026-09-30", 200), entry(2, "2026-08-01", 100, 2)))
        assertNull(inflationFor(data, comparisonWindows.first()).rate)
        assertNull(inflationFor(TrackerData(), comparisonWindows.first()).differenceCents)
    }

    @Test fun `daily charts use highest id but comparisons average every observation`() {
        val data = TrackerData(listOf(product), listOf(entry(4, "2026-09-30", 300), entry(3, "2026-09-30", 100), entry(1, "2026-08-20", 100)))
        assertEquals(600.0, productHistory(product, data.observations).last().priceCents, 1e-9)
        assertEquals(400.0, inflationFor(data, comparisonWindows.first()).currentCost, 1e-9)
        assertEquals(listOf(4, 3, 1), data.entries(1).map { it.id })
    }

    @Test fun `admission does not create price inflation and next link includes admitted products`() {
        val first = product.copy(basketQuantity = 1.0)
        val data = TrackerData(listOf(first, first.copy(id = 2)), listOf(
            entry(1, "2025-01-01", 100), entry(2, "2025-02-01", 110),
            entry(3, "2025-02-01", 10000, 2), entry(4, "2025-03-01", 9000, 2)))
        val points = basketHistory(data)
        assertEquals(10110.0, points[1].priceCents, 1e-9)
        assertEquals(1.1, points[1].priceIndex, 1e-9)
        assertEquals(10.0, points[1].priceChangeCents, 1e-9)
        assertEquals(1, points[1].addedProductCount)
        assertEquals(1.1 * 9110 / 10110, points[2].priceIndex, 1e-9)
        assertEquals(-990.0, points[2].priceChangeCents, 1e-9)
    }

    @Test fun `fractional reference quantities do not round before computing changes`() {
        val fractional = product.copy(basketQuantity = 0.06)
        val entries = listOf(entry(2, "2026-09-22", 6150), entry(1, "2026-07-28", 5800))
        val points = productHistory(fractional, entries)
        assertEquals(348.0, points[0].priceCents, 1e-9)
        assertEquals(21.0, points[1].differenceCents, 1e-9)
        assertEquals(6.03448275862069, points[1].changePercent, 1e-9)
        assertEquals(6150.0, productHistory(fractional, entries, true)[1].priceCents, 1e-9)
    }

    @Test fun `annual comparison uses last point before anniversary with leap day clamp`() {
        val first = product.copy(basketQuantity = 1.0)
        val points = basketHistory(TrackerData(listOf(first, first.copy(id = 2)), listOf(
            entry(1, "2023-02-28", 100), entry(2, "2023-03-01", 9000, 2), entry(3, "2024-02-29", 200))))
        val annual = yearOverYear(points, basket = true)!!
        assertEquals(LocalDate.parse("2023-02-28"), annual.target)
        assertEquals(100.0, annual.differenceCents, 1e-9)
        assertEquals((9200.0 / 9100 - 1) * 100, annual.percent, 1e-9)
        assertNull(yearOverYear(points.take(2), true))
        assertNull(yearOverYear(emptyList()))
    }

    @Test fun `new products leave every earlier basket point unchanged`() {
        val first = product.copy(basketQuantity = 1.0)
        val old = TrackerData(listOf(first), listOf(entry(1, "2024-01-01", 100), entry(2, "2025-01-01", 110)))
        val before = basketHistory(old)
        val after = basketHistory(old.copy(products = old.products + first.copy(id = 2),
            observations = old.observations + entry(3, "2026-01-01", 10000, 2)))
        assertEquals(before, after.take(2))
        assertEquals(0.0, yearOverYear(after, true)!!.percent, 1e-9)
    }

    @Test fun `orphan observations do not create chart dates and products without prices are absent`() {
        val data = TrackerData(listOf(product, product.copy(id = 2)), listOf(entry(1, "2026-01-01", 100), entry(2, "2026-02-01", 200, 99)))
        assertEquals(1, basketHistory(data).size)
        assertTrue(basketHistory(TrackerData()).isEmpty())
    }

    @Test fun `price parser uses integer cents and enforces server bounds`() {
        mapOf("0,01" to 1, "12.30" to 1230, "1000000" to 100000000).forEach { (raw, expected) -> assertEquals(expected, priceInCents(raw)) }
        listOf("0", "-1", "1.234", "NaN", "1e2", "1000000.01", " 1", "1,2,3", "").forEach { assertNull(it, priceInCents(it)) }
    }

    @Test fun `identity trims folds case and keeps brands separate`() {
        val data = TrackerData(listOf(product, product.copy(id = 2, brand = "")))
        assertEquals(product, data.findProduct(" LATTE ", " MARCA "))
        assertEquals(2, data.findProduct("latte", "")!!.id)
        assertNull(data.findProduct("latte", "nuova"))
    }

    @Test fun `today is computed in Rome across midnight and DST`() {
        val clock = Clock.fixed(Instant.parse("2026-03-29T22:30:00Z"), ZoneOffset.UTC)
        assertEquals(LocalDate.parse("2026-03-30"), purchaseToday(clock))
        val winter = Clock.fixed(Instant.parse("2026-10-25T23:30:00Z"), ZoneOffset.UTC)
        assertEquals(LocalDate.parse("2026-10-26"), purchaseToday(winter))
    }

    @Test fun `purchase validation rejects invalid dates future dates quantity and legacy new units`() {
        val today = LocalDate.parse("2026-10-23")
        val draft = PurchaseDraft(name = "Pane", price = "2,50", date = today.toString())
        assertTrue(draft.errors(TrackerData(), today).isEmpty())
        assertTrue(draft.copy(date = "2026-02-30").errors(TrackerData(), today).containsKey("date"))
        assertTrue(draft.copy(date = "2026-10-24").errors(TrackerData(), today).containsKey("date"))
        assertTrue(draft.copy(quantity = "NaN").errors(TrackerData(), today).containsKey("quantity"))
        assertTrue(draft.copy(unit = "pezzo").errors(TrackerData(), today).containsKey("unit"))
        val legacy = TrackerData(listOf(product.copy(name = "Pane", brand = "", unit = "pezzo")))
        assertTrue(draft.copy(unit = "pezzo", quantity = "").errors(legacy, today).isEmpty())
    }
}
