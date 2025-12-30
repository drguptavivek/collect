package org.aiims.odk.auth.api

import okhttp3.Interceptor
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.aiims.odk.auth.managers.AiimsAuthManager
import org.aiims.odk.auth.storage.AiimsTokenProvider
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import kotlinx.coroutines.runBlocking

@RunWith(AndroidJUnit4::class)
class AuthInterceptorTest {

    private val authManager: AiimsAuthManager = mock()
    private val tokenProvider: AiimsTokenProvider = mock()
    private lateinit var interceptor: AuthInterceptor
    private val chain: Interceptor.Chain = mock()

    @Before
    fun setUp() {
        interceptor = AuthInterceptor(authManager, tokenProvider)
    }

    @Test
    fun `intercept RETRIES request on 401 if reauth succeeds`() = runBlocking {
        // GIVEN
        val originalRequest = Request.Builder().url("https://api.example.com/data").build()
        val errorResponse = Response.Builder()
            .request(originalRequest)
            .protocol(Protocol.HTTP_1_1)
            .code(401)
            .message("Unauthorized")
            .body("{}".toResponseBody())
            .build()

        val successResponse = Response.Builder()
            .request(originalRequest)
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body("{}".toResponseBody())
            .build()

        // Chain Config
        whenever(chain.request()).thenReturn(originalRequest)
        
        // Smart Answer to handle both calls
        whenever(chain.proceed(any())).thenAnswer { invocation ->
            val req = invocation.arguments[0] as Request
            println("Test: chain.proceed called with header: ${req.header("Authorization")}")
            if (req.header("Authorization") == "Bearer new_token") {
                successResponse
            } else {
                errorResponse
            }
        }
        
        // Mocks for Auth Flow
        whenever(authManager.awaitReauthentication()).thenReturn(true) // suspend func mocked
        whenever(tokenProvider.getActiveProjectToken()).thenReturn("new_token")

        // WHEN
        println("Test: Calling intercept")
        val result = interceptor.intercept(chain)

        // THEN
        println("Test: Result code: ${result.code}")
        verify(authManager).awaitReauthentication()
        verify(tokenProvider).getActiveProjectToken()
        assertThat(result.code, equalTo(200))
    }

    @Test
    fun `intercept DOES NOT retry on 401 if reauth fails`() = runBlocking {
        val originalRequest = Request.Builder().url("https://api.example.com/data").build()
        val errorResponse = Response.Builder()
            .request(originalRequest)
            .protocol(Protocol.HTTP_1_1)
            .code(401)
            .message("Unauthorized")
            .body("{}".toResponseBody())
            .build()

        whenever(chain.request()).thenReturn(originalRequest)
        whenever(chain.proceed(any())).thenReturn(errorResponse)

        whenever(authManager.awaitReauthentication()).thenReturn(false)

        val result = interceptor.intercept(chain)

        verify(authManager).awaitReauthentication()
        verify(tokenProvider, times(0)).getActiveProjectToken()
        assertThat(result.code, equalTo(401))
    }

    @Test
    fun `intercept DOES NOT retry on 403`() = runBlocking {
        val originalRequest = Request.Builder().url("https://api.example.com/data").build()
        val errorResponse = Response.Builder()
            .request(originalRequest)
            .protocol(Protocol.HTTP_1_1)
            .code(403)
            .message("Forbidden")
            .body("{}".toResponseBody())
            .build()

        whenever(chain.request()).thenReturn(originalRequest)
        whenever(chain.proceed(any())).thenReturn(errorResponse)

        val result = interceptor.intercept(chain)

        verify(authManager, times(0)).awaitReauthentication()
        assertThat(result.code, equalTo(403))
    }
}
