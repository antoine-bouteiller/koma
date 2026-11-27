package eu.kanade.tachiyomi.network.interceptor

import okhttp3.Interceptor
import okhttp3.Response

/** Passes every request through: Koma solves no Cloudflare challenge, but `KeiSource` requires one. */
class CloudflareInterceptor : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response = chain.proceed(chain.request())
}
