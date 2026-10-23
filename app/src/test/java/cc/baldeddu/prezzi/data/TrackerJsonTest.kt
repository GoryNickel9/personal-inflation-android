package cc.baldeddu.prezzi.data

import cc.baldeddu.prezzi.domain.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class TrackerJsonTest {
    private val data = TrackerData(listOf(Product(1, "Latte", "Marca", "L", 0.15)),
        listOf(Observation(1, 1, 155, LocalDate.parse("2026-01-01"), "Negozio", "Offerta")))

    @Test fun `JSON roundtrip preserves fractional quantities cents dates and optional fields`() {
        assertEquals(data, TrackerJson.decode(TrackerJson.encode(data)))
        val withoutOptional = data.copy(observations = listOf(data.observations[0].copy(location = null, note = null)))
        assertEquals(withoutOptional, TrackerJson.decode(TrackerJson.encode(withoutOptional)))
    }

    @Test fun `legacy brand defaults to empty string`() {
        val json = TrackerJson.encode(data)
        json.getJSONArray("products").getJSONObject(0).remove("brand")
        assertEquals("", TrackerJson.decode(json).products.first().brand)
    }

    @Test fun `invalid and dangling data is rejected`() {
        val invalid = TrackerJson.encode(data)
        invalid.getJSONArray("observations").getJSONObject(0).put("productId", 99)
        assertThrows(IllegalArgumentException::class.java) { TrackerJson.decode(invalid) }
        assertThrows(Exception::class.java) { TrackerJson.decode(JSONObject("{}")) }
        val negative = TrackerJson.encode(data)
        negative.getJSONArray("products").getJSONObject(0).put("basketQuantity", -1)
        assertThrows(IllegalArgumentException::class.java) { TrackerJson.decode(negative) }
    }

    @Test fun `purchase body respects matching product unit and quantity and trims identity`() {
        val draft = PurchaseDraft(" LATTE ", " marca ", "kg", "99", "2,25", "2020-01-01")
        val json = TrackerJson.purchaseBody(draft, data)
        assertEquals("LATTE", json.getString("name"))
        assertEquals("L", json.getString("unit"))
        assertEquals(0.15, json.getDouble("basketQuantity"), 1e-9)
        assertEquals("2.25", json.getString("unitPrice"))
    }

    @Test fun `invalid draft cannot reach transport`() {
        assertThrows(IllegalArgumentException::class.java) { TrackerJson.purchaseBody(PurchaseDraft(), data) }
    }
}
