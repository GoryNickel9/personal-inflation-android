package cc.baldeddu.prezzi

import cc.baldeddu.prezzi.data.*
import cc.baldeddu.prezzi.domain.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class TrackerViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val product = Product(1, "Latte", "", "L", 1.0)
    private val entry = Observation(1, 1, 100, LocalDate.parse("2020-01-01"))
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun teardown() { Dispatchers.resetMain() }

    private inner class Fake : TrackerGateway {
        var data = TrackerData(listOf(product), listOf(entry))
        var failRead = false
        var failWrite = false
        var reads = 0
        var writes = 0
        override suspend fun cached() = Snapshot(data, 1)
        override suspend fun refresh(): Snapshot {
            reads++
            if (failRead) throw IOException("Offline")
            return Snapshot(data, System.currentTimeMillis())
        }
        override suspend fun purchase(draft: PurchaseDraft, data: TrackerData): PurchaseReceipt {
            writes++
            if (failWrite) throw IOException("Esito non confermato: controlla il paniere.")
            val receipt = entry.copy(id = 2, unitPriceCents = 200)
            this.data = data.copy(observations = data.observations + receipt)
            return PurchaseReceipt(product, receipt, false)
        }
        override suspend fun delete(productId: Int) { data = TrackerData() }
    }

    @Test fun `failed refresh retains cached data and marks offline`() = runTest(dispatcher) {
        val gateway = Fake().apply { failRead = true }
        val model = TrackerViewModel(gateway)
        advanceUntilIdle()
        assertEquals(gateway.data, model.state.value.data)
        assertTrue(model.state.value.offline)
        assertTrue(model.state.value.hasData)
        assertEquals("Offline", model.state.value.error)
        assertFalse(model.state.value.loading)
    }

    @Test fun `successful purchase is not repeated and refreshes server data`() = runTest(dispatcher) {
        val gateway = Fake()
        val model = TrackerViewModel(gateway)
        advanceUntilIdle()
        val draft = PurchaseDraft("Latte", price = "2", date = "2020-01-01")
        model.purchase(draft)
        model.purchase(draft)
        advanceUntilIdle()
        assertEquals(1, gateway.writes)
        assertEquals(1, model.state.value.savedPurchases)
        assertEquals(2, model.state.value.data.observations.size)
        assertEquals(2, gateway.reads)
    }

    @Test fun `uncertain mutation keeps error and never automatically retries writes`() = runTest(dispatcher) {
        val gateway = Fake().apply { failWrite = true }
        val model = TrackerViewModel(gateway)
        advanceUntilIdle()
        model.purchase(PurchaseDraft("Latte", price = "2", date = "2020-01-01"))
        advanceUntilIdle()
        assertEquals(1, gateway.writes)
        assertEquals(0, model.state.value.savedPurchases)
        assertTrue(model.state.value.error!!.contains("Esito non confermato"))
        assertFalse(model.state.value.busy)
    }

    @Test fun `refresh throttles recent data unless explicitly forced`() = runTest(dispatcher) {
        val gateway = Fake()
        val model = TrackerViewModel(gateway)
        advanceUntilIdle()
        model.refresh()
        advanceUntilIdle()
        assertEquals(1, gateway.reads)
        model.refresh(true)
        advanceUntilIdle()
        assertEquals(2, gateway.reads)
    }

    @Test fun `delete removes product and all its observations`() = runTest(dispatcher) {
        val gateway = Fake()
        val model = TrackerViewModel(gateway)
        advanceUntilIdle()
        model.delete(product)
        advanceUntilIdle()
        assertTrue(model.state.value.data.products.isEmpty())
        assertTrue(model.state.value.data.observations.isEmpty())
    }
}
