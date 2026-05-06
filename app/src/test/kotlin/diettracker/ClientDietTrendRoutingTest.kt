/*
 * Routing tests using Ktor's testApplication.
 * Each test resets and seeds the in-memory H2 test database, starts module(testing = true),
 * then uses a test HTTP client with cookies to log in and call the target route.
 * Acceptance criteria: P1-8, P5-4.
 */
package diettracker

import diettracker.db.tables.Clients
import diettracker.db.tables.Users
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
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

class ClientDietTrendRoutingTest {
    @BeforeEach
    fun setUP() {
        TestDatabaseFactory.init()
        transaction {
            Users.deleteAll()
            Clients.deleteAll()
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
            Clients.insert {
                it[client_id] = userId
                it[height_cm] = 180
                it[weight_kg] = 80
                it[daily_calorie_goal] = 2300
                it[goal] = "Lose weight"
                it[age] = 20
                it[gender] = "Male"
            }
        }
    }

    @Test
    fun should_change_month_when_url_query_parameters() =
        testApplication {
            application { module(testing = true) }
            val client =
                createClient {
                    followRedirects = false
                    install(HttpCookies)
                }

            client.post("/Login") {
                contentType(ContentType.Application.FormUrlEncoded)
                setBody(
                    listOf("email" to "test@test.com", "password" to "test@test.com").formUrlEncode(),
                )
            }
            val mayResult = client.get("/client_dash?year=2026&month=5")
            val mayBody = mayResult.bodyAsText()
            val juneResult = client.get("/client_dash?year=2026&month=6")
            val juneBody = juneResult.bodyAsText()
            assertEquals(200, mayResult.status.value)
            assertEquals(200, juneResult.status.value)
            assertTrue(mayBody.contains("2026 / 5"))
            assertTrue(juneBody.contains("2026 / 6"))
        }

    @Test
    fun should_link_to_last_year_december_when_current_month_is_january() =
        testApplication {
            application { module(testing = true) }
            val client =
                createClient {
                    followRedirects = false
                    install(HttpCookies)
                }

            client.post("/Login") {
                contentType(ContentType.Application.FormUrlEncoded)
                setBody(
                    listOf("email" to "test@test.com", "password" to "test@test.com").formUrlEncode(),
                )
            }
            val result = client.get("/client_dash?year=2026&month=1")
            val body = result.bodyAsText()
            // now page should show january
            assertEquals(200, result.status.value)
            assertTrue(body.contains("year=2025&month=12"))
            assertTrue(body.contains("year=2026&month=2"))
        }
}
