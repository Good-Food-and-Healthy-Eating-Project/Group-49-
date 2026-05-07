/*
 * Routing tests using Ktor's testApplication.
 * Each test resets and seeds the in-memory H2 test database, starts module(testing = true),
 * then uses the test HTTP client to request recipe routes and verify responses.
 */
package diettracker

import diettracker.db.tables.Foods
import diettracker.db.tables.RecipeIngredients
import diettracker.db.tables.RecipeReviews
import diettracker.db.tables.Recipes
import diettracker.db.tables.UserFavouritedRecipes
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
import java.math.BigDecimal
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RecipeRoutingTest {
    private var userId: Int = 0
    private var recipeId: Int = 0
    private var foodId: Int = 0

    @BeforeEach
    fun setUp() {
        TestDatabaseFactory.init()
        transaction {
            RecipeReviews.deleteAll()
            UserFavouritedRecipes.deleteAll()
            RecipeIngredients.deleteAll()
            Recipes.deleteAll()
            Foods.deleteAll()
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

            foodId =
                Foods.insert {
                    it[food_name] = "Apple"
                    it[calories_per_100g] = BigDecimal("65.00")
                    it[protein_per_100g] = BigDecimal("12.00")
                    it[carbs_per_100g] = BigDecimal("1.00")
                    it[fat_per_100g] = BigDecimal("2.00")
                } get Foods.food_id

            recipeId =
                Recipes.insert {
                    it[recipe_name] = "Test"
                    it[instructions] = "test_instruction"
                    it[category] = "Launch"
                    it[area] = "UK"
                    it[created_by_user_id] = userId
                    it[is_system_recipe] = false
                } get Recipes.recipes_id

            RecipeIngredients.insert {
                it[recipe_id] = recipeId
                it[food_id] = foodId
                it[quantity_g] = BigDecimal("200.00")
                it[original_measure] = "200g apple"
            }
        }
    }

    @Test
    fun should_search_recipe_by_query() =
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
            val response = client.get("/recipes?query=Test")
            val body = response.bodyAsText()
            assertEquals(200, response.status.value)
            assertTrue(body.contains("Test"))
        }

    @Test
    fun should_search_recipe_by_ingredient() =
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
            val response = client.get("/recipes?ingredient=Apple")
            val body = response.bodyAsText()
            assertEquals(200, response.status.value)
            assertTrue(body.contains("Test"))
        }

    @Test
    fun should_load_recipe_detail_page() =
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
            val response = client.get("/recipes/$recipeId")
            val body = response.bodyAsText()
            assertEquals(200, response.status.value)
            assertTrue(body.contains("Apple"))
            assertTrue(body.contains("test_instruction"))
            assertTrue(body.contains("200g apple"))
        }

    @Test
    fun should_return_bad_requst_when_recipe_id_is_invaild() =
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
            val response = client.get("/recipes/invaild")
            assertEquals(400, response.status.value)
        }

    @Test
    fun should_return_not_found_when_recipe_id_does_not_exist() =
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
            val response = client.get("/recipes/9999")
            assertEquals(404, response.status.value)
        }

    @Test
    fun should_add_review_when_logged_in() =
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
            val response =
                client.post("/recipes/$recipeId/review") {
                    contentType(ContentType.Application.FormUrlEncoded)
                    setBody(
                        listOf("rating" to "5", "comment" to "test comment").formUrlEncode(),
                    )
                }
            val reviews = RecipeDatabaseQuery.getReviewsForRecipe(recipeId)
            assertEquals(302, response.status.value)
            assertEquals("/recipes/$recipeId", response.headers[HttpHeaders.Location])
            assertEquals(1, reviews.size)
            assertEquals("test comment", reviews.first().comment)
        }

    @Test
    fun should_remove_favourite_recipe_when_logged_in() =
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
            val response = client.post("/recipes/favourite/$recipeId")
            val favourite = RecipeDatabaseQuery.getFavourites(userId)
            assertEquals(200, response.status.value)
            assertEquals(listOf(recipeId), favourite)
        }

    @Test
    fun should_add_favourite_recipe_when_logged_in() =
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
            val response = client.post("/recipes/unfavourite/$recipeId")
            val favourite = RecipeDatabaseQuery.getFavourites(userId)
            assertEquals(200, response.status.value)
            assertTrue(favourite.isEmpty())
        }
}
