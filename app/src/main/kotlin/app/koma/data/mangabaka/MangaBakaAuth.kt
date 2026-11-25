package app.koma.data.mangabaka

import app.koma.AuthBrowser
import app.koma.SecretStore
import app.koma.data.HttpException
import app.koma.data.Session
import app.koma.data.SignInResult
import app.koma.data.SignedOutException
import app.koma.data.call
import app.koma.data.getText
import app.koma.data.json
import java.io.IOException
import java.time.Clock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

private const val ISSUER = "https://mangabaka.org/auth"
private const val CLIENT_ID = "lYFPQeyaYBnVaZJmcQHPiNjlprtCsVDL"
// MangaBaka rejects every token without `openid`.
private const val SCOPES = "openid library.read library.write offline_access"
private const val REDIRECT = "koma://auth"
private const val KEY = "mangabaka.session"
private const val SESSION_ENDED = "Your MangaBaka session ended. Sign in again."
private val JSON = "application/json".toMediaType()

@Serializable
private data class Tokens(
    val accessToken: String,
    val refreshToken: String? = null,
    val expiresAt: Long = 0,
)

@Serializable
private class Discovery(
    val issuer: String,
    @SerialName("authorization_endpoint") val authorization: String,
    @SerialName("token_endpoint") val token: String,
    @SerialName("revocation_endpoint") val revocation: String? = null,
)

@Serializable
private class TokenReply(
    @SerialName("access_token") val accessToken: String? = null,
    @SerialName("refresh_token") val refreshToken: String? = null,
    @SerialName("expires_in") val expiresIn: Long? = null,
    val error: String? = null,
)

