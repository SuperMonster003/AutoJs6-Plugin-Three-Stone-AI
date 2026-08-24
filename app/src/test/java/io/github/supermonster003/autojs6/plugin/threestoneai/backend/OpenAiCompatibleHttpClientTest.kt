package io.github.supermonster003.autojs6.plugin.threestoneai.backend

import okhttp3.Authenticator
import okhttp3.CookieJar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenAiCompatibleHttpClientTest {
    @Test
    fun clientDisablesCredentialReplayAndObservationSurfaces() {
        val client = OpenAiCompatibleHttpClient.create()
        try {
            assertFalse(client.followRedirects)
            assertFalse(client.followSslRedirects)
            assertFalse(client.retryOnConnectionFailure)
            assertTrue(client.interceptors.isEmpty())
            assertTrue(client.networkInterceptors.isEmpty())
            assertSame(Authenticator.NONE, client.authenticator)
            assertSame(Authenticator.NONE, client.proxyAuthenticator)
            assertSame(CookieJar.NO_COOKIES, client.cookieJar)
            assertNull(client.cache)
            assertEquals(30_000, client.connectTimeoutMillis)
            assertEquals(60_000, client.writeTimeoutMillis)
            assertEquals(0, client.readTimeoutMillis)
            assertEquals(0, client.callTimeoutMillis)
        } finally {
            OpenAiCompatibleHttpClient.close(client)
        }
    }
}
