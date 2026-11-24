package app.koma.update

import app.koma.data.HttpException
import app.koma.data.call
import app.koma.data.json
import app.koma.version.versionCode
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.OkHttpClient
import okhttp3.Request

private const val LATEST = "https://api.github.com/repos/antoine-bouteiller/koma/releases/latest"

data class Release(val version: String, val url: String)

@Serializable
private class Asset(val name: String, @SerialName("browser_download_url") val url: String)

@Serializable
private class GitHubRelease(
    @SerialName("tag_name") val tag: String = "",
    val assets: List<Asset> = emptyList(),
)

/** The latest stable release when it is newer than [installed], else null. */
fun newerRelease(http: OkHttpClient, installed: String): Release? {
    val request =
        Request.Builder().url(LATEST).header("Accept", "application/vnd.github+json").build()
    val body =
        http.newBuilder().callTimeout(10, TimeUnit.SECONDS).build().call(request) { response ->
            // No release yet.
            if (response.code == 404) return null
            if (!response.isSuccessful) throw HttpException(response.code)
            response.body.string()
        }
    val latest = json.decodeFromString(GitHubRelease.serializer(), body)
    // semantic-release tags each release `v<version>`.
    val version = latest.tag.removePrefix("v")
    val apk =
        latest.assets.find { it.name == "koma.apk" }
            ?: throw IOException("The latest release has no koma.apk.")
    return if (versionCode(version) > versionCode(installed)) Release(version, apk.url) else null
}
