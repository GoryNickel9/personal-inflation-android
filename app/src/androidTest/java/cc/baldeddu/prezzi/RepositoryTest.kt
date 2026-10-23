package cc.baldeddu.prezzi

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import cc.baldeddu.prezzi.data.*
import cc.baldeddu.prezzi.domain.*
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.IOException
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class RepositoryTest {
    private val data = TrackerData(listOf(Product(1, "Latte", "", "L", 1.5)),
        listOf(Observation(1, 1, 123, LocalDate.parse("2020-01-01"))))

    @Test fun atomicCacheRoundtripSurvivesNewRepositoryAndRejectsCorruption() = runBlocking {
        val file = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "test-${System.nanoTime()}.json")
        try {
            val repository = TrackerRepository(file, Transport { _, _, _ -> TrackerJson.encode(data) })
            assertNull(repository.cached())
            repository.refresh()
            val offline = TrackerRepository(file, Transport { _, _, _ -> throw IOException("offline") })
            assertEquals(data, offline.cached()!!.data)
            try { offline.refresh(); fail("Expected offline failure") } catch (_: ApiException) { }
            assertEquals(data, offline.cached()!!.data)
            file.writeText("truncated{")
            try { offline.cached(); fail("Expected invalid cache failure") } catch (_: Exception) { }
        } finally { file.delete() }
    }

    @Test fun successfulMutationsUseExistingApiContractAndInvalidateStaleCache() = runBlocking {
        val file = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "test-${System.nanoTime()}.json")
        val calls = mutableListOf<String>()
        val transport = Transport { method, path, body ->
            calls += "$method $path"
            when (method) {
                "GET" -> TrackerJson.encode(data)
                "POST" -> {
                    assertEquals("L", body!!.getString("unit"))
                    assertEquals("2.50", body.getString("unitPrice"))
                    JSONObject().put("product", TrackerJson.encode(data).getJSONArray("products").getJSONObject(0))
                        .put("observation", TrackerJson.encode(data).getJSONArray("observations").getJSONObject(0)).put("created", false)
                }
                else -> JSONObject().put("ok", true)
            }
        }
        try {
            val repository = TrackerRepository(file, transport)
            repository.refresh()
            repository.purchase(PurchaseDraft("latte", price = "2,50", date = "2020-01-01"), data)
            assertNull(repository.cached())
            repository.delete(1)
            assertEquals(listOf("GET /api/tracker", "POST /api/products", "DELETE /api/products/1"), calls)
        } finally { file.delete() }
    }
}
