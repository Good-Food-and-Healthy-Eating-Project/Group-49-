package diettracker

import TestDatabaseFactory
import diettracker.db.tables.Users
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.formUrlEncode
import io.ktor.server.testing.testApplication
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

class RoutingTest {
    @BeforeEach
    fun setUP() {
        TestDatabaseFactory.init()
        transaction {
            Users.deleteAll()
            val time = Instant.now()
            val salt = BCrypt.gensalt()
            Users.insert {
                it[first_name] = "Sponge"
                it[second_name] = "Bob"
                it[email] = "test@test.com"
                it[password_hash] = BCrypt.hashpw("test@test.com", salt)
                it[created_at] = time
            }
        }
    }

    @Test
    fun should_return_ok_for_heath_check() =
        testApplication {
            application { module(testing = true) }
            val result = client.get("/health")
            assertEquals(200, result.status.value)
        }

    @Test
    fun should_load_landing_page() =
        testApplication {
            application { module(testing = true) }
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
                assertTrue(users.any { it[Users.email] == "test@test.com" })
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

    // now return 200 but it should be 400
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
                        )
                            .formUrlEncode(),
                    )
                }
            assertEquals(200, result.status.value)
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
}
