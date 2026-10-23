package cc.baldeddu.prezzi.update

import cc.baldeddu.prezzi.BuildConfig
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

class UpdateException(message: String) : IOException(message)

class GitHubClient {
    fun latest(): Release? {
        val release = json("https://api.github.com/repos/${BuildConfig.RELEASE_REPOSITORY}/releases/latest", allowMissing = true) ?: return null
        val url = ReleaseParser.manifestAsset(release)
        return ReleaseParser.parse(release, json(url)!!)
    }

    private fun json(url: String, allowMissing: Boolean = false): JSONObject? = read(url, allowMissing) { stream ->
        val bytes = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val count = stream.read(buffer)
            if (count < 0) break
            if (bytes.size() + count > 1024 * 1024) throw UpdateException("Metadati aggiornamento troppo grandi.")
            bytes.write(buffer, 0, count)
        }
        JSONObject(bytes.toString(Charsets.UTF_8.name()))
    }

    fun <T> read(url: String, allowMissing: Boolean = false, consume: (InputStream) -> T): T? {
        var current = URL(url)
        repeat(6) { attempt ->
            require(current.protocol == "https" && current.userInfo == null) { "Il download richiede HTTPS." }
            require(current.host in setOf("api.github.com", "github.com", "release-assets.githubusercontent.com", "objects.githubusercontent.com")) {
                "Host del download non autorizzato."
            }
            val connection = (current.openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000; readTimeout = 30_000; instanceFollowRedirects = false
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "PrezziNelTempo/${BuildConfig.VERSION_NAME}")
                setRequestProperty("Cache-Control", "no-cache")
            }
            try {
                when (connection.responseCode) {
                    200 -> return connection.inputStream.use(consume)
                    301, 302, 303, 307, 308 -> {
                        if (attempt == 5) throw UpdateException("Troppi reindirizzamenti durante il download.")
                        val location = connection.getHeaderField("Location") ?: throw UpdateException("Reindirizzamento non valido.")
                        current = URL(current, location)
                    }
                    404 -> if (allowMissing) return null else throw UpdateException("File della release non disponibile.")
                    403, 429 -> throw UpdateException("GitHub ha limitato le richieste. Riprova più tardi.")
                    else -> throw UpdateException("GitHub non è disponibile (HTTP ${connection.responseCode}).")
                }
            } finally { connection.disconnect() }
        }
        throw UpdateException("Download non riuscito.")
    }
}
