package cc.baldeddu.prezzi

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.test.ext.junit.runners.AndroidJUnit4
import cc.baldeddu.prezzi.data.*
import cc.baldeddu.prezzi.domain.*
import cc.baldeddu.prezzi.ui.PrezziApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class NativeAppTest {
    @get:Rule val compose = createComposeRule()
    private val product = Product(1, "Latte", "Marca test", "L", 2.0)
    private val data = TrackerData(listOf(product), listOf(
        Observation(1, 1, 100, LocalDate.parse("2024-01-01")),
        Observation(2, 1, 120, LocalDate.parse("2025-01-01"))))

    private class FakeGateway(var data: TrackerData) : TrackerGateway {
        var writes = 0
        var deletes = 0
        var fail = false
        override suspend fun cached() = Snapshot(data, 1)
        override suspend fun refresh(): Snapshot {
            if (fail) throw java.io.IOException("Offline: ultimi dati salvati")
            return Snapshot(data, System.currentTimeMillis())
        }
        override suspend fun purchase(draft: PurchaseDraft, data: TrackerData): PurchaseReceipt {
            writes++
            val product = Product(2, draft.name.trim(), draft.brand, draft.unit, draft.quantity.toDouble())
            val observation = Observation(3, 2, priceInCents(draft.price)!!, LocalDate.parse(draft.date))
            this.data = TrackerData(data.products + product, data.observations + observation)
            return PurchaseReceipt(product, observation, true)
        }
        override suspend fun delete(productId: Int) {
            deletes++
            data = TrackerData(data.products.filterNot { it.id == productId }, data.observations.filterNot { it.productId == productId })
        }
    }

    private fun launch(gateway: FakeGateway) {
        val model = runBlocking(Dispatchers.Main) { TrackerViewModel(gateway) }
        compose.setContent { PrezziApp(model) }
        compose.waitUntil(10_000) { !model.state.value.loading }
    }

    private fun reveal(matcher: SemanticsMatcher): SemanticsNodeInteraction {
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(matcher)
        return compose.onNode(matcher)
    }

    @Test fun nativeNavigationChartAndDeleteConfirmation() {
        val gateway = FakeGateway(data)
        launch(gateway)
        compose.onNodeWithText("Il tuo costo\ndella vita").assertIsDisplayed()
        compose.onNodeWithText("Paniere", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Andamento del paniere").assertIsDisplayed()
        reveal(hasContentDescription("Rimuovi Latte · Marca test")).performClick()
        compose.onNodeWithText("Annulla").performClick()
        assertEquals(0, gateway.deletes)
        reveal(hasContentDescription("Mostra grafico di Latte · Marca test")).performClick()
        compose.onNodeWithText("Prezzo unitario", substring = false).assertDoesNotExist()
        compose.onNodeWithText("Costo di 2 L").assertExists()
        reveal(hasContentDescription("Rimuovi Latte · Marca test")).performClick()
        compose.onNodeWithText("Rimuovi").performClick()
        compose.waitUntil(10_000) { gateway.deletes == 1 }
        compose.onNodeWithText("Il paniere è ancora vuoto").assertExists()
    }

    @Test fun purchaseRetainsFreeTextAcrossNavigationAndSavesExactlyOnce() {
        val gateway = FakeGateway(data)
        launch(gateway)
        compose.onNodeWithText("Acquisto", useUnmergedTree = true).performClick()
        reveal(hasText("Prodotto")).performTextInput("Pane nuovo")
        reveal(hasText("Marca (facoltativa)")).performTextInput("Forno")
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        compose.onNodeWithText("Riepilogo", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Acquisto", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Pane nuovo").assertExists()
        compose.onNodeWithText("Forno").assertExists()
        reveal(hasText("Prezzo (€/kg)")).performTextInput("2,50")
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        reveal(hasText("Salva acquisto")).performClick()
        compose.waitUntil(10_000) { gateway.writes == 1 }
        compose.waitForIdle()
        assertEquals(1, gateway.writes)
        assertEquals(250, gateway.data.observations.last().unitPriceCents)
        compose.onNodeWithText("Prodotto aggiunto al paniere con il primo acquisto.").assertExists()
    }

    @Test fun offlineRetainsDataAndPreventsSilentEmptyState() {
        launch(FakeGateway(data).apply { fail = true })
        compose.onNodeWithText("Offline: ultimi dati salvati").assertExists()
        compose.onNodeWithText("Il paniere è ancora vuoto").assertDoesNotExist()
    }

    @Test fun draftSurvivesSavedStateRestoration() {
        val gateway = FakeGateway(data)
        val model = runBlocking(Dispatchers.Main) { TrackerViewModel(gateway) }
        val restoration = StateRestorationTester(compose)
        restoration.setContent { PrezziApp(model) }
        compose.waitUntil(10_000) { !model.state.value.loading }
        compose.onNodeWithText("Acquisto", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Prodotto").performTextInput("Nome da conservare")
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("Nome da conservare").assertExists()
        assertEquals(0, gateway.writes)
    }
}
