/**
 * General routing tests using Ktor's testApplication.
 * Each test starts the app with module(testing = true), uses the in-memory H2 test database
 * when setup data is needed, and sends requests through Ktor's test HTTP client.
 */
package diettracker

import TestDatabaseFactory
import diettracker.db.tables.Roles
import diettracker.db.tables.UserRoles
import diettracker.db.tables.Users
import io.ktor.client.HttpClient
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.formUrlEncode
import io.ktor.server.testing.testApplication
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteAll
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.BeforeEach
import org.mindrot.jbcrypt.BCrypt
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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

class RoutingTest {
    @BeforeEach
    fun setUP() {
        TestDatabaseFactory.init()
        transaction {
            Users.deleteAll()
            val time = Instant.now()
            val salt = BCrypt.gensalt()
            val userId =
                Users.insert {
                    it[first_name] = "Sponge"
                    it[second_name] = "Bob"
                    it[email] = "test@test.com"
                    it[password_hash] = BCrypt.hashpw("test@test.com", salt)
                    it[created_at] = time
                } get Users.user_id

            val clientRoleId =
                Roles.selectAll()
                    .where { Roles.role_name eq "client" }
                    .map { it[Roles.role_id] }
                    .singleOrNull()
            if (clientRoleId != null) {
                UserRoles.insert {
                    it[UserRoles.user_id] = userId
                    it[UserRoles.role_id] = clientRoleId
                }
            }
        }
    }

    @Test
    fun should_return_ok_for_heath_check() =
        testApplication {
            application { module(testing = true) }

            val client =
                createClient {
                    install(HttpCookies)
                    followRedirects = false
                }

            client.loginTestUser()

            val result = client.get("/health")
            assertEquals(200, result.status.value)
        }

    @Test
    fun should_load_landing_page() =
        testApplication {
            application { module(testing = true) }
            val client =
                createClient {
                    install(HttpCookies)
                    followRedirects = false
                }

            client.loginTestUser()

            val result = client.get("/")
            assertEquals(200, result.status.value)
        }

    @Test
    fun should_load_login_page() =
        testApplication {
            application { module(testing = true) }
            val result = client.get("/Login")
            assertEquals(200, result.status.value)
        }

    @Test
    fun should_load_signup_page() =
        testApplication {
            application { module(testing = true) }
            val result = client.get("/Sign-Up")
            assertEquals(200, result.status.value)
        }

    @Test
    fun should_load_professional_signup_page() =
        testApplication {
            application { module(testing = true) }
            val result = client.get("/Professional-Sign-Up")
            assertEquals(200, result.status.value)
        }

    @Test
    fun should_load_professional_login_page() =
        testApplication {
            application { module(testing = true) }
            val result = client.get("/Professional-Login")
            assertEquals(200, result.status.value)
        }

    @Test
    fun should_load_food_diary_day_page() =
        testApplication {
            application { module(testing = true) }
            val client =
                createClient {
                    install(HttpCookies)
                    followRedirects = false
                }

            client.loginTestUser()

            val result = client.get("/food_diary_day")
            assertEquals(200, result.status.value)
        }

    @Test
    fun should_load_professional_page() =
        testApplication {
            application { module(testing = true) }
            val client =
                createClient {
                    install(HttpCookies)
                    followRedirects = false
                }

            client.loginTestUser()

            val result = client.get("/professionals")
            assertEquals(200, result.status.value)
        }

    @Test
    fun should_load_food_recipe_page() =
        testApplication {
            application { module(testing = true) }
            val client =
                createClient {
                    install(HttpCookies)
                    followRedirects = false
                }

            client.loginTestUser()

            val result = client.get("/recipes")
            assertEquals(200, result.status.value)
        }

    @Test
    fun should_signup_user() =
        testApplication {
            application { module(testing = true) }
            val result =
                client.post("/Sign-Up") {
                    contentType(ContentType.Application.FormUrlEncoded)
                    setBody(
                        listOf("email" to "newuser@test.com", "password" to "test@test.com")
                            .formUrlEncode(),
                    )
                }
            transaction {
                val users = Users.selectAll().toList()
                assertTrue(users.any { it[Users.email] == "newuser@test.com" })
                assertTrue(result.status.value == 302)
            }
        }