/** MangaBaka sign-in: OAuth authorization code with PKCE, as a public client. */
class MangaBakaAuth(
    private val http: OkHttpClient,
    private val secrets: SecretStore,
    private val browser: AuthBrowser,
    private val clock: Clock,
) {
    private val discovering = Mutex()
    private var discovered: Discovery? = null
    private val refreshing = Mutex()
    // Guards each read-then-write of the session, so a reply for an older session never lands.
    private val lock = Any()
    @Volatile private var ended = ""
    private val current = MutableStateFlow(read())

    private val mutableSessions = MutableStateFlow(session(current.value))

    /** The session, updated at each sign-in, sign-out and end. */
    val sessions: StateFlow<Session> = mutableSessions.asStateFlow()

    suspend fun session(): Session = sessions.value

    suspend fun signIn(): SignInResult {
        val meta =
            try {
                discovery()
            } catch (_: IOException) {
                return SignInResult(false, "network")
            }
        val verifier = Pkce.random()
        val state = Pkce.random()
        val url =
            meta.authorization
                .toHttpUrl()
                .newBuilder()
                .addQueryParameter("response_type", "code")
                .addQueryParameter("client_id", CLIENT_ID)
                .addQueryParameter("redirect_uri", REDIRECT)
                .addQueryParameter("scope", SCOPES)
                .addQueryParameter("state", state)
                .addQueryParameter("code_challenge", Pkce.challenge(verifier))
                .addQueryParameter("code_challenge_method", "S256")
                .build()
                .toString()
        val back = browser.open(url, REDIRECT) ?: return SignInResult(false, "cancelled")
        if (back.getQueryParameters("state") != listOf(state)) {
            return SignInResult(false, "state_mismatch")
        }
        val iss = back.getQueryParameter("iss")
        if (iss != null && iss != meta.issuer) return SignInResult(false, "issuer_mismatch")
        back.getQueryParameter("error")?.let {
            return SignInResult(false, it)
        }
        val code =
            back.getQueryParameter("code")?.takeIf { it.isNotEmpty() }
                ?: return SignInResult(false, "invalid_response")
        val reply =
            try {
                token(
                    meta,
                    "grant_type" to "authorization_code",
                    "code" to code,
                    "redirect_uri" to REDIRECT,
                    "code_verifier" to verifier,
                )
            } catch (_: IOException) {
                return SignInResult(false, "network")
            }
        val access =
            reply.accessToken ?: return SignInResult(false, reply.error ?: "invalid_response")
        try {
            synchronized(lock) {
                ended = ""
                keep(Tokens(access, reply.refreshToken, expiresAt(reply)))
            }
        } catch (_: IOException) {
            return SignInResult(false, "storage")
        }
        return SignInResult(true, "")
    }

    /** Forgets the session, then revokes its refresh token, best effort. */
    suspend fun signOut() {
        val tokens =
            synchronized(lock) {
                val held = current.value ?: return
                ended = ""
                forget()
                held
            }
        val token = tokens.refreshToken ?: tokens.accessToken
        try {
            val endpoint = discovery().revocation ?: return
            http.call(
                Request.Builder()
                    .url(endpoint)
                    .post(
                        form(
                            "token" to token,
                            "token_type_hint" to
                                if (tokens.refreshToken != null) "refresh_token"
                                else "access_token",
                        )
                    )
                    .build()
            ) {}
        } catch (_: IOException) {
            // The session is already gone here; the token expires on its own.
        }
    }

    /** A GET with the access token, refreshed when it has expired or the API refuses it. */
    suspend fun <T> get(url: String, body: DeserializationStrategy<T>): T =
        json.decodeFromString(body, authorized(Request.Builder().url(url)))

    /** A write with [body] as JSON, refreshed as for [get]. A reply that is not 2xx throws. */
    suspend fun send(method: String, url: String, body: String) {
        authorized(Request.Builder().url(url).method(method, body.toRequestBody(JSON)))
    }

    private suspend fun authorized(request: Request.Builder): String {
        var tokens = current.value ?: throw SignedOutException()
        if (tokens.expiresAt in 1..clock.millis()) tokens = refresh(tokens)
        var reply = request(request, tokens)
        if (reply.first == 401) {
            tokens = refresh(tokens)
            reply = request(request, tokens)
        }
        if (reply.first !in 200..299) throw HttpException(reply.first)
        return reply.second
    }

    private fun request(request: Request.Builder, tokens: Tokens): Pair<Int, String> =
        http.call(request.header("Authorization", "Bearer ${tokens.accessToken}").build()) {
            it.code to it.body.string()
        }

    private suspend fun refresh(seen: Tokens): Tokens = refreshing.withLock {
        val now = current.value ?: throw SignedOutException()
        // Another caller refreshed meanwhile.
        if (now.accessToken != seen.accessToken) return@withLock now
        val sent = now.refreshToken
        val reply = sent?.let {
            token(discovery(), "grant_type" to "refresh_token", "refresh_token" to it)
        }
        synchronized(lock) {
            // Signed out, or in again, while the request was out: the reply is not this session's.
            val after = current.value ?: throw SignedOutException()
            if (after.refreshToken != sent) return@withLock after
            if (reply == null || reply.error == "invalid_grant") {
                ended = SESSION_ENDED
                forget()
                throw SignedOutException()
            }
            // MangaBaka revokes every token of the app when a retired refresh token is sent, so a
            // new one is kept before anything else, even from a reply that is otherwise unusable.
            val access = reply.accessToken
            if (access == null) {
                if (reply.error == null)
                    reply.refreshToken?.let { keep(after.copy(refreshToken = it)) }
                throw IOException("Refresh failed: ${reply.error ?: "invalid_response"}")
            }
            Tokens(access, reply.refreshToken ?: sent, expiresAt(reply)).also(::keep)
        }
    }

    private suspend fun discovery(): Discovery = discovering.withLock {
        discovered
            ?: try {
                json
                    .decodeFromString(
                        Discovery.serializer(),
                        http.getText("$ISSUER/.well-known/openid-configuration"),
                    )
                    .also {
                        val urls = listOfNotNull(it.authorization, it.token, it.revocation)
                        if (urls.any { url -> url.toHttpUrlOrNull() == null }) {
                            throw IOException("Unreadable discovery document.")
                        }
                        discovered = it
                    }
            } catch (e: SerializationException) {
                throw IOException("Unreadable discovery document.", e)
            }
    }

    private fun token(meta: Discovery, vararg fields: Pair<String, String>): TokenReply =
        http.call(Request.Builder().url(meta.token).post(form(*fields)).build()) { response ->
            // An OAuth error is a 400 or 401: a 429 or 5xx is the server's failure, whatever its
            // body.
            if (response.code == 429 || response.code >= 500)
                return@call TokenReply(error = "server")
            val reply =
                try {
                    json.decodeFromString(TokenReply.serializer(), response.body.string())
                } catch (_: SerializationException) {
                    TokenReply(error = "invalid_response")
                }
            if (reply.error == null && !response.isSuccessful)
                TokenReply(error = "invalid_response")
            else reply
        }

    private fun form(vararg fields: Pair<String, String>): FormBody =
        FormBody.Builder()
            .apply {
                for ((name, value) in fields) add(name, value)
                add("client_id", CLIENT_ID)
            }
            .build()

    private fun expiresAt(reply: TokenReply): Long =
        reply.expiresIn?.let { clock.millis() + it * 1000 } ?: 0

    private fun read(): Tokens? =
        secrets.get(KEY)?.let {
            try {
                json.decodeFromString(Tokens.serializer(), it)
            } catch (_: SerializationException) {
                null
            }
        }

    private fun keep(tokens: Tokens) {
        secrets.set(KEY, json.encodeToString(Tokens.serializer(), tokens))
        changed(tokens)
    }

    private fun forget() {
        secrets.delete(KEY)
        changed(null)
    }

    private fun changed(tokens: Tokens?) {
        current.value = tokens
        mutableSessions.value = session(tokens)
    }

    private fun session(tokens: Tokens?) =
        Session(signedIn = tokens != null, error = if (tokens == null) ended else "")
}
