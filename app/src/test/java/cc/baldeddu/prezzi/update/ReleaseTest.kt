package cc.baldeddu.prezzi.update

import cc.baldeddu.prezzi.BuildConfig
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ReleaseTest {
    private val base = "https://github.com/${BuildConfig.RELEASE_REPOSITORY}/releases/download/v1.2.0/"
    private fun asset(name: String, size: Int) = JSONObject().put("name", name).put("size", size).put("browser_download_url", base + name)
    private fun release() = JSONObject().put("draft", false).put("prerelease", false).put("tag_name", "v1.2.0")
        .put("body", "Novità").put("assets", JSONArray().put(asset("update.json", 200)).put(asset("PrezziNelTempo-1.2.0.apk", 1234)))
    private fun manifest() = JSONObject().put("schemaVersion", 1).put("applicationId", BuildConfig.APPLICATION_ID)
        .put("versionCode", 3).put("versionName", "1.2.0").put("minSdk", 26).put("size", 1234)
        .put("apkAsset", "PrezziNelTempo-1.2.0.apk").put("sha256", "a".repeat(64))

    @Test fun `parses complete stable release and exact repository asset`() {
        val result = ReleaseParser.parse(release(), manifest())
        assertEquals(3L, result.versionCode)
        assertEquals(base + "PrezziNelTempo-1.2.0.apk", result.apkUrl)
        assertEquals("Novità", result.notes)
    }

    @Test fun `rejects draft prerelease missing and duplicate assets`() {
        listOf("draft", "prerelease").forEach { key ->
            assertThrows(IllegalArgumentException::class.java) { ReleaseParser.parse(release().put(key, true), manifest()) }
        }
        assertThrows(IllegalArgumentException::class.java) { ReleaseParser.parse(release().put("assets", JSONArray()), manifest()) }
        val duplicate = release().apply { getJSONArray("assets").put(asset("update.json", 200)) }
        assertThrows(IllegalArgumentException::class.java) { ReleaseParser.manifestAsset(duplicate) }
    }

    @Test fun `rejects mismatched package version and unsafe metadata`() {
        val changes = mapOf("applicationId" to "other.app", "versionName" to "1.3.0", "versionCode" to 0,
            "size" to MAX_APK_BYTES + 1, "sha256" to "short", "minSdk" to 25, "schemaVersion" to 99,
            "apkAsset" to "../../other.apk")
        changes.forEach { (key, value) ->
            assertThrows(key, IllegalArgumentException::class.java) { ReleaseParser.parse(release(), manifest().put(key, value)) }
        }
        assertThrows(IllegalArgumentException::class.java) { ReleaseParser.parse(release(), manifest().put("size", 1235)) }
    }

    @Test fun `rejects another host repository tag and encoded paths`() {
        listOf(base.replace("https:", "http:") + "update.json", base.replace("github.com", "evil.invalid") + "update.json",
            base.replace("/v1.2.0/", "/v1.0.0/") + "update.json", base + "update.json?token=other",
            base.replace("personal-inflation-android", "another-repository") + "update.json").forEach {
            assertThrows(IllegalArgumentException::class.java) { ReleaseParser.assetUrl(it, "v1.2.0", "update.json") }
        }
    }
}
