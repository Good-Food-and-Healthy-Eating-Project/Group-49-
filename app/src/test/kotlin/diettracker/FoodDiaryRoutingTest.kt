package diettracker

import diettracker.db.tables.Users
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
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

class FoodDiaryRoutingTest {
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
    fun should_access_food_diary_after_login() =
        testApplication {
            application { module(testing = true) }

            val client =
                createClient {
                    install(HttpCookies)
                }

            client.post("/Login") {
                contentType(ContentType.Application.FormUrlEncoded)
                setBody(
                    listOf("email" to "test@test.com", "password" to "test@test.com").formUrlEncode(),
                )
            }
            val response = client.get("/food_diary")

            assertEquals(200, response.status.value)
        }

    @Test
    fun should_open_food_diary_with_vaild_week_parameter() =
        testApplication {
            application { module(testing = true) }

            val client =
                createClient {
                    install(HttpCookies)
                }

            client.post("/Login") {
                contentType(ContentType.Application.FormUrlEncoded)
                setBody(
                    listOf("email" to "test@test.com", "password" to "test@test.com").formUrlEncode(),
                )
            }

            val response = client.get("/food_diary?week=2026-04-13")

            assertEquals(200, response.status.value)
        }

    @Test
    fun should_open_food_diary_with_invaild_week_parameter() =
        testApplication {
            application { module(testing = true) }

            val client =
                createClient {
                    install(HttpCookies)
                }

            client.post("/Login") {
                contentType(ContentType.Application.FormUrlEncoded)
                setBody(
                    listOf("email" to "test@test.com", "password" to "test@test.com").formUrlEncode(),
                )
            }

            val response = client.get("/food_diary?week=not-a-date")

            assertEquals(200, response.status.value)
        }
}
