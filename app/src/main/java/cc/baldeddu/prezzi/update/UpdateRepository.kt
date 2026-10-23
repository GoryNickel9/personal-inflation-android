package cc.baldeddu.prezzi.update

import android.content.Context
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import kotlin.coroutines.coroutineContext

interface UpdateGateway {
    suspend fun check(force: Boolean): Release?
    suspend fun download(release: Release, progress: (Int) -> Unit): File
    suspend fun verify(file: File, release: Release)
}

class UpdateRepository(context: Context, private val client: GitHubClient = GitHubClient()) : UpdateGateway {
    private val context = context.applicationContext
    private val preferences = this.context.getSharedPreferences("updates", Context.MODE_PRIVATE)
    private val directory = File(this.context.cacheDir, "updates")
    private val verifier = ApkVerifier(this.context)

    override suspend fun check(force: Boolean): Release? = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val last = preferences.getLong("lastAttempt", 0)
        if (!force && now >= last && now - last < UPDATE_CHECK_INTERVAL_MS) return@withContext null
        preferences.edit().putLong("lastAttempt", now).apply()
        client.latest()?.also {
            if (it.minSdk > Build.VERSION.SDK_INT) throw UpdateException("La nuova versione richiede Android API ${it.minSdk} o superiore.")
        }
    }

    override suspend fun download(release: Release, progress: (Int) -> Unit): File = withContext(Dispatchers.IO) {
        check(directory.exists() || directory.mkdirs()) { "Non riesco a creare la cartella degli aggiornamenti." }
        val partial = File(directory, "update.part")
        val ready = File(directory, "update-${release.versionCode}.apk")
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            var received = 0L
            var lastProgress = -1
            val downloadContext = coroutineContext
            client.read(release.apkUrl) { input ->
                FileOutputStream(partial).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        downloadContext.ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        received += count
                        require(received <= release.size && received <= MAX_APK_BYTES) { "Dimensione dell’APK superiore al previsto." }
                        output.write(buffer, 0, count)
                        digest.update(buffer, 0, count)
                        val current = ((received * 100) / release.size).toInt()
                        if (current != lastProgress) { progress(current); lastProgress = current }
                    }
                    output.fd.sync()
                }
            }
            require(received == release.size) { "Download incompleto. Riprova." }
            val actual = digest.digest().joinToString("") { "%02x".format(it) }
            require(actual == release.sha256) { "Checksum dell’APK non valido. Il file non verrà installato." }
            verifier.verify(partial, release)
            check(partial.renameTo(ready)) { "Non riesco a salvare l’aggiornamento." }
            directory.listFiles()?.filter { it != ready && it.name.matches(Regex("update-[0-9]+\\.apk")) }?.forEach { it.delete() }
            ready
        } finally { partial.delete() }
    }

    override suspend fun verify(file: File, release: Release) = withContext(Dispatchers.IO) {
        require(file.exists() && file.length() == release.size && file.parentFile?.canonicalFile == directory.canonicalFile) { "File dell’aggiornamento non disponibile. Scaricalo di nuovo." }
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) { val count = input.read(buffer); if (count < 0) break; digest.update(buffer, 0, count) }
        }
        require(digest.digest().joinToString("") { "%02x".format(it) } == release.sha256) { "Il file è stato modificato dopo il download." }
        verifier.verify(file, release)
    }
}
