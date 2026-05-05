package diettracker

import TestDatabaseFactory
import diettracker.db.tables.ClientProfessionalLink
import diettracker.db.tables.Clients
import diettracker.db.tables.Professionals
import diettracker.db.tables.Roles
import diettracker.db.tables.UserRoles
import diettracker.db.tables.Users
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
import kotlin.text.get
import kotlin.toString

class ProfessionalRoutingTest {
    private var clientId: Int = 0
    private var professionalId: Int = 0

    @BeforeEach
    fun setUP() {
        TestDatabaseFactory.init()
        transaction {
            ClientProfessionalLink.deleteAll()
            UserRoles.deleteAll()
            Roles.deleteAll()
            Professionals.deleteAll()
            Users.deleteAll()
            val time = Instant.now()
            val salt = BCrypt.gensalt()
            professionalId =
                Users.insert {
                    it[first_name] = "Sponge"
                    it[second_name] = "Bob"
                    it[email] = "testpro@test.com"
                    it[password_hash] = BCrypt.hashpw("testpro@test.com", salt)
                    it[created_at] = time
                } get Users.user_id

            Professionals.insert {
                it[professional_id] = professionalId
                it[job_title] = "test"
                it[organistation] = "tester"
                it[bio] = "for test"
            }
            val userId =
                Users.insert {
                    it[first_name] = "Sponge"
                    it[second_name] = "Bob"
                    it[email] = "foodlog@test.com"
                    it[password_hash] = BCrypt.hashpw("foodlog@test.com", BCrypt.gensalt())
                    it[created_at] = time
                } get Users.user_id

            val clientRoleId =
                Roles.insert {
                    it[role_name] = "client"
                } get Roles.role_id

            UserRoles.insert {
                it[UserRoles.user_id] = userId
                it[UserRoles.role_id] = clientRoleId
            }

            val professionalRoleId =
                Roles.insert {
                    it[role_name] = "professional"
                } get Roles.role_id

            UserRoles.insert {
                it[user_id] = professionalId
                it[role_id] = professionalRoleId
            }
            clientId =
                Users.insert {
                    it[first_name] = "cilent"
                    it[second_name] = "test"
                    it[email] = "test@test.com"
                    it[password_hash] = BCrypt.hashpw("test@test.com", salt)
                    it[created_at] = time
                } get Users.user_id

            Clients.insert {
                it[client_id] = clientId
                it[height_cm] = 180
                it[weight_kg] = 80
                it[daily_calorie_goal] = 2300
                it[goal] = "Lose weight"
                it[age] = 20
                it[gender] = "Male"
            }

            UserRoles.insert {
                it[user_id] = clientId
                it[role_id] = clientRoleId
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
                assertEquals(302, result.status.value)
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
    fun professional_sign_up_should_fail_when_have_same_eamil() =
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
            assertEquals("/professionals_dash", result.headers[HttpHeaders.Location])
        }

    @Test
    fun should_load_professional_page_with_professional_list() =
        testApplication {
            application { module(testing = true) }
            val client =
                createClient {
                    install(HttpCookies)
                }
            client.post("/Login") {
                contentType(ContentType.Application.FormUrlEncoded)
                setBody(
                    listOf("email" to "test@test.com", "password" to "test@test.com")
                        .formUrlEncode(),
                )
            }
            val result = client.get("/professionals")
            val body = result.bodyAsText()
            assertEquals(200, result.status.value)
            assertTrue(body.contains("Find a Professional"))
        }

    @Test
    fun should_load_professional_dash_page_with_professional_list() =
        testApplication {
            application { module(testing = true) }
            val client =
                createClient {
                    install(HttpCookies)
                }
            client.post("/Professional-Login") {
                contentType(ContentType.Application.FormUrlEncoded)
                setBody(
                    listOf("email" to "testpro@test.com", "password" to "testpro@test.com")
                        .formUrlEncode(),
                )
            }
            val result = client.get("/professionals_dash")
            val body = result.bodyAsText()
            assertEquals(200, result.status.value)
            assertTrue(body.contains("Your Clients"))
            assertTrue(body.contains("Manage and view the progress of your linked clients."))
        }

    @Test
    fun should_show_client_detail_for_professional() =
        testApplication {
            application { module(testing = true) }
            val client =
                createClient {
                    install(HttpCookies)
                }
            client.post("/Professional-Login") {
                contentType(ContentType.Application.FormUrlEncoded)
                setBody(
                    listOf("email" to "testpro@test.com", "password" to "testpro@test.com")
                        .formUrlEncode(),
                )
            }
            val result = client.get("/professional/client/$clientId")
            val body = result.bodyAsText()
            assertEquals(200, result.status.value)
            assertTrue(body.contains("Client Overview"))
            assertTrue(body.contains("cilent test"))
            assertTrue(body.contains("test@test.com"))
            assertTrue(body.contains("Goal"))
            assertTrue(body.contains("Daily Calorie Target"))
            assertTrue(body.contains("Age"))
            assertTrue(body.contains("Gender"))
        }

    @Test
    fun should_redirect_to_login_when_selecting_professional_not_Authentication() =
        testApplication {
            application { module(testing = true) }
            val result =
                client.post("/select-professional") {
                    contentType(ContentType.Application.FormUrlEncoded)
                    setBody(
                        listOf("professional_id" to professionalId.toString())
                            .formUrlEncode(),
                    )
                }
            assertEquals(302, result.status.value)
            assertEquals("/Login", result.headers[HttpHeaders.Location])
        }

    @Test
    fun should_redirect_with_error_when_no_consent_given() =
        testApplication {
            application {
                module(testing = true)
            }

            val client =
                createClient {
                    install(HttpCookies)
                    followRedirects = false
                }
            client.post("/Login") {
                contentType(ContentType.Application.FormUrlEncoded)
                setBody(listOf("email" to "test@test.com", "password" to "test@test.com").formUrlEncode())
            }

            val result =
                client.post("/select-professional") {
                    contentType(ContentType.Application.FormUrlEncoded)
                    setBody(listOf("professional_id" to professionalId.toString(), "consent" to "false").formUrlEncode())
                }
            assertEquals("/professionals?error=consent", result.headers[HttpHeaders.Location])
        }

    @Test
    fun should_link_client_to_professional_after_select_professional() =
        testApplication {
            application { module(testing = true) }
            val client =
                createClient {
                    install(HttpCookies)
                    followRedirects = false
                }
            client.post("/Login") {
                contentType(ContentType.Application.FormUrlEncoded)
                setBody(
                    listOf("email" to "test@test.com", "password" to "test@test.com")
                        .formUrlEncode(),
                )
            }
            val result =
                client.post("/select-professional") {
                    contentType(ContentType.Application.FormUrlEncoded)
                    setBody(
                        listOf(
                            "professional_id" to professionalId.toString(),
                            "consent" to "true",
                        ).formUrlEncode(),
                    )
                }
            val link =
                transaction {
                    ClientProfessionalLink
                        .selectAll()
                        .where { ClientProfessionalLink.client_id eq clientId }
                        .single()
                }
            assertEquals(302, result.status.value)
            assertEquals("/professionals?linked=true", result.headers[HttpHeaders.Location])
            assertEquals(true, link[ClientProfessionalLink.consent_given])
        }

    /**
     * This functions tests that when a client is unlinked from a profession
     * The Client-Professional link entry is deleted from the database*/
    @Test
    fun should_unlink_client_from_professional() =
        testApplication {
            application { module(testing = true) }

            transaction {
                ClientProfessionalLink.insert {
                    it[ClientProfessionalLink.client_id] = clientId
                    it[ClientProfessionalLink.professional_id] = professionalId
                }
            }

            val client =
                createClient {
                    install(HttpCookies)
                    followRedirects = false
                }

            client.post("/Login") {
                contentType(ContentType.Application.FormUrlEncoded)
                setBody(listOf("email" to "test@test.com", "password" to "test@test.com").formUrlEncode())
            }

            client.post("/unlink-professional") {
                contentType(ContentType.Application.FormUrlEncoded)
                setBody(listOf("professional_id" to professionalId.toString()).formUrlEncode())
            }
            val count =
                transaction {
                    ClientProfessionalLink.selectAll().count()
                }
            assertEquals(0, count)
        }
}