    @Test
    fun should_signup_fail_when_missing_email() =
        testApplication {
            application { module(testing = true) }
            val result =
                client.post("/Sign-Up") {
                    contentType(ContentType.Application.FormUrlEncoded)
                    setBody(listOf("password" to "test@test.com").formUrlEncode())
                }
            assertEquals(400, result.status.value)
        }

    @Test
    fun should_signup_fail_when_missing_password() =
        testApplication {
            application { module(testing = true) }
            val result =
                client.post("/Sign-Up") {
                    contentType(ContentType.Application.FormUrlEncoded)
                    setBody(listOf("email" to "test@test.com").formUrlEncode())
                }
            assertEquals(400, result.status.value)
        }

    @Test
    fun should_signup_fail_when_have_same_eamil() =
        testApplication {
            application { module(testing = true) }
            val beforCount = transaction { Users.selectAll().count() }
            val result =
                client.post("/Sign-Up") {
                    contentType(ContentType.Application.FormUrlEncoded)
                    setBody(
                        listOf("email" to "test@test.com", "password" to "test@test.com")
                            .formUrlEncode(),
                    )
                }
            val afterCount = transaction { Users.selectAll().count() }
            assertEquals(beforCount, afterCount)
            assertEquals(400, result.status.value)
        }

    @Test
    fun should_login_success_when_password_right() =
        testApplication {
            application { module(testing = true) }
            val result =
                client.post("/Login") {
                    contentType(ContentType.Application.FormUrlEncoded)
                    setBody(
                        listOf("email" to "test@test.com", "password" to "test@test.com")
                            .formUrlEncode(),
                    )
                }
            assertEquals(302, result.status.value)
        }

    @Test
    fun should_login_fail_when_password_wrong() =
        testApplication {
            application { module(testing = true) }
            val result =
                client.post("/Login") {
                    contentType(ContentType.Application.FormUrlEncoded)
                    setBody(
                        listOf(
                            "email" to "test@test.com",
                            "password" to "wrongpassword@test.com",
                        ).formUrlEncode(),
                    )
                }
            val body = result.bodyAsText()
            assertEquals(200, result.status.value)
            assertTrue(body.contains("Invalid email or password"))
        }

    @Test
    fun should_login_fail_when_missing_email() =
        testApplication {
            application { module(testing = true) }
            val result =
                client.post("/Login") {
                    contentType(ContentType.Application.FormUrlEncoded)
                    setBody(listOf("password" to "test@test.com").formUrlEncode())
                }
            assertEquals(400, result.status.value)
        }

    @Test
    fun should_login_fail_when_missing_password() =
        testApplication {
            application { module(testing = true) }
            val result =
                client.post("/Login") {
                    contentType(ContentType.Application.FormUrlEncoded)
                    setBody(listOf("email" to "test@test.com").formUrlEncode())
                }
            assertEquals(400, result.status.value)
        }

    @Test
    fun should_login_fail_when_missing_password_and_email() =
        testApplication {
            application { module(testing = true) }
            val result =
                client.post("/Login") {
                    contentType(ContentType.Application.FormUrlEncoded)
                    setBody(listOf("" to "").formUrlEncode())
                }
            assertEquals(400, result.status.value)
        }

    @Test
    fun should_redirect_to_client_dash_when_login_success() =
        testApplication {
            application { module(testing = true) }
            val result =
                client.post("/Login") {
                    contentType(ContentType.Application.FormUrlEncoded)
                    setBody(
                        listOf(
                            "email" to "test@test.com",
                            "password" to "test@test.com",
                        ).formUrlEncode(),
                    )
                }
            assertEquals(302, result.status.value)
            assertEquals("/client_dash", result.headers[HttpHeaders.Location])
        }
}
