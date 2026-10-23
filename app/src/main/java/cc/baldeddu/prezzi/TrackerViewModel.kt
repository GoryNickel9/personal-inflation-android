package cc.baldeddu.prezzi

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cc.baldeddu.prezzi.data.*
import cc.baldeddu.prezzi.domain.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TrackerState(
    val data: TrackerData = TrackerData(), val hasData: Boolean = false, val loading: Boolean = true,
    val busy: Boolean = false, val error: String? = null, val notice: String? = null,
    val fetchedAt: Long? = null, val offline: Boolean = false, val savedPurchases: Int = 0,
)

class TrackerViewModel(private val repository: TrackerGateway) : ViewModel() {
    private val mutableState = MutableStateFlow(TrackerState())
    val state = mutableState.asStateFlow()
    private var refreshJob: Job? = null
    private var initialized = false

    init {
        viewModelScope.launch {
            try {
                repository.cached()?.let { cache ->
                    mutableState.update { it.copy(data = cache.data, hasData = true, fetchedAt = cache.fetchedAt, offline = true) }
                }
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) {
                mutableState.update { it.copy(notice = "Cache locale non leggibile. Ricarico i dati dal server.") }
            }
            initialized = true
            refresh(force = true)
        }
    }

    fun refresh(force: Boolean = false) {
        if (!initialized || state.value.busy || refreshJob?.isActive == true) return
        val last = state.value.fetchedAt
        if (!force && !state.value.offline && last != null && System.currentTimeMillis() - last < 60_000) return
        refreshJob = viewModelScope.launch {
            mutableState.update { it.copy(loading = true, error = null) }
            try {
                val snapshot = repository.refresh()
                mutableState.update { it.copy(data = snapshot.data, hasData = true, fetchedAt = snapshot.fetchedAt,
                    offline = false, notice = snapshot.warning ?: it.notice) }
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) {
                mutableState.update { it.copy(error = error.message ?: "Caricamento non riuscito.", offline = true) }
            } finally {
                mutableState.update { it.copy(loading = false) }
            }
        }
    }

    fun purchase(draft: PurchaseDraft) {
        if (state.value.busy || !state.value.hasData) return
        val errors = draft.errors(state.value.data)
        if (errors.isNotEmpty()) {
            mutableState.update { it.copy(error = errors.values.first()) }
            return
        }
        mutate {
            val receipt = repository.purchase(draft, state.value.data)
            mutableState.update {
                val data = it.data
                it.copy(data = TrackerData(
                    (data.products.filterNot { p -> p.id == receipt.product.id } + receipt.product).sortedBy { p -> normalized(p.name) },
                    data.observations.filterNot { o -> o.id == receipt.observation.id } + receipt.observation),
                    savedPurchases = it.savedPurchases + 1,
                    notice = if (receipt.created) "Prodotto aggiunto al paniere con il primo acquisto." else "Acquisto aggiunto allo storico.")
            }
        }
    }

    fun delete(product: Product) {
        if (state.value.busy) return
        mutate {
            repository.delete(product.id)
            mutableState.update { it.copy(data = TrackerData(it.data.products.filterNot { p -> p.id == product.id },
                it.data.observations.filterNot { o -> o.productId == product.id }), notice = "Prodotto rimosso.") }
        }
    }

    private fun mutate(action: suspend () -> Unit) {
        mutableState.update { it.copy(busy = true, error = null, notice = null) }
        viewModelScope.launch {
            // Join the cancelled read before applying a mutation so stale data cannot overwrite its receipt.
            refreshJob?.cancel()
            refreshJob?.join()
            var success = false
            try {
                action()
                success = true
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) { mutableState.update { it.copy(error = error.message ?: "Operazione non riuscita.") } }
            finally { mutableState.update { it.copy(busy = false) } }
            if (success) refresh(force = true)
        }
    }
}
