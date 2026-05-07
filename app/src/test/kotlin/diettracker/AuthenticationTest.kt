/**
 * Authentication tests using Ktor's testApplication.
 * Each test resets the in-memory H2 test database, starts the app with module(testing = true),
 * then creates a test HTTP client with cookies/redirect settings to verify route responses.
 */
package diettracker

import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
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
    fun `login with valid credentials sets session and redirects`() =
        testApplication {
            application { module(testing = true) }

            val client =
                createClient {
                    install(HttpCookies)
                    followRedirects = false
                }

            // need a test user in DB first
            UserDatabase.addUser("test@test.com", "password123")

            val response =
                client.post("/Login") {
                    header(HttpHeaders.ContentType, ContentType.Application.FormUrlEncoded.toString())
                    setBody("email=test@test.com&password=password123")
                }

            assertEquals(302, response.status.value)
            assertEquals("/client_dash", response.headers[HttpHeaders.Location])
        }

    // AC-ELDER-02
    @Test
    fun `login with invalid credentials shows error`() =
        testApplication {
            application { module(testing = true) }

            val client =
                createClient {
                    install(HttpCookies)
                }

            val response =
                client.post("/Login") {
                    header(HttpHeaders.ContentType, ContentType.Application.FormUrlEncoded.toString())
                    setBody("email=wrong@test.com&password=wrong")
                }

            assertEquals(200, response.status.value) // stays on page
        }

    // AC-ELDER-02
    @Test
    fun `login with correct email but wrong password shows error`() =
        testApplication {
            application { module(testing = true) }

            val client =
                createClient {
                    install(HttpCookies)
                }

            UserDatabase.addUser("test@test.com", "password123")

            val response =
                client.post("/Login") {
                    header(HttpHeaders.ContentType, ContentType.Application.FormUrlEncoded.toString())
                    setBody("email=test@test.com&password=wrongpassword")
                }

            assertEquals(200, response.status.value) // stays on page
        }

    @Test
    fun `authenticated user can access protected route`() =
        testApplication {
            application { module(testing = true) }

            val client =
                createClient {
                    install(HttpCookies)
                }

            UserDatabase.addUser("test@test.com", "password123")

            client.post("/Login") {
                header(HttpHeaders.ContentType, ContentType.Application.FormUrlEncoded.toString())
                setBody("email=test@test.com&password=password123")
            }

            val response = client.get("/client_dash")

            assertEquals(200, response.status.value)
        }

    @Test
    fun `logout clears session and redirects to home`() =
        testApplication {
            application { module(testing = true) }

            val client =
                createClient {
                    install(HttpCookies)
                    followRedirects = false
                }

            UserDatabase.addUser("test@test.com", "password123")

            client.post("/Login") {
                header(HttpHeaders.ContentType, ContentType.Application.FormUrlEncoded.toString())
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
    fun protected_routes_must_require_Authentication() =
        testApplication {
            application { module(testing = true) }

            val client =
                createClient {
                    followRedirects = false
                }

            val routes =
                listOf(
                    "/client_dash",
                    "/food_log",
                    "/professionals_dash",
                    "/professionals",
                    "/food_diary",
                    "/food_diary_day",
                )

            routes.forEach {
                    route ->
                val response = client.get(route)
                assertEquals(302, response.status.value)
                assertEquals("/Login", response.headers[HttpHeaders.Location])
            }
        }

    /**
     * Testing role based authentication:
     * Professionals cant access client features
     * It redirects to Login page
     */
    @Test
    fun professional_cannot_access_client_role_features() =
        testApplication {
            application { module(testing = true) }

            val client =
                createClient {
                    install(HttpCookies)
                    followRedirects = false
                }

            // Create profession via route so role is assigned
            client.post("/Professional-Sign-Up") {
                header(HttpHeaders.ContentType, ContentType.Application.FormUrlEncoded.toString())
                setBody("email=pro@test.com&password=password123")
            }

            // login the professional
            client.post("/Login") {
                header(HttpHeaders.ContentType, ContentType.Application.FormUrlEncoded.toString())
                setBody("email=pro@test.com&password=password123")
            }

            val response = client.get("/client_dash")

            assertEquals(302, response.status.value)
            assertEquals("/Login", response.headers[HttpHeaders.Location])
        }

    /**
     * Testing role based authentication:
     * Clients cant access professional features
     * It redirects to Login page
     */

    @Test
    fun client_cannot_access_professional_features() =
        testApplication {
            application { module(testing = true) }

            val client =
                createClient {
                    install(HttpCookies)
                    followRedirects = false
                }

            // Create profession via route so role is assigned
            client.post("/Sign-Up") {
                header(HttpHeaders.ContentType, ContentType.Application.FormUrlEncoded.toString())
                setBody("email=client@test.com&password=password123")
            }

            // login the professional
            client.post("/Login") {
                header(HttpHeaders.ContentType, ContentType.Application.FormUrlEncoded.toString())
                setBody("email=client@test.com&password=password123")
            }

            val response = client.get("/professionals_dash")

            assertEquals(302, response.status.value)
            assertEquals("/Login", response.headers[HttpHeaders.Location])
        }

    /**
     * Testing correct access using client:
     * Authenticated clients with client role can access client dashboard
     * Should return 200 for a successfully redirecting to client dashboard
     */
    @Test
    fun client_can_access_client_feature() =
        testApplication {
            application { module(testing = true) }

            val client =
                createClient {
                    install(HttpCookies)
                    followRedirects = false
                }
            client.post("/Sign-Up") {
                header(HttpHeaders.ContentType, ContentType.Application.FormUrlEncoded.toString())
                setBody("email=client@test.com&password=password123")
            }

            client.post("/Login") {
                header(HttpHeaders.ContentType, ContentType.Application.FormUrlEncoded.toString())
                setBody("email=client@test.com&password=password123")
            }

            val response = client.get("/client_dash")

            assertEquals(200, response.status.value)
        }

    // AC-DB-05
    @Test
    fun `sign up with duplicate email shows error`() =
        testApplication {
            application { module(testing = true) }

            val client =
                createClient {
                    install(HttpCookies)
                }

            client.post("/Sign-Up") {
                header(HttpHeaders.ContentType, ContentType.Application.FormUrlEncoded.toString())
                setBody("email=duplicate@test.com&password=password123")
            }

            val response =
                client.post("/Sign-Up") {
                    header(HttpHeaders.ContentType, ContentType.Application.FormUrlEncoded.toString())
                    setBody("email=duplicate@test.com&password=password123")
                }

            assertEquals(400, response.status.value) // stays on page with error
        }

    @Test
    fun should_redirect_to_login_when_recipe_dash_not_Authentication() =
        testApplication {
            application { module(testing = true) }
            val client =
                createClient {
                    followRedirects = false
                }
            val result = client.get("/recipes")
            assertEquals(302, result.status.value)
            assertEquals("/Login", result.headers[HttpHeaders.Location])
        }

    @Test
    fun should_redirect_to_login_when_profile_dash_not_Authentication() =
        testApplication {
            application { module(testing = true) }
            val client =
                createClient {
                    followRedirects = false
                }
            val result = client.get("/profile")
            assertEquals(302, result.status.value)
            assertEquals("/Login", result.headers[HttpHeaders.Location])
        }

    @Test
    fun should_redirect_to_login_when_professional_profile_dash_not_Authentication() =
        testApplication {
            application { module(testing = true) }
            val client =
                createClient {
                    followRedirects = false
                }
            val result = client.get("/professional-profile")
            assertEquals(302, result.status.value)
            assertEquals("/Professional-Login", result.headers[HttpHeaders.Location])
        }

    @Test
    fun should_redirect_to_login_when_message_page_not_Authentication() =
        testApplication {
            application { module(testing = true) }
            val client =
                createClient {
                    followRedirects = false
                }
            val result = client.get("/messages")
            assertEquals(302, result.status.value)
            assertEquals("/Login", result.headers[HttpHeaders.Location])
        }
}
