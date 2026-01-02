package edu.aiims.medresodk.auth.api

import okhttp3.Interceptor
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import edu.aiims.medresodk.auth.managers.MedresAuthManager
import edu.aiims.medresodk.auth.storage.MedresTokenProvider
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import kotlinx.coroutines.runBlocking

@RunWith(AndroidJUnit4::class)
class AuthInterceptorUrlUpdateTest {

    private val authManager: MedresAuthManager = mock()
    private val tokenProvider: MedresTokenProvider = mock()
    private lateinit var interceptor: AuthInterceptor
    private val chain: Interceptor.Chain = mock()

    @Before
    fun setUp() {
        interceptor = AuthInterceptor(authManager, tokenProvider)
    }

    @Test
    fun `intercept UPDATES token in URL on 401 retry`() = runBlocking {
        // GIVEN: A tokenized URL with an old token
        val oldToken = "OLD_TOKEN"
        val newToken = "NEW_TOKEN"
        val originalUrl = "https://central.local/v1/key/$oldToken/projects/1/formList"
        val originalRequest = Request.Builder().url(originalUrl).build()
        
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

        whenever(chain.request()).thenReturn(originalRequest)
        
        // Mock chain.proceed: check URL on second call
        var callCount = 0
        whenever(chain.proceed(any())).thenAnswer { invocation ->
            callCount++
            val req = invocation.arguments[0] as Request
            if (callCount == 1) {
                errorResponse
            } else {
                // VERIFY: The URL should now have the NEW token
                assertThat(req.url.toString(), equalTo("https://central.local/v1/key/$newToken/projects/1/formList"))
                // VERIFY: The Authorization header should also have the NEW token
                assertThat(req.header("Authorization"), equalTo("Bearer $newToken"))
                successResponse
            }
        }
        
        whenever(authManager.awaitReauthentication()).thenReturn(true)
        whenever(tokenProvider.getActiveProjectToken()).thenReturn(newToken)

        // WHEN
        val result = interceptor.intercept(chain)

        // THEN
        assertThat(result.code, equalTo(200))
        assertThat(callCount, equalTo(2)) // Initial + Retry
    }
}
