package app.koma.update

import app.koma.data.HttpException
import app.koma.data.call
import java.io.File
import java.io.IOException
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.buffer
import okio.sink

/**
 * Downloads [url] into [apk], reporting the bytes done and the total, null when unknown. Blocks.
 */
fun downloadApk(
    http: OkHttpClient,
    url: String,
    apk: File,
    onProgress: (done: Long, total: Long?) -> Unit,
) {
    checkNotNull(apk.parentFile).mkdirs()
    // A download cut short never leaves a partial APK at [apk].
    val partial = File(apk.path + ".part")
    http.call(Request.Builder().url(url).build()) { response ->
        if (!response.isSuccessful) throw HttpException(response.code)
        val total = response.body.contentLength().takeIf { it >= 0 }
        val source = response.body.source()
        partial.sink().buffer().use { sink ->
            var done = 0L
            while (true) {
                val read = source.read(sink.buffer, 64 * 1024L)
                if (read == -1L) break
                sink.emitCompleteSegments()
                done += read
                onProgress(done, total)
            }
        }
    }
    if (!partial.renameTo(apk)) throw IOException("Could not move the APK into place.")
}
