package eu.kanade.tachiyomi.network

import eu.kanade.tachiyomi.network.interceptor.CloudflareInterceptor
import eu.kanade.tachiyomi.network.interceptor.UncaughtExceptionInterceptor
import eu.kanade.tachiyomi.network.interceptor.UserAgentInterceptor
import okhttp3.OkHttpClient

/** The client every source builds on: Koma's [http], with the interceptors extensions expect. */
class NetworkHelper(http: OkHttpClient) {

    val client: OkHttpClient = http.newBuilder()
        .addInterceptor(UncaughtExceptionInterceptor())
        .addInterceptor(UserAgentInterceptor { USER_AGENT })
        .addInterceptor(CloudflareInterceptor())
        .build()
}

/** Mihon's default `User-Agent`. */
const val USER_AGENT = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 " +
    "(KHTML, like Gecko) Chrome/149.0.0.0 Mobile Safari/537.36"
