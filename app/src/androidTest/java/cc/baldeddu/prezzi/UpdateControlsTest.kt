package cc.baldeddu.prezzi

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import cc.baldeddu.prezzi.ui.PrezziTheme
import cc.baldeddu.prezzi.ui.UpdateControls
import cc.baldeddu.prezzi.update.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class UpdateControlsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun downloadRequiresExplicitCommandAndShowsVerifiedInstallAction() {
        var downloads = 0
        val gateway = object : UpdateGateway {
            override suspend fun check(force: Boolean) = Release(BuildConfig.VERSION_CODE + 1L, "1.2.0", 26,
                "https://github.com/test.apk", 1000, "a".repeat(64), "Aggiornamento di prova")
            override suspend fun download(release: Release, progress: (Int) -> Unit): File {
                downloads++
                progress(100)
                return File("verified-test.apk")
            }
            override suspend fun verify(file: File, release: Release) = Unit
        }
        val model = runBlocking(Dispatchers.Main) { UpdateViewModel(gateway) }
        compose.setContent { PrezziTheme { UpdateControls(model) } }
        compose.onNodeWithContentDescription("Aggiornamenti dell’app").performClick()
        compose.waitUntil(10_000) { model.state.value.available != null }
        assertEquals(0, downloads)
        compose.onNodeWithText("Scarica aggiornamento").performClick()
        compose.waitUntil(10_000) { model.state.value.file != null }
        compose.onNodeWithText("Installa aggiornamento").assertExists().assertIsEnabled()
        compose.onNodeWithText("Download verificato. Puoi installare l’aggiornamento.").assertExists()
        assertEquals(1, downloads)
    }
}
