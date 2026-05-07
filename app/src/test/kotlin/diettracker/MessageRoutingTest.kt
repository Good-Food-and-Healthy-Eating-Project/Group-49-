/**
 * Routing tests using Ktor's testApplication.
 * Each test resets and seeds the in-memory H2 test database, starts module(testing = true),
 * then uses a cookie-enabled test HTTP client to log in and verify messaging routes.
 */
package diettracker

import TestDatabaseFactory
import diettracker.db.tables.Chats
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
import org.jetbrains.exposed.v1.jdbc.deleteAll
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.BeforeEach
import org.mindrot.jbcrypt.BCrypt
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MessageRoutingTest {
    private var clientId: Int = 0
    private var professionalId: Int = 0
    private var otherClientId: Int = 0

    private fun createUser(
        email: String,
        firstName: String,
        secondName: String,
        time: Instant,
    ): Int =
        Users.insert {
            it[Users.email] = email
            it[password_hash] = BCrypt.hashpw(email, BCrypt.gensalt())
            it[first_name] = firstName
            it[second_name] = secondName
            it[created_at] = time
        } get Users.user_id

    private fun createClientProfile(
        userId: Int,
        height: Int,
        weight: Int,
        calorieGoal: Int,
    ) {
        Clients.insert {
            it[client_id] = userId
            it[height_cm] = height
            it[weight_kg] = weight
            it[daily_calorie_goal] = calorieGoal
            it[goal] = "Lose weight"
            it[age] = 20
            it[gender] = "Male"
        }
    }

    @BeforeEach
    fun setUp() {
        TestDatabaseFactory.init()
        transaction {
            Chats.deleteAll()
            ClientProfessionalLink.deleteAll()
            UserRoles.deleteAll()
            Roles.deleteAll()
            Clients.deleteAll()
            Professionals.deleteAll()
            Users.deleteAll()
            val time = Instant.now()

            val clientRoleId =
                Roles.insert {
                    it[role_name] = "client"
                } get Roles.role_id

            val professionalRoleId =
                Roles.insert {
                    it[role_name] = "professional"
                } get Roles.role_id

            clientId = createUser("test@test.com", "Test", "User", time)
            otherClientId = createUser("test1@test.com", "Test", "Other", time)
            professionalId = createUser("testpro@test.com", "Testpro", "User", time)

            UserRoles.insert {
                it[user_id] = clientId
                it[role_id] = clientRoleId
            }

            UserRoles.insert {
                it[user_id] = otherClientId
                it[role_id] = clientRoleId
            }

            UserRoles.insert {
                it[user_id] = professionalId
                it[role_id] = professionalRoleId
            }
            createClientProfile(clientId, 180, 80, 2300)
            createClientProfile(otherClientId, 170, 70, 2000)
            Professionals.insert {
                it[professional_id] = professionalId
                it[job_title] = "test"
                it[organistation] = "tester"
                it[bio] = "for test"
            }

            ClientProfessionalLink.insert {
                it[ClientProfessionalLink.client_id] = clientId
                it[ClientProfessionalLink.professional_id] = professionalId
            }
        }
    }

    @Test
    fun should_show_message_after_professional_start_covesation() =
        testApplication {
            application { module(testing = true) }

            val client =
                createClient {
                    install(HttpCookies)
                    followRedirects = false
                }

            client.post("/Professional-Login") {
                contentType(ContentType.Application.FormUrlEncoded)
                setBody(
                    listOf("email" to "testpro@test.com", "password" to "testpro@test.com").formUrlEncode(),
                )
            }

            val result =
                client.post("/messages/start") {
                    contentType(ContentType.Application.FormUrlEncoded)
                    setBody(
                        listOf("client_id" to clientId.toString()).formUrlEncode(),
                    )
                }
            val location = result.headers[HttpHeaders.Location] ?: "empty URL"
            val page = client.get(location)
            val body = page.bodyAsText()
            assertEquals(302, result.status.value)
            assertEquals(200, page.status.value)
            assertTrue(body.contains("No messages in this conversation yet."))
        }

    @Test
    fun should_return_not_allowed_when_user_open_other_user_covesation() =
        testApplication {
            application { module(testing = true) }
            // login first user
            val ownerUser =
                createClient {
                    install(HttpCookies)
                    followRedirects = false
                }

            ownerUser.post("/Login") {
                contentType(ContentType.Application.FormUrlEncoded)
                setBody(
                    listOf("email" to "test@test.com", "password" to "test@test.com").formUrlEncode(),
                )
            }
            // start conversation with professional
            val startResult =
                ownerUser.post("/messages/start") {
                    contentType(ContentType.Application.FormUrlEncoded)
                    setBody(
                        listOf("professional_id" to professionalId.toString()).formUrlEncode(),
                    )
                }

            val location = startResult.headers[HttpHeaders.Location] ?: "empty URL"
            // login secound user
            val otherUser =
                createClient {
                    install(HttpCookies)
                    followRedirects = false
                }
            otherUser.post("/Login") {
                contentType(ContentType.Application.FormUrlEncoded)
                setBody(
                    listOf("email" to "test1@test.com", "password" to "test1@test.com").formUrlEncode(),
                )
            }
            // secound user try access first user URl
            val result = otherUser.get(location)
            val body = result.bodyAsText()
            assertEquals(403, result.status.value)
            assertTrue(body.contains("Not allowed"))
        }

    @Test
    fun should_redirect_when_message_is_empty() =
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
                    listOf("email" to "test@test.com", "password" to "test@test.com").formUrlEncode(),
                )
            }
            val startResult =
                client.post("/messages/start") {
                    contentType(ContentType.Application.FormUrlEncoded)
                    setBody(
                        listOf("professional_id" to professionalId.toString()).formUrlEncode(),
                    )
                }
            val location = startResult.headers[HttpHeaders.Location] ?: "empty URL"
            val result =
                client.post(location) {
                    contentType(ContentType.Application.FormUrlEncoded)
                    setBody(listOf("body" to "   ").formUrlEncode())
                }

            assertEquals(302, result.status.value)
            assertEquals("$location?error=Message cannot be empty.", result.headers[HttpHeaders.Location])
        }
}
