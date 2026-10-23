package cc.baldeddu.prezzi.update

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cc.baldeddu.prezzi.BuildConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

data class UpdateState(
    val checking: Boolean = false, val downloading: Boolean = false, val verifying: Boolean = false,
    val progress: Int = 0, val available: Release? = null, val file: File? = null,
    val message: String? = null, val error: String? = null,
) { val busy: Boolean get() = checking || downloading || verifying }

class UpdateViewModel(private val repository: UpdateGateway, private val currentCode: Long = BuildConfig.VERSION_CODE.toLong()) : ViewModel() {
    private val mutableState = MutableStateFlow(UpdateState())
    val state = mutableState.asStateFlow()
    private val installChannel = Channel<File>(Channel.BUFFERED)
    val installEvents = installChannel.receiveAsFlow()

    fun check(force: Boolean = false) {
        if (state.value.busy) return
        if (!force && state.value.file != null) return
        mutableState.update { it.copy(checking = true, error = null) }
        viewModelScope.launch {
            try {
                val release = repository.check(force)
                mutableState.update {
                    when {
                        release != null && release.versionCode > currentCode -> it.copy(available = release,
                            file = if (it.available == release) it.file else null, message = "Versione ${release.versionName} disponibile.")
                        force -> it.copy(available = null, file = null, message = "Hai già la versione più recente disponibile.")
                        else -> it
                    }
                }
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) {
                if (force) mutableState.update { it.copy(error = message(error)) }
            } finally { mutableState.update { it.copy(checking = false) } }
        }
    }

    fun download() {
        val release = state.value.available ?: return
        if (state.value.busy) return
        mutableState.update { it.copy(downloading = true, progress = 0, file = null, error = null, message = null) }
        viewModelScope.launch {
            try {
                val file = repository.download(release) { progress -> mutableState.update { it.copy(progress = progress.coerceIn(0, 100)) } }
                mutableState.update { it.copy(file = file, message = "Download verificato. Puoi installare l’aggiornamento.") }
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) { mutableState.update { it.copy(error = message(error)) } }
            finally { mutableState.update { it.copy(downloading = false) } }
        }
    }

    fun install() {
        val file = state.value.file ?: return
        val release = state.value.available ?: return
        if (state.value.busy) return
        mutableState.update { it.copy(verifying = true, error = null) }
        viewModelScope.launch {
            try {
                repository.verify(file, release)
                installChannel.send(file)
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) { mutableState.update { it.copy(file = null, error = message(error)) } }
            finally { mutableState.update { it.copy(verifying = false) } }
        }
    }

    fun reportError(message: String) { mutableState.update { it.copy(error = message) } }
    private fun message(error: Exception): String = if (error is UpdateException || error is IllegalArgumentException || error is IllegalStateException)
        error.message ?: "Aggiornamento non riuscito." else "Connessione non disponibile. Riprova a controllare gli aggiornamenti."
}
