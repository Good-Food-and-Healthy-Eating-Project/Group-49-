package diettracker

import io.ktor.client.request.get
import io.ktor.http.HttpHeaders
import io.ktor.server.testing.testApplication
import org.junit.jupiter.api.BeforeEach
import kotlin.test.Test
import kotlin.test.assertEquals

class AuthenticationTest {
    @BeforeEach
    fun setUP() {
        TestDatabaseFactory.init()
    }

    @Test
    fun should_log_out_redirect_to_login_when_not_Authentication() =
        testApplication {
            application { module(testing = true) }
            val result = client.get("/logout")
            assertEquals(302, result.status.value)
            assertEquals("/Login", result.headers[HttpHeaders.Location])
        }

    @Test
    fun should_logout_redirect_to_login_page_when_user_login() =
        testApplication {
            application { module(testing = true) }
            val result = client.get("/logout")
            assertEquals(302, result.status.value)
            assertEquals("/Login", result.headers[HttpHeaders.Location])
        }

    @Test
    fun should_redirect_to_login_when_client_dash_not_Authentication() =
        testApplication {
            application { module(testing = true) }
            val result = client.get("/client_dash")
            assertEquals(302, result.status.value)
            assertEquals("/Login", result.headers[HttpHeaders.Location])
        }

    @Test
    fun should_redirect_to_login_when_food_log_not_Authentication() =
        testApplication {
            application { module(testing = true) }
            val result = client.get("/food_log")
            assertEquals(302, result.status.value)
            assertEquals("/Login", result.headers[HttpHeaders.Location])
        }

    @Test
    fun should_redirect_to_login_when_food_diary_not_Authentication() =
        testApplication {
            application { module(testing = true) }
            val result = client.get("/food_diary")
            assertEquals(302, result.status.value)
            assertEquals("/Login", result.headers[HttpHeaders.Location])
        }

    @Test
    fun should_redirect_to_login_when_food_diary_day_not_Authentication() =
        testApplication {
            application { module(testing = true) }
            val result = client.get("/food_diary_day")
            assertEquals(302, result.status.value)
            assertEquals("/Login", result.headers[HttpHeaders.Location])
        }

    @Test
    fun should_redirect_to_login_when_professional_not_Authentication() =
        testApplication {
            application { module(testing = true) }
            val result = client.get("/professionals")
            assertEquals(302, result.status.value)
            assertEquals("/Login", result.headers[HttpHeaders.Location])
        }

    @Test
    fun should_redirect_to_login_when_professional_dash_not_Authentication() =
        testApplication {
            application { module(testing = true) }
            val client =
                createClient {
                    followRedirects = false
                }
            val result = client.get("/professionals_dash")
            assertEquals(302, result.status.value)
            assertEquals("/Login", result.headers[HttpHeaders.Location])
        }
}
