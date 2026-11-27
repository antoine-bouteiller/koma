package app.koma.data

import java.io.IOException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

/** A reply whose status is not 2xx. */
class HttpException(val status: Int) : IOException("HTTP $status")

val json = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
}

/**
 * The app's HTTP client. GitHub refuses API requests without a User-Agent; a source's own reaches
 * its site.
 */
fun httpClient(): OkHttpClient =
    OkHttpClient.Builder()
        .addInterceptor { chain ->
            val request = chain.request()
            chain.proceed(
                if (request.header("User-Agent") != null) request
                else request.newBuilder().header("User-Agent", "koma").build()
            )
        }
        .build()

/** Runs [request] and hands its response to [read], closing it after. Blocks. */
inline fun <T> OkHttpClient.call(request: Request, read: (Response) -> T): T =
    newCall(request).execute().use(read)

/** The body of a 2xx reply to a GET of [url]. */
fun OkHttpClient.getText(url: String): String =
    call(Request.Builder().url(url).build()) { response ->
        if (!response.isSuccessful) throw HttpException(response.code)
        response.body.string()
    }

/** Runs [work], answering whether it failed on the network or on a reply it could not read. */
inline fun failed(work: () -> Unit): Boolean =
    try {
        work()
        false
    } catch (_: IOException) {
        true
    } catch (_: SerializationException) {
        true
    }
