package diettracker

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

class ProfileTest {
    private var userId: Int = 0
    private var clientId: Int = 0
    private var professionalId: Int = 0

    @BeforeEach
    fun setUp() {
        TestDatabaseFactory.init()
        transaction {
            UserRoles.deleteAll()
            Roles.deleteAll()
            Professionals.deleteAll()
            Clients.deleteAll()
            Users.deleteAll()

            val time = Instant.now()
            userId =
                Users.insert {
                    it[first_name] = "Sponge"
                    it[second_name] = "Bob"
                    it[email] = "test@test.com"
                    it[password_hash] = BCrypt.hashpw("test@test.com", BCrypt.gensalt())
                    it[created_at] = time
                } get Users.user_id
            clientId =
                Clients.insert {
                    it[client_id] = userId
                    it[age] = 20
                    it[height_cm] = 180
                    it[weight_kg] = 80
                    it[gender] = "male"
                    it[goal] = "maintain"
                    it[daily_calorie_goal] = 2500
                } get Clients.client_id

            val proId =
                Users.insert {
                    it[first_name] = "testpro"
                    it[second_name] = "pro"
                    it[email] = "testpro@test.com"
                    it[password_hash] = BCrypt.hashpw("testpro@test.com", BCrypt.gensalt())
                    it[created_at] = time
                } get Users.user_id

            val clientRoleId =
                Roles.insert {
                    it[role_name] = "client"
                } get Roles.role_id

            UserRoles.insert {
                it[user_id] = userId
                it[role_id] = clientRoleId
            }

            val roleId =
                Roles.insert {
                    it[role_name] = "professional"
                } get Roles.role_id

            UserRoles.insert {
                it[user_id] = proId
                it[role_id] = roleId
            }

            professionalId =
                Professionals.insert {
                    it[professional_id] = proId
                    it[job_title] = "testpro"
                    it[organistation] = "testorganisation"
                    it[bio] = "testbio"
                } get Professionals.professional_id
        }
    }

    @Test
    fun should_load_food_profile_page() =
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
            val result = client.get("/profile")
            assertEquals(200, result.status.value)
        }

    @Test
    fun should_load_profile_page_deatil_when_logged_in() =
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

            val result = client.get("/profile")
            val body = result.bodyAsText()
            val clientId = clientId
            assertEquals(200, result.status.value)
            assertTrue(body.contains("Profile"))
            assertTrue(body.contains("test@test.com"))
            assertTrue(body.contains(clientId.toString()))
            assertTrue(body.contains("2500"))
            assertTrue(body.contains("180"))
        }

    @Test
    fun should_update_profile_and_redirect_back_to_profile_page() =
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
            val result =
                client.post("/profileupdate") {
                    contentType(ContentType.Application.FormUrlEncoded)
                    setBody(
                        listOf(
                            "firstName" to "testfirstName",
                            "lastName" to "testlastName",
                            "height" to "190",
                            "weight" to "90",
                            "age" to "30",
                            "gender" to "male",
                            "goal" to "gain",
                        ).formUrlEncode(),
                    )
                }
            assertEquals(302, result.status.value)
            assertEquals("/profile", result.headers[HttpHeaders.Location])
            transaction {
                val updateClient =
                    Clients
                        .selectAll()
                        .where { Clients.client_id eq userId }
                        .single()
                assertEquals("testfirstName", updateClient[Clients.firstName])
                assertEquals("testlastName", updateClient[Clients.lastName])
                assertEquals(190, updateClient[Clients.height_cm])
                assertEquals(90, updateClient[Clients.weight_kg])
                assertEquals(30, updateClient[Clients.age])
                assertEquals("male", updateClient[Clients.gender])
                assertEquals("gain", updateClient[Clients.goal])
            }
        }

    @Test
    fun should_not_crash_when_user_has_no_profile() =
        testApplication {
            application { module(testing = true) }
            transaction {
                Clients.deleteAll()
            }
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
            val result = client.get("/profile")
            assertEquals(200, result.status.value)
        }

    @Test
    fun should_load_professional_profile_page_when_logged() =
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
                    listOf(
                        "email" to "testpro@test.com",
                        "password" to "testpro@test.com",
                    ).formUrlEncode(),
                )
            }
            val result = client.get("/professional-profile")
            val body = result.bodyAsText()
            println("BODY = $body")
            println("PROFILE LOCATION = ${result.headers[HttpHeaders.Location]}")
            assertEquals(200, result.status.value)
            assertTrue(body.contains("Profile"))
            assertTrue(body.contains(professionalId.toString()))
            assertTrue(body.contains("testpro@test.com"))
            assertTrue(body.contains("testpro"))
            assertTrue(body.contains("testorganisation"))
        }

    @Test
    fun should_update_professional_profile() =
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
                    listOf(
                        "email" to "testpro@test.com",
                        "password" to "testpro@test.com",
                    ).formUrlEncode(),
                )
            }

            val result =
                client.post("/professional-profile-update") {
                    contentType(ContentType.Application.FormUrlEncoded)
                    setBody(
                        listOf(
                            "job_title" to "new title",
                            "organisation" to "new organistation",
                            "bio" to "new bio",
                        ).formUrlEncode(),
                    )
                }
            assertEquals(302, result.status.value)
            assertEquals("/professional-profile", result.headers[HttpHeaders.Location])
            transaction {
                val update =
                    Professionals
                        .selectAll()
                        .where { Professionals.professional_id eq professionalId }
                        .single()

                assertEquals("new title", update[Professionals.job_title])
                assertEquals("new organistation", update[Professionals.organistation])
                assertEquals("new bio", update[Professionals.bio])
            }
        }
}
