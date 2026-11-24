package app.koma.testing

import java.io.IOException
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import okhttp3.Headers
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer

/** Answers every request from stubs: the stub with the longest URL prefix wins. */
class FakeNetwork : Interceptor {
    data class Request(
        val method: String,
        val url: String,
        val body: String,
        val headers: Headers,
    ) {
        /** A form body's field. */
        fun field(name: String): String? =
            body
                .split("&")
                .map { it.split("=", limit = 2) }
                .find { it[0] == name }
                ?.getOrNull(1)
                ?.let { java.net.URLDecoder.decode(it, "UTF-8") }
    }

    private class Stub(val status: Int, val body: String?)

    private val stubs = mutableMapOf<String, Stub>()
    private val inFlight = AtomicInteger()
    val requests = CopyOnWriteArrayList<Request>()
    val unstubbed = CopyOnWriteArrayList<String>()

    /** Runs as each request arrives, before it is answered: what happens while it is in flight. */
    @Volatile var whileInFlight: (Request) -> Unit = {}

    val idle: Boolean
        get() = inFlight.get() == 0

    fun answer(url: String, status: Int, json: String) {
        synchronized(stubs) { stubs[url] = Stub(status, json) }
    }

    /** Requests under [url] throw an IOException. */
    fun fail(url: String) {
        synchronized(stubs) { stubs[url] = Stub(0, null) }
    }

    /** The URLs requested under [prefix], in order. */
    fun requested(prefix: String): List<String> =
        requests.map { it.url }.filter { it.startsWith(prefix) }

    override fun intercept(chain: Interceptor.Chain): Response {
        inFlight.incrementAndGet()
        try {
            val request = chain.request()
            val url = request.url.toString()
            val body = request.body?.let { Buffer().also(it::writeTo).readUtf8() } ?: ""
            val recorded = Request(request.method, url, body, request.headers)
            requests += recorded
            whileInFlight(recorded)
            val stub =
                synchronized(stubs) {
                    stubs.entries
                        .filter { url.startsWith(it.key) }
                        .maxByOrNull { it.key.length }
                        ?.value
                }
            if (stub == null) {
                unstubbed += url
                throw IOException("No stub answers $url")
            }
            val json = stub.body ?: throw IOException("$url failed")
            return Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(stub.status)
                .message("")
                .body(json.toResponseBody("application/json".toMediaType()))
                .build()
        } finally {
            inFlight.decrementAndGet()
        }
    }
}
