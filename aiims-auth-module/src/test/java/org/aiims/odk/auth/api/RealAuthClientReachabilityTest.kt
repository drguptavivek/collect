package org.aiims.odk.auth.api

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class RealAuthClientReachabilityTest {

    private lateinit var server: MockWebServer
    private lateinit var context: Context
    private lateinit var authClient: RealAuthClient

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        context = ApplicationProvider.getApplicationContext()
        // Instantiate directly for test isolation
        authClient = RealAuthClient(context, server.url("/v1/").toString(), null)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `checkReachability returns true on 200 OK and uses HEAD request`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200))

        val result = authClient.checkReachability()

        assertThat(result, equalTo(true))
        val request = server.takeRequest()
        assertThat("Expected HEAD request but was ${request.method}", request.method, equalTo("HEAD"))
        assertThat(request.path, equalTo("/v1/version.txt"))
    }

    @Test
    fun `checkReachability returns false on 404`() = runTest {
        server.enqueue(MockResponse().setResponseCode(404))

        val result = authClient.checkReachability()

        assertThat(result, equalTo(false))
    }

    @Test
    fun `checkReachability caches result within TTL`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200))

        // First call
        val result1 = authClient.checkReachability()
        assertThat(result1, equalTo(true))
        assertThat(server.requestCount, equalTo(1))

        // Second call immediately - should be cached
        val result2 = authClient.checkReachability()
        assertThat(result2, equalTo(true))
        assertThat(server.requestCount, equalTo(1)) // Request count still 1
    }

    @Test
    fun `checkReachability deduplicates concurrent calls`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBodyDelay(500, TimeUnit.MILLISECONDS))
        // We only enqueue one response, proving only one request is made if deduplication works

        val deferred1 = async { authClient.checkReachability() }
        val deferred2 = async { authClient.checkReachability() }

        val res1 = deferred1.await()
        val res2 = deferred2.await()

        assertThat(res1, equalTo(true))
        assertThat(res2, equalTo(true))
        assertThat(server.requestCount, equalTo(1))
    }

    @Test
    fun `checkReachability respects TTL`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200))
        server.enqueue(MockResponse().setResponseCode(200))

        // First call
        authClient.checkReachability()
        assertThat(server.requestCount, equalTo(1))

        // Manually expire cache using reflection
        val lastTimeField = RealAuthClient::class.java.getDeclaredField("lastReachabilityCheckTime")
        lastTimeField.isAccessible = true
        lastTimeField.set(authClient, 0L)

        // Second call - should make new request
        authClient.checkReachability()
        assertThat(server.requestCount, equalTo(2))
    }
}
