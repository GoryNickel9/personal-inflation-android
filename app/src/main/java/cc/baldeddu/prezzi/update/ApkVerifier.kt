package cc.baldeddu.prezzi.update

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.pm.PackageInfoCompat
import java.io.File
import java.security.MessageDigest

fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

class ApkVerifier(private val context: Context) {
    @Suppress("DEPRECATION")
    fun verify(file: File, release: Release) {
        val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        val archive = context.packageManager.getPackageArchiveInfo(file.absolutePath, flags)
            ?: throw UpdateException("Il file scaricato non è un APK valido.")
        val installed = context.packageManager.getPackageInfo(context.packageName, flags)
        require(archive.packageName == context.packageName) { "L’APK appartiene a un’altra app." }
        require(PackageInfoCompat.getLongVersionCode(archive) == release.versionCode && archive.versionName == release.versionName) { "La versione dell’APK non corrisponde alla release." }
        require(release.versionCode > PackageInfoCompat.getLongVersionCode(installed)) { "L’aggiornamento non è più recente della versione installata." }
        require(archive.applicationInfo?.minSdkVersion == release.minSdk && release.minSdk <= Build.VERSION.SDK_INT) { "L’aggiornamento non è compatibile con questo dispositivo." }
        require(archive.applicationInfo?.flags?.and(ApplicationInfo.FLAG_DEBUGGABLE) == 0) { "La release contiene un APK di debug." }
        val expected = signatures(installed)
        require(expected.isNotEmpty() && signatures(archive) == expected) {
            "Firma diversa dall’app installata. Se usi il vecchio APK debug, installa prima la release da GitHub; i dati del sito restano disponibili."
        }
    }

    @Suppress("DEPRECATION")
    private fun signatures(info: PackageInfo): Set<String> = if (Build.VERSION.SDK_INT >= 28) {
        info.signingInfo?.apkContentsSigners.orEmpty().map { sha256(it.toByteArray()) }.toSet()
    } else info.signatures.orEmpty().map { sha256(it.toByteArray()) }.toSet()
}
