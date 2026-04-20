package diettracker

import TestDatabaseFactory
import diettracker.db.tables.Users
import diettracker.db.tables.Roles
import diettracker.db.tables.UserRoles
import diettracker.db.tables.Professionals
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
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

class ProfessionalRoutingTest {
    @BeforeEach
    fun setUP() {
        TestDatabaseFactory.init()
        transaction {
            UserRoles.deleteAll()
            Roles.deleteAll()
            Professionals.deleteAll()
            Users.deleteAll()
            val time = Instant.now()
            val salt = BCrypt.gensalt()
            val userId = 
            Users.insert {
                it[first_name] = "Sponge"
                it[second_name] = "Bob"
                it[email] = "testpro@test.com"
                it[password_hash] = BCrypt.hashpw("testpro@test.com", salt)
                it[created_at] = time
            }get Users.user_id

            Professionals.insert {
                it[professional_id] = userId
                it[job_title] = "test"
                it[organistation] = "tester"
                it[bio] = "for test"
            }

            val roleId = 
            Roles.insert {
                it[role_name] = "professional"
            }get Roles.role_id

            UserRoles.insert {
            it[user_id] = userId
            it[role_id] = roleId
        }
        }
    }

    @Test
    fun should_signup_professional_user() =
        testApplication {
            application { module(testing = true) }
            val result =
                client.post("/Professional-Sign-Up") {
                    contentType(ContentType.Application.FormUrlEncoded)
                    setBody(
                        listOf("email" to "testpronew@test.com", "password" to "testpro@test.com")
                            .formUrlEncode(),
                    )
                }
            transaction {
                val users = Users.selectAll().toList()
                assertTrue(users.any { it[Users.email] == "testpronew@test.com" })
                assertTrue(result.status.value == 200)
            }
        }

    @Test
    fun professional_sign_up_should_fail_when_missing_email() =
        testApplication {
            application { module(testing = true) }
            val result =
                client.post("/Professional-Sign-Up") {
                    contentType(ContentType.Application.FormUrlEncoded)
                    setBody(listOf("password" to "testpro@test.com").formUrlEncode())
                }
            assertEquals(400, result.status.value)
        }

    @Test
    fun professional_sign_up_should_fail_when_missing_password() =
        testApplication {
            application { module(testing = true) }
            val result =
                client.post("/Professional-Sign-Up") {
                    contentType(ContentType.Application.FormUrlEncoded)
                    setBody(listOf("email" to "testpro@test.com").formUrlEncode())
                }
            assertEquals(400, result.status.value)
        }

    @Test
    fun  professional_sign_up_should_fail_when_have_same_eamil() =
        testApplication {
            application { module(testing = true) }
            val beforCount = transaction { Users.selectAll().count() }
            val result =
                client.post("/Professional-Sign-Up") {
                    contentType(ContentType.Application.FormUrlEncoded)
                    setBody(
                        listOf("email" to "testpro@test.com", "password" to "testpro@test.com")
                            .formUrlEncode(),
                    )
                }
            val afterCount = transaction { Users.selectAll().count() }
            assertEquals(beforCount, afterCount)
            assertEquals(400, result.status.value)
        }

    @Test
    fun should_login_professional_success_when_password_right() =
        testApplication {
            application { module(testing = true) }
            val result =
                client.post("/Professional-Login") {
                    contentType(ContentType.Application.FormUrlEncoded)
                    setBody(
                        listOf("email" to "testpro@test.com", "password" to "testpro@test.com")
                            .formUrlEncode(),
                    )
                }
            assertEquals(302, result.status.value)
        }

    @Test
    fun should_login_professional_fail_when_missing_email() =
        testApplication {
            application { module(testing = true) }
            val result =
                client.post("/Professional-Login") {
                    contentType(ContentType.Application.FormUrlEncoded)
                    setBody(listOf("password" to "testpro@test.com").formUrlEncode())
                }
            assertEquals(400, result.status.value)
        }

    @Test
    fun should_login_professional_fail_when_missing_password() =
        testApplication {
            application { module(testing = true) }
            val result =
                client.post("/Professional-Login") {
                    contentType(ContentType.Application.FormUrlEncoded)
                    setBody(listOf("email" to "testpro@test.com").formUrlEncode())
                }
            assertEquals(400, result.status.value)
        }

    @Test
    fun should_login_professional_fail_when_missing_password_and_email() =
        testApplication {
            application { module(testing = true) }
            val result =
                client.post("/Professional-Login") {
                    contentType(ContentType.Application.FormUrlEncoded)
                    setBody(listOf("" to "").formUrlEncode())
                }
            assertEquals(400, result.status.value)
        }

    @Test
    fun should_redirect_to_professionals_page_when_login_success() =
        testApplication {
            application { module(testing = true) }
            val result =
                client.post("/Professional-Login") {
                    contentType(ContentType.Application.FormUrlEncoded)
                    setBody(
                        listOf(
                            "email" to "testpro@test.com",
                            "password" to "testpro@test.com",
                        ).formUrlEncode(),
                    )
                }
            assertEquals(302, result.status.value)
            assertEquals("/professionals", result.headers[HttpHeaders.Location])
        }
}
