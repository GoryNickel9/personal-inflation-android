package cc.baldeddu.prezzi.ui

import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cc.baldeddu.prezzi.BuildConfig
import cc.baldeddu.prezzi.update.UpdateViewModel

@Composable
internal fun UpdateControls(model: UpdateViewModel) {
    val state by model.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var open by rememberSaveable { mutableStateOf(false) }
    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (context.packageManager.canRequestPackageInstalls()) model.install()
        else model.reportError("Per installare la release, consenti a Prezzi di installare app nelle impostazioni Android.")
    }
    LaunchedEffect(model, context) {
        model.installEvents.collect { file ->
            try {
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", file)
                context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION).apply { clipData = ClipData.newRawUri("Aggiornamento", uri) })
            } catch (_: Exception) { model.reportError("Non riesco ad aprire l’installazione Android. Riprova.") }
        }
    }
    IconButton(onClick = { open = true; model.check(force = true) }) {
        BadgedBox(badge = { if (state.available != null) Badge { Text("1") } }) {
            Icon(Icons.Default.Info, if (state.available != null) "Aggiornamento disponibile" else "Aggiornamenti dell’app")
        }
    }
    if (open) AlertDialog(onDismissRequest = { open = false }, title = { Text("Aggiornamenti") },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Versione installata: ${BuildConfig.VERSION_NAME}")
                if (BuildConfig.DEBUG) Text("Build debug: per passare al canale release serve una prima installazione dell’APK firmato da GitHub.", style = MaterialTheme.typography.bodySmall)
                if (state.checking) { LinearProgressIndicator(Modifier.fillMaxWidth()); Text("Controllo le release GitHub…") }
                state.message?.let { Text(it) }
                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                state.available?.let { release ->
                    Text("Versione ${release.versionName} · ${"%.1f".format(java.util.Locale.ITALY, release.size / 1048576.0)} MiB", style = MaterialTheme.typography.titleMedium)
                    if (release.notes.isNotBlank()) Text(release.notes, style = MaterialTheme.typography.bodySmall)
                }
                if (state.downloading) {
                    LinearProgressIndicator(progress = { state.progress / 100f }, modifier = Modifier.fillMaxWidth())
                    Text("Download: ${state.progress}%")
                }
                if (state.verifying) Text("Verifico il file prima dell’installazione…")
                Text("Il controllo automatico avviene all’apertura, al massimo una volta ogni 6 ore. Download e installazione partono dal tuo comando; Android chiederà conferma.", style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/${BuildConfig.RELEASE_REPOSITORY}/releases/latest")))
                }) { Text("Apri release su GitHub") }
            }
        }, confirmButton = {
            if (state.available != null) Button(enabled = !state.busy, onClick = {
                if (state.file == null) model.download()
                else if (context.packageManager.canRequestPackageInstalls()) model.install()
                else {
                    try { permissions.launch(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))) }
                    catch (_: Exception) { model.reportError("Apri le impostazioni Android e consenti le installazioni da questa app.") }
                }
            }) { Text(if (state.file == null) "Scarica aggiornamento" else "Installa aggiornamento") }
            else TextButton(enabled = !state.busy, onClick = { model.check(force = true) }) { Text("Controlla ora") }
        }, dismissButton = { TextButton(onClick = { open = false }) { Text("Chiudi") } })
}
