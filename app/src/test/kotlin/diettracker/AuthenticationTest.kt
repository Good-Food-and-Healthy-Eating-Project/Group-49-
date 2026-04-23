package diettracker

import io.ktor.client.HttpClient
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.formUrlEncode
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals

private suspend fun HttpClient.loginTestUser() {
    post("/Login") {
        contentType(ContentType.Application.FormUrlEncoded)
        setBody(
            listOf(
                "email" to "test@test.com",
                "password" to "test@test.com",
            ).formUrlEncode(),
        )
    }
}

class AuthenticationTest {
    @Test
    fun should_redirect_to_login_when_not_Authentication() =
        testApplication {
            application { module(testing = true) }

            val client =
                createClient {
                    install(HttpCookies)
                    followRedirects = false
                }

            client.loginTestUser()

            val result = client.get("/logout")
            assertEquals(302, result.status.value)
            assertEquals("/Login", result.headers[HttpHeaders.Location])
        }

    @Test
    fun should_redirect_to_login_page_when_user_login() =
        testApplication {
            application { module(testing = true) }
            val result = client.get("/logout")
            assertEquals(302, result.status.value)
            assertEquals("/Login", result.headers[HttpHeaders.Location])
        }

    @Test
    fun client_dash_should_redirect_to_login_when_not_Authentication() =
        testApplication {
            application { module(testing = true) }
            val result = client.get("/client_dash")
            assertEquals(302, result.status.value)
            assertEquals("/Login", result.headers[HttpHeaders.Location])
        }

    @Test
    fun food_log_should_redirect_to_login_when_not_Authentication() =
        testApplication {
            application { module(testing = true) }
            val result = client.get("/food_log")
            assertEquals(302, result.status.value)
            assertEquals("/Login", result.headers[HttpHeaders.Location])
        }

    @Test
    fun food_diary_should_redirect_to_login_when_not_Authentication() =
        testApplication {
            application { module(testing = true) }
            val result = client.get("/diary")
            assertEquals(302, result.status.value)
            assertEquals("/Login", result.headers[HttpHeaders.Location])
        }
}
