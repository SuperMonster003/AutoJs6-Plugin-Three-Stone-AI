package io.github.supermonster003.autojs6.plugin.threestoneai.backend

import okhttp3.Authenticator
import okhttp3.ConnectionPool
import okhttp3.CookieJar
import okhttp3.Dispatcher
import okhttp3.EventListener
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/** Builds an isolated client for credential-bearing online generation requests. */
internal object OpenAiCompatibleHttpClient {
    fun create(): OkHttpClient = OkHttpClient.Builder()
        .dispatcher(Dispatcher())
        .connectionPool(ConnectionPool())
        .eventListener(EventListener.NONE)
        .authenticator(Authenticator.NONE)
        .proxyAuthenticator(Authenticator.NONE)
        .cookieJar(CookieJar.NO_COOKIES)
        .cache(null)
        .followRedirects(false)
        .followSslRedirects(false)
        .retryOnConnectionFailure(false)
        .connectTimeout(CONNECT_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
        .writeTimeout(WRITE_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
        // The caller owns the generation timeout and cancels the active Call. A read timeout
        // would otherwise terminate a healthy stream while the model is thinking.
        .readTimeout(0L, TimeUnit.MILLISECONDS)
        .callTimeout(0L, TimeUnit.MILLISECONDS)
        .build()

    fun close(client: OkHttpClient) {
        client.dispatcher.cancelAll()
        client.connectionPool.evictAll()
        client.dispatcher.executorService.shutdown()
    }

    private const val CONNECT_TIMEOUT_MILLIS = 30_000L
    private const val WRITE_TIMEOUT_MILLIS = 60_000L
}
