package cc.baldeddu.prezzi.domain

import cc.baldeddu.prezzi.data.TrackerJson
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ParityTest {
    @Test fun `matches original TypeScript calculations on sixty deterministic datasets`() {
        val text = javaClass.getResource("/parity.json")!!.readText()
        val cases = JSONObject(text).getJSONArray("cases")
        repeat(cases.length()) { i ->
            val case = cases.getJSONObject(i)
            val data = TrackerJson.decode(case.getJSONObject("data"))
            comparisonWindows.forEachIndexed { j, window ->
                val expected = case.getJSONArray("metrics").getJSONObject(j)
                val actual = inflationFor(data, window)
                close(expected.getDouble("currentCost"), actual.currentCost)
                close(expected.getDouble("priorCost"), actual.priorCost)
                assertEquals(expected.getInt("matched"), actual.matched)
                if (expected.isNull("rate")) assertNull(actual.rate) else close(expected.getDouble("rate"), actual.rate!!)
            }
            val points = basketHistory(data)
            checkPoints(case.getJSONArray("points"), points, basket = true)
            data.products.forEachIndexed { j, product ->
                checkPoints(case.getJSONArray("productPoints").getJSONArray(j), productHistory(product, data.observations))
            }
            val annual = yearOverYear(points, true)
            if (case.isNull("annual")) assertNull(annual) else {
                val expected = case.getJSONObject("annual")
                assertEquals(expected.getString("priorDate"), annual!!.priorDate.toString())
                close(expected.getDouble("changePercent"), annual.percent)
                close(expected.getDouble("differenceCents"), annual.differenceCents)
            }
        }
    }

    private fun checkPoints(expected: JSONArray, actual: List<PricePoint>, basket: Boolean = false) {
        assertEquals(expected.length(), actual.size)
        actual.forEachIndexed { i, point ->
            val row = expected.getJSONObject(i)
            assertEquals(row.getString("date"), point.date.toString())
            close(row.getDouble("priceCents"), point.priceCents)
            close(row.getDouble("changePercent"), point.changePercent)
            close(row.getDouble("differenceCents"), point.differenceCents)
            if (basket) {
                close(row.getDouble("priceIndex"), point.priceIndex)
                close(row.getDouble("priceChangeCents"), point.priceChangeCents)
                assertEquals(row.getInt("productCount"), point.productCount)
                assertEquals(row.getInt("addedProductCount"), point.addedProductCount)
            }
        }
    }

    private fun close(expected: Double, actual: Double) = assertEquals(expected, actual, maxOf(1e-8, kotlin.math.abs(expected) * 1e-12))
}
