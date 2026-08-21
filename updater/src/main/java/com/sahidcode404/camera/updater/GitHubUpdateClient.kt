package com.sahidcode404.camera.updater

import android.content.Context
import android.os.Build
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class GitHubUpdateClient(
    private val context: Context,
    private val owner: String = "sahid-code404",
    private val repo: String = "Universal_Camera",
) {
    suspend fun check(): UpdateCheckResult = withContext(Dispatchers.IO) {
        runCatching {
            val release = getJson("https://api.github.com/repos/$owner/$repo/releases/latest")
            val assets = release.getJSONArray("assets")
            var manifestUrl: String? = null
            val assetUrls = mutableMapOf<String, String>()
            for (i in 0 until assets.length()) {
                val a = assets.getJSONObject(i)
                val name = a.getString("name")
                val url = a.getString("browser_download_url")
                assetUrls[name] = url
                if (name == "release-manifest.json") manifestUrl = url
            }
            val manifestJson = getJson(requireNotNull(manifestUrl) { "release-manifest.json is missing" })
            val manifest = parseManifest(manifestJson)
            require(manifest.schema == 1) { "Unsupported update manifest schema ${manifest.schema}" }
            if (Build.VERSION.SDK_INT < manifest.minSdk) return@runCatching UpdateCheckResult.UpToDate
            val installed = context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode
            if (manifest.versionCode <= installed) return@runCatching UpdateCheckResult.UpToDate
            val apkUrl = requireNotNull(assetUrls[manifest.apkAssetName]) { "APK asset ${manifest.apkAssetName} is missing" }
            UpdateCheckResult.Available(AvailableUpdate(manifest, apkUrl))
        }.getOrElse { UpdateCheckResult.Failed(it.message ?: "Update check failed") }
    }

    suspend fun downloadAndVerify(update: AvailableUpdate): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            update.manifest.signingCertSha256?.takeIf { it.isNotBlank() }?.let { expected ->
                val current = SigningCertificate.currentSha256(context)
                require(current.equals(expected, ignoreCase = true)) {
                    "Release signing certificate does not match installed Camera"
                }
            }
            val dir = File(context.cacheDir, "updates").apply { mkdirs() }
            val out = File(dir, update.manifest.apkAssetName)
            download(update.apkUrl, out)
            val actual = sha256(out)
            require(actual.equals(update.manifest.sha256, ignoreCase = true)) { "APK SHA-256 verification failed" }
            out
        }
    }

    private fun parseManifest(json: JSONObject) = ReleaseManifest(
        schema = json.optInt("schema", 1),
        versionCode = json.getLong("versionCode"),
        versionName = json.getString("versionName"),
        minSdk = json.optInt("minSdk", 28),
        apkAssetName = json.getString("apkAssetName"),
        sha256 = json.getString("sha256"),
        signingCertSha256 = json.optString("signingCertSha256").takeIf { it.isNotBlank() },
        changelog = json.optString("changelog"),
        mandatory = json.optBoolean("mandatory", false),
    )

    private fun getJson(url: String): JSONObject = JSONObject(requestText(url))

    private fun requestText(url: String): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000; readTimeout = 15_000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            setRequestProperty("User-Agent", "Camera-Android-Updater")
        }
        return connection.inputStream.bufferedReader().use { it.readText() }.also { connection.disconnect() }
    }

    private fun download(url: String, out: File) {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = true; connectTimeout = 15_000; readTimeout = 60_000
            setRequestProperty("User-Agent", "Camera-Android-Updater")
        }
        connection.inputStream.use { input -> out.outputStream().use { output -> input.copyTo(output, 1024 * 128) } }
        connection.disconnect()
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(1024 * 128)
            while (true) {
                val n = input.read(buffer)
                if (n <= 0) break
                digest.update(buffer, 0, n)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
