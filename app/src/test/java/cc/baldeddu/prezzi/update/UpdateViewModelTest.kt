package cc.baldeddu.prezzi.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class UpdateViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val release = Release(3, "1.2.0", 26, "https://github.com/test.apk", 1234, "a".repeat(64), "")
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun teardown() { Dispatchers.resetMain() }

    private inner class Fake : UpdateGateway {
        var result: Release? = release
        var failCheck = false
        var failDownload = false
        var failVerify = false
        var downloads = 0
        var checks = 0
        var verifications = 0
        override suspend fun check(force: Boolean): Release? {
            checks++
            if (failCheck) throw IOException("network failure")
            return result
        }
        override suspend fun download(release: Release, progress: (Int) -> Unit): File {
            downloads++
            if (failDownload) throw UpdateException("Checksum non valido")
            progress(50); progress(100)
            return File("verified-update.apk")
        }
        override suspend fun verify(file: File, release: Release) {
            verifications++
            if (failVerify) throw UpdateException("Firma non valida")
        }
    }

    @Test fun `only offers a strictly newer version and never downloads automatically`() = runTest(dispatcher) {
        val gateway = Fake()
        val model = UpdateViewModel(gateway, currentCode = 2)
        model.check(); model.check()
        advanceUntilIdle()
        assertEquals(1, gateway.checks)
        assertEquals(release, model.state.value.available)
        assertEquals(0, gateway.downloads)
        gateway.result = release.copy(versionCode = 2)
        model.check(true); advanceUntilIdle()
        assertNull(model.state.value.available)
        gateway.result = release.copy(versionCode = 1)
        model.check(true); advanceUntilIdle()
        assertNull(model.state.value.available)
    }

    @Test fun `offline automatic check stays quiet and manual check exposes retryable error`() = runTest(dispatcher) {
        val gateway = Fake().apply { failCheck = true }
        val model = UpdateViewModel(gateway, 2)
        model.check(); advanceUntilIdle()
        assertNull(model.state.value.error)
        model.check(true); advanceUntilIdle()
        assertNotNull(model.state.value.error)
        assertFalse(model.state.value.busy)
    }

    @Test fun `keeps known release when throttled check returns no new information`() = runTest(dispatcher) {
        val gateway = Fake()
        val model = UpdateViewModel(gateway, 2)
        model.check(); advanceUntilIdle()
        gateway.result = null
        model.check(); advanceUntilIdle()
        assertEquals(release, model.state.value.available)
    }

    @Test fun `downloads once on command and verifies again before install event`() = runTest(dispatcher) {
        val gateway = Fake()
        val model = UpdateViewModel(gateway, 2)
        val events = mutableListOf<File>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.installEvents.collect { events += it } }
        model.check(); advanceUntilIdle()
        model.download(); model.download(); advanceUntilIdle()
        assertEquals(1, gateway.downloads)
        assertEquals(100, model.state.value.progress)
        assertTrue(events.isEmpty())
        model.check(); advanceUntilIdle()
        assertEquals(1, gateway.checks)
        model.install(); advanceUntilIdle()
        assertEquals(1, gateway.verifications)
        assertEquals(listOf(File("verified-update.apk")), events)
    }

    @Test fun `checksum or signature failure never starts installation`() = runTest(dispatcher) {
        val gateway = Fake().apply { failDownload = true }
        val model = UpdateViewModel(gateway, 2)
        val events = mutableListOf<File>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.installEvents.collect { events += it } }
        model.check(); advanceUntilIdle()
        model.download(); advanceUntilIdle()
        assertNull(model.state.value.file)
        assertTrue(events.isEmpty())
        gateway.failDownload = false
        gateway.failVerify = true
        model.download(); advanceUntilIdle()
        model.install(); advanceUntilIdle()
        assertEquals("Firma non valida", model.state.value.error)
        assertTrue(events.isEmpty())
        assertNull(model.state.value.file)
    }
}
