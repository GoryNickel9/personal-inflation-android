package cc.baldeddu.prezzi.update

import cc.baldeddu.prezzi.BuildConfig
import org.json.JSONObject
import java.net.URI

const val MAX_APK_BYTES = 100L * 1024 * 1024
const val UPDATE_CHECK_INTERVAL_MS = 6L * 60 * 60 * 1000

data class Release(
    val versionCode: Long, val versionName: String, val minSdk: Int,
    val apkUrl: String, val size: Long, val sha256: String, val notes: String,
)

object ReleaseParser {
    fun assetUrl(url: String, tag: String, file: String, repository: String = BuildConfig.RELEASE_REPOSITORY): String {
        val uri = URI(url)
        require(uri.scheme == "https" && uri.host == "github.com" && uri.port == -1 && uri.userInfo == null
            && uri.query == null && uri.fragment == null
            && uri.rawPath == "/$repository/releases/download/$tag/$file") { "Indirizzo dell’aggiornamento non valido." }
        return url
    }

    fun manifestAsset(release: JSONObject): String {
        require(!release.getBoolean("draft") && !release.getBoolean("prerelease")) { "La release non è stabile." }
        val tag = release.getString("tag_name")
        require(tag.matches(Regex("v[0-9]+\\.[0-9]+\\.[0-9]+"))) { "Tag release non valido." }
        val assets = release.getJSONArray("assets")
        val matches = (0 until assets.length()).map { assets.getJSONObject(it) }.filter { it.getString("name") == "update.json" }
        require(matches.size == 1) { "La release non contiene un manifest aggiornamenti valido." }
        return assetUrl(matches.single().getString("browser_download_url"), tag, "update.json")
    }

    fun parse(release: JSONObject, manifest: JSONObject): Release {
        manifestAsset(release)
        require(manifest.getInt("schemaVersion") == 1) { "Formato aggiornamento non supportato." }
        require(manifest.getString("applicationId") == BuildConfig.APPLICATION_ID) { "Package aggiornamento non valido." }
        val name = manifest.getString("versionName")
        require(name.matches(Regex("[0-9]+\\.[0-9]+\\.[0-9]+")) && release.getString("tag_name") == "v$name") { "Versioni della release incoerenti." }
        val code = manifest.getLong("versionCode")
        val minSdk = manifest.getInt("minSdk")
        val size = manifest.getLong("size")
        val hash = manifest.getString("sha256")
        require(code in 1..2_100_000_000 && minSdk >= 26 && size in 1..MAX_APK_BYTES && hash.matches(Regex("[a-f0-9]{64}"))) { "Metadati aggiornamento non validi." }
        val apkName = "PrezziNelTempo-$name.apk"
        require(manifest.getString("apkAsset") == apkName) { "Nome APK non valido." }
        val assets = release.getJSONArray("assets")
        val matches = (0 until assets.length()).map { assets.getJSONObject(it) }.filter { it.getString("name") == apkName }
        require(matches.size == 1 && matches.single().getLong("size") == size) { "Dimensione APK non coerente con la release." }
        return Release(code, name, minSdk, assetUrl(matches.single().getString("browser_download_url"), "v$name", apkName),
            size, hash, release.optString("body", "").take(4000))
    }
}
