package diettracker

import diettracker.db.tables.FoodLogItems
import diettracker.db.tables.FoodLogs
import diettracker.db.tables.Foods
import diettracker.db.tables.RecipeIngredients
import diettracker.db.tables.Recipes
import diettracker.db.tables.Users
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
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.BeforeEach
import org.mindrot.jbcrypt.BCrypt
import java.math.BigDecimal
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FoodLogRoutingTest {
    @BeforeEach
    fun setUp() {
        TestDatabaseFactory.init()
        transaction {
            FoodLogItems.deleteAll()
            FoodLogs.deleteAll()
            Foods.deleteAll()
            RecipeIngredients.deleteAll()
            Recipes.deleteAll()
            Users.deleteAll()

            val time = Instant.now()

            val userId =
                Users.insert {
                    it[first_name] = "Sponge"
                    it[second_name] = "Bob"
                    it[email] = "foodlog@test.com"
                    it[password_hash] = BCrypt.hashpw("foodlog@test.com", BCrypt.gensalt())
                    it[created_at] = time
                } get Users.user_id

            val appleId =
                Foods.insert {
                    it[food_name] = "apple"
                    it[calories_per_100g] = BigDecimal("110.00")
                    it[protein_per_100g] = BigDecimal("5.00")
                    it[carbs_per_100g] = BigDecimal("15.00")
                    it[fat_per_100g] = BigDecimal("0.50")
                } get Foods.food_id

            val bananaId =
                Foods.insert {
                    it[food_name] = "banana"
                    it[calories_per_100g] = BigDecimal("55.00")
                    it[protein_per_100g] = BigDecimal("7.00")
                    it[carbs_per_100g] = BigDecimal("7.00")
                    it[fat_per_100g] = BigDecimal("0.70")
                } get Foods.food_id

            val recipeId =
                Recipes.insert {
                    it[recipe_name] = "test mix"
                    it[instructions] = "mix"
                    it[created_by_user_id] = userId
                    it[is_system_recipe] = false
                } get Recipes.recipes_id

            RecipeIngredients.insert {
                it[recipe_id] = recipeId
                it[food_id] = bananaId
                it[quantity_g] = BigDecimal("200.00")
                it[original_measure] = "200g"
            }

            RecipeIngredients.insert {
                it[recipe_id] = recipeId
                it[food_id] = appleId
                it[quantity_g] = BigDecimal("100.00")
                it[original_measure] = "100g"
            }
        }
    }

    @Test
    fun should_load_food_log_page() =
        testApplication {
            application { module(testing = true) }
            val client =
                createClient {
                    install(io.ktor.client.plugins.cookies.HttpCookies)
                    followRedirects = false
                }
            client.post("/Login") {
                contentType(ContentType.Application.FormUrlEncoded)
                setBody(listOf("email" to "foodlog@test.com", "password" to "foodlog@test.com").formUrlEncode())
            }
            val result = client.get("/food_log")

            assertEquals(200, result.status.value)
        }

    @Test
    fun should_search_recipe_in_food_log() =
        testApplication {
            application { module(testing = true) }
            val client =
                createClient {
                    install(io.ktor.client.plugins.cookies.HttpCookies)
                    followRedirects = false
                }
            client.post("/Login") {
                contentType(ContentType.Application.FormUrlEncoded)
                setBody(listOf("email" to "foodlog@test.com", "password" to "foodlog@test.com").formUrlEncode())
            }
            val result = client.get("/food_log?query=Test")
            val body = result.bodyAsText()

            assertEquals(200, result.status.value)
            assertTrue(body.contains("test mix"))
        }

    @Test
    fun should_search_food_in_log() =
        testApplication {
            application { module(testing = true) }
            val client =
                createClient {
                    install(io.ktor.client.plugins.cookies.HttpCookies)
                    followRedirects = false
                }
            client.post("/Login") {
                contentType(ContentType.Application.FormUrlEncoded)
                setBody(listOf("email" to "foodlog@test.com", "password" to "foodlog@test.com").formUrlEncode())
            }
            val result = client.get("/food_log?foodquery=apple")
            val body = result.bodyAsText()

            assertEquals(200, result.status.value)
            assertTrue(body.contains("apple"))
        }

    @Test
    fun should_add_custom_food_calories() =
        testApplication {
            application { module(testing = true) }

            val client =
                createClient {
                    install(io.ktor.client.plugins.cookies.HttpCookies)
                    followRedirects = false
                }

            client.post("/Login") {
                contentType(ContentType.Application.FormUrlEncoded)
                setBody(listOf("email" to "foodlog@test.com", "password" to "foodlog@test.com").formUrlEncode())
            }

            val foodId =
                transaction {
                    Foods
                        .selectAll()
                        .first { it[Foods.food_name] == "apple" }[Foods.food_id]
                }

            val result =
                client.post("/food_log_custom") {
                    contentType(ContentType.Application.FormUrlEncoded)
                    setBody(
                        listOf("foodId" to foodId.toString(), "grams" to "200")
                            .formUrlEncode(),
                    )
                }
            val page = client.get("/food_log")
            val body = page.bodyAsText()
            assertEquals(302, result.status.value)
            assertEquals(200, page.status.value)
            assertTrue(body.contains("Total Calories: 220")) // grams / 100 * 110
        }

    @Test
    fun should_add_recipe_calories() =
        testApplication {
            application { module(testing = true) }

            val client =
                createClient {
                    install(io.ktor.client.plugins.cookies.HttpCookies)
                    followRedirects = false
                }

            transaction {
                Users.insert {
                    it[email] = "test@test.com"
                    it[password_hash] = BCrypt.hashpw("test", BCrypt.gensalt())
                    it[first_name] = "Test"
                    it[second_name] = "User"
                    it[created_at] = java.time.Instant.now()
                }
            }
            client.post("/Login") {
                contentType(ContentType.Application.FormUrlEncoded)
                setBody(
                    listOf(
                        "email" to "test@test.com",
                        "password" to "test",
                    ).formUrlEncode(),
                )
            }

            val recipeId =
                transaction {
                    Recipes
                        .selectAll()
                        .first { it[Recipes.recipe_name] == "test mix" }[Recipes.recipes_id]
                }

            val result =
                client.post("/food_log_recipe") {
                    contentType(ContentType.Application.FormUrlEncoded)
                    setBody(listOf("recipeId" to recipeId.toString()).formUrlEncode())
                }
            val page = client.get("/food_log")
            val body = page.bodyAsText()

            assertEquals(302, result.status.value)
            assertEquals(200, page.status.value)
            assertTrue(body.contains("Total Calories: 220")) // 110 + (55 / 100 * 200)
        }

    @Test
    fun should_reset_food_log_calories() =
        testApplication {
            application { module(testing = true) }

            val foodId =
                transaction {
                    Foods
                        .selectAll()
                        .first { it[Foods.food_name] == "apple" }[Foods.food_id]
                }
            client.post("/food_log_custom") {
                contentType(ContentType.Application.FormUrlEncoded)
                setBody(
                    listOf("foodId" to foodId.toString(), "grams" to "200")
                        .formUrlEncode(),
                )
            }

            val result = client.post("/food_log_reset")
            val body = result.bodyAsText()

            assertEquals(200, result.status.value)
            assertTrue(body.contains("Total Calories: 0"))
        }

    @Test
    fun should_fail_when_food_id_missing() =
        testApplication {
            application { module(testing = true) }
            val result =
                client.post("/food_log_custom") {
                    contentType(ContentType.Application.FormUrlEncoded)
                    setBody(
                        listOf("grams" to "200")
                            .formUrlEncode(),
                    )
                }

            val body = result.bodyAsText()

            assertEquals(200, result.status.value)
            assertTrue(body.contains("Total Calories: 0"))
        }

    @Test
    fun should_fail_when_recipe_id_missing() =
        testApplication {
            application { module(testing = true) }

            val result =
                client.post("/food_log_recipe") {
                    contentType(ContentType.Application.FormUrlEncoded)
                    setBody(
                        listOf("" to "")
                            .formUrlEncode(),
                    )
                }
            val body = result.bodyAsText()

            assertEquals(200, result.status.value)
            assertTrue(body.contains("Total Calories: 0"))
        }
}
