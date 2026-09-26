package io.github.supermonster003.autojs6.plugin.threestoneai.profile

import android.content.Context
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.AndroidOnlineAiNetworkAccess
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.OnlineAiNetworkAccess
import okhttp3.Authenticator
import okhttp3.Call
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.concurrent.CancellationException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/** Fetches only a fixed public document. Profile endpoints and credentials are never consulted. */
internal class OnlineAiModelCatalogHttpTransport(
    private val networkAccess: OnlineAiNetworkAccess,
    private val url: HttpUrl = PUBLISHED_URL.toHttpUrl(),
    internal val client: OkHttpClient = createClient(),
) : OnlineAiModelCatalogTransport {
    private val activeCall = AtomicReference<Call?>()

    init {
        require(url.username.isEmpty() && url.password.isEmpty()) { "Model catalog URL must not contain credentials" }
        // HTTP injection exists solely for a loopback test server; production uses PUBLISHED_URL.
        require(url.isHttps || (url.scheme == "http" && url.host in setOf("127.0.0.1", "localhost", "::1"))) {
            "Model catalog URL must use HTTPS"
        }
    }

    override fun requireAccess() = networkAccess.requireAccess()

    override fun fetch(etag: String?): OnlineAiModelCatalogHttpResponse {
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("User-Agent", "3-Stone-AI-model-catalog")
            .apply { validOnlineAiModelCatalogEtag(etag)?.let { header("If-None-Match", it) } }
            .build()
        val call = client.newCall(request)
        check(activeCall.compareAndSet(null, call)) { "A model catalog request is already running" }
        try {
            if (Thread.currentThread().isInterrupted) {
                call.cancel()
                throw CancellationException("Model catalog refresh was cancelled")
            }
            return call.execute().use { response ->
                val payload = if (response.code == 200) {
                    val body = requireNotNull(response.body) { "Model catalog response is empty" }
                    readOnlineAiModelCatalogBytes(body.byteStream(), body.contentLength())
                } else null
                OnlineAiModelCatalogHttpResponse(response.code, payload, validOnlineAiModelCatalogEtag(response.header("ETag")))
            }
        } finally {
            activeCall.compareAndSet(call, null)
        }
    }

    override fun cancel() {
        activeCall.get()?.cancel()
    }

    internal companion object {
        const val PUBLISHED_URL = "https://raw.githubusercontent.com/SuperMonster003/AutoJs6-Plugin-Three-Stone-AI/model-catalog/online-models.json"

        fun createClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .callTimeout(30, TimeUnit.SECONDS)
            .followRedirects(false)
            .followSslRedirects(false)
            .retryOnConnectionFailure(false)
            .authenticator(Authenticator.NONE)
            .proxyAuthenticator(Authenticator.NONE)
            .cookieJar(CookieJar.NO_COOKIES)
            .cache(null)
            .build()
    }
}

/** Construction and snapshots read local files only; refresh is explicitly called by the UI. */
internal fun createOnlineAiModelCatalogRepository(context: Context): OnlineAiModelCatalogRepository {
    val app = context.applicationContext
    val profileRepository = OnlineAiProfileRepository(FileOnlineAiProfileDocumentStorage(app.filesDir))
    val network = AndroidOnlineAiNetworkAccess(app) { profileRepository.settings().allowMeteredNetwork }
    val bundled = app.assets.open("online-models.json").use { readOnlineAiModelCatalogBytes(it) }
    return OnlineAiModelCatalogRepository(
        bundledPayload = bundled,
        storage = OnlineAiModelCatalogFileStorage(app.filesDir),
        transport = OnlineAiModelCatalogHttpTransport(network),
    )
}

internal fun readOnlineAiModelCatalogBytes(input: InputStream, declaredLength: Long = -1L): ByteArray {
    val maximum = OnlineAiModelCatalogCodec.MAXIMUM_DOCUMENT_BYTES
    require(declaredLength < 0L || declaredLength <= maximum) { "Model catalog response is too large" }
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    while (true) {
        val read = input.read(buffer, 0, minOf(buffer.size, maximum - output.size() + 1))
        if (read < 0) break
        if (read == 0) continue
        require(output.size() + read <= maximum) { "Model catalog response is too large" }
        output.write(buffer, 0, read)
    }
    return output.toByteArray()
}
