package diettracker

import io.ktor.client.request.get
import io.ktor.http.HttpHeaders
import io.ktor.server.routing.get
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals

class AuthenticationTest {
    @Test
    fun should_redirect_to_login_when_not_Authentication() =
        testApplication {
            application { module(testing = true) }
            val result = client.get("/logout")
            assertEquals(200, result.status.value)
            assertEquals("/Login", result.headers[HttpHeaders.Location])
        }

    @Test
    fun client_dash_should_redirect_to_login_when_not_Authentication() =
        testApplication {
            application { module(testing = true) }
            val result = client.get("/client_dash")
            assertEquals(200, result.status.value)
            assertEquals("/Login", result.headers[HttpHeaders.Location])
        }

    @Test
    fun food_log_should_redirect_to_login_when_not_Authentication() =
        testApplication {
            application { module(testing = true) }
            val result = client.get("/food_log")
            assertEquals(200, result.status.value)
            assertEquals("/Login", result.headers[HttpHeaders.Location])
        }

    @Test
    fun food_diary_should_redirect_to_login_when_not_Authentication() =
        testApplication {
            application { module(testing = true) }
            val result = client.get("/diary")
            assertEquals(200, result.status.value)
            assertEquals("/Login", result.headers[HttpHeaders.Location])
        }
}
