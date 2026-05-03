package diettracker

import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
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
    fun `login with valid credentials sets session and redirects`() = testApplication {
        application { module(testing = true) }

        val client = createClient {
            install(HttpCookies)
            followRedirects = false
        }

        // need a test user in DB first
        UserDatabase.addUser("test@test.com", "password123")

        val response = client.post("/Login") {
            setBody("email=test@test.com&password=password123")
        }

        assertEquals(302, response.status.value)
        assertEquals("/client_dash", response.headers[HttpHeaders.Location])
    }

    @Test
    fun `login with invalid credentials shows error`() = testApplication {
        application { module(testing = true) }

        val client = createClient {
            install(HttpCookies)
        }

        val response = client.post("/Login") {
            setBody("email=wrong@test.com&password=wrong")
        }

        assertEquals(200, response.status.value) // stays on page
    }

    @Test
    fun `authenticated user can access protected route`() = testApplication {
        application { module(testing = true) }

        val client = createClient {
            install(HttpCookies)
        }

        UserDatabase.addUser("test@test.com", "password123")

        client.post("/Login") {
            setBody("email=test@test.com&password=password123")
        }

        val response = client.get("/client_dash")

        assertEquals(200, response.status.value)
    }

    @Test
    fun should_log_out_redirect_to_login_when_not_Authentication() =
        testApplication {
            application { module(testing = true) }
            val client =
                createClient {
                    install(HttpCookies)
                    followRedirects = false
                }
            val result = client.get("/logout")
            assertEquals(302, result.status.value)
            assertEquals("/Login", result.headers[HttpHeaders.Location])
        }

    @Test
    fun `logout clears session and redirects to home`() = testApplication {
        application { module(testing = true) }

        val client = createClient {
            install(HttpCookies)
            followRedirects = false
        }

        UserDatabase.addUser("test@test.com", "password123")

        client.post("/Login") {
            setBody("email=test@test.com&password=password123")
        }

        val logoutResponse = client.get("/logout")

        assertEquals(302, logoutResponse.status.value)
        assertEquals("/", logoutResponse.headers[HttpHeaders.Location])

        val afterLogout = client.get("/client_dash")

        assertEquals(302, afterLogout.status.value)
    }


    @Test
    fun should_redirect_to_login_when_client_dash_not_Authentication() =
        testApplication {
            application {
                module(testing = true)
            }
            val client =
                createClient {
                    install(HttpCookies)
                    followRedirects = false
                }
            val result = client.get("/client_dash")
            assertEquals(302, result.status.value)
            assertEquals("/Login", result.headers[HttpHeaders.Location])
        }

    @Test
    fun should_redirect_to_login_when_food_log_not_Authentication() =
        testApplication {
            application {
                module(testing = true)
            }
            val client =
                createClient {
                    install(HttpCookies)
                    followRedirects = false
                }
            val result = client.get("/food_log")
            assertEquals(302, result.status.value)
            assertEquals("/Login", result.headers[HttpHeaders.Location])
        }

    @Test
    fun should_redirect_to_login_when_food_diary_not_Authentication() =
        testApplication {
            application {
                module(testing = true)
            }
            val client =
                createClient {
                    install(HttpCookies)
                    followRedirects = false
                }
            val result = client.get("/food_diary")
            assertEquals(302, result.status.value)
            assertEquals("/Login", result.headers[HttpHeaders.Location])
        }

    @Test
    fun should_redirect_to_login_when_food_diary_day_not_Authentication() =
        testApplication {
            application {
                module(testing = true)
            }
            val client =
                createClient {
                    install(HttpCookies)
                    followRedirects = false
                }
            val result = client.get("/food_diary_day")
            assertEquals(302, result.status.value)
            assertEquals("/Login", result.headers[HttpHeaders.Location])
        }

    @Test
    fun should_redirect_to_login_when_professional_not_Authentication() =
        testApplication {
            application {
                module(testing = true)
            }
            val client =
                createClient {
                    install(HttpCookies)
                    followRedirects = false
                }
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

    @Test
    fun
}
