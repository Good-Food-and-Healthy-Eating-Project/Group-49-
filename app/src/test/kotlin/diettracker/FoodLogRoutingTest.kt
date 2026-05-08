/**
 * Routing tests using Ktor's testApplication.
 * Each test resets and seeds the in-memory H2 test database, starts module(testing = true),
 * then uses a cookie-enabled test HTTP client to log in and exercise food log routes.
 * Acceptance criteria: API-1, API-4, P1-2, P4-1, P5-2, P7-2, P7-3.
 */
package diettracker

import diettracker.db.tables.FoodLogItems
import diettracker.db.tables.FoodLogs
import diettracker.db.tables.Foods
import diettracker.db.tables.RecipeIngredients
import diettracker.db.tables.Recipes
import diettracker.db.tables.Roles
import diettracker.db.tables.SavedMeals
import diettracker.db.tables.UserRoles
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
import org.jetbrains.exposed.v1.core.eq
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
            val userId = insertTestUser(time)
            insertTestFoods(userId)
        }
    }

    private fun insertTestUser(time: Instant): Int {
        val userId =
            Users.insert {
                it[first_name] = "Sponge"
                it[second_name] = "Bob"
                it[email] = "foodlog@test.com"
                it[password_hash] = BCrypt.hashpw("foodlog@test.com", BCrypt.gensalt())
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
        return userId
    }

    private fun insertTestFoods(userId: Int) {
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

    // AC-API-01
    // AC-PARENT-01
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

    // AC-ATH-02
    // AC-API-04
    // AC-STUDENT-01
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
            assertTrue(body.contains("220 kcal")) // grams / 100 * 110
        }

    // AC-ATH-04
    // AC-VEG-10
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
                val testUserId =
                    Users.insert {
                        it[email] = "test@test.com"
                        it[password_hash] = BCrypt.hashpw("test", BCrypt.gensalt())
                        it[first_name] = "Test"
                        it[second_name] = "User"
                        it[created_at] = java.time.Instant.now()
                    } get Users.user_id

                val clientRoleId =
                    Roles.selectAll()
                        .where { Roles.role_name eq "client" }
                        .map { it[Roles.role_id] }
                        .singleOrNull()
                if (clientRoleId != null) {
                    UserRoles.insert {
                        it[UserRoles.user_id] = testUserId
                        it[UserRoles.role_id] = clientRoleId
                    }
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
            assertTrue(body.contains("220 kcal")) // 110 + (55 / 100 * 200)
        }

    @Test
    fun should_reset_food_log_calories() =
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
            client.post("/food_log_custom") {
                contentType(ContentType.Application.FormUrlEncoded)
                setBody(
                    listOf("foodId" to foodId.toString(), "grams" to "200")
                        .formUrlEncode(),
                )
            }

            val result = client.post("/food_log_reset")
            val page = client.get("/food_log")
            val body = page.bodyAsText()

            assertEquals(302, result.status.value)
            assertEquals(200, page.status.value)
            assertTrue(body.contains("0 kcal"))
        }

    // AC-STUDENT-02
    // AC-ELDER-02
    @Test
    fun should_fail_when_food_id_missing() =
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
            assertTrue(body.contains("0 kcal"))
        }

    // AC-STUDENT-02
    // AC-ELDER-02
    @Test
    fun should_fail_when_recipe_id_missing() =
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
            assertTrue(body.contains("0 kcal"))
        }

    // AC-STUDENT-02
    // AC-ELDER-02
    @Test
    fun should_display_error_message_when_food_id_is_missing() =
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
            // Submit without a foodId to trigger the invalid entry path
            val result =
                client.post("/food_log_custom") {
                    contentType(ContentType.Application.FormUrlEncoded)
                    setBody(listOf("grams" to "100").formUrlEncode())
                }
            val body = result.bodyAsText()
            assertEquals(200, result.status.value)
            // The page must display an error message so the user knows their entry was not accepted
            assertTrue(body.contains("Invalid or missing foodId"))
        }

    /**
     * This test should fail now as there is an error in the code
     * Normally, clicking the add or save meal button should not
     * add the food to food diary in case of errors so users can reset to delete
     *
     */
    @Test
    fun should_not_save_food_when_only_added_to_session() =
        testApplication {
            application { module(testing = true) }

            val client =
                createClient {
                    install(HttpCookies)
                }

            // login
            client.post("/Login") {
                contentType(ContentType.Application.FormUrlEncoded)
                setBody(
                    listOf("email" to "foodlog@test.com", "password" to "foodlog@test.com").formUrlEncode(),
                )
            }

            // add food (session only)
            client.post("/food_log_custom") {
                contentType(ContentType.Application.FormUrlEncoded)
                setBody(
                    listOf("food_id" to "1", "grams" to "100").formUrlEncode(),
                )
            }

            // check UI updated
            val response = client.get("/food_log")
            val body = response.bodyAsText()

            assertTrue(body.contains("kcal"))

            // ensure DB is still empty
            val logCount =
                transaction {
                    FoodLogs.selectAll().count()
                }
            val itemCount =
                transaction {
                    FoodLogs.selectAll().count()
                }

            assertEquals(0, logCount)
            assertEquals(0, itemCount)
        }

    // AC-DB-04
    // AC-STUDENT-01
    @Test
    fun should_save_food_log_when_add_to_diary_button_pressed() =
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
                    listOf("email" to "foodlog@test.com", "password" to "foodlog@test.com").formUrlEncode(),
                )
            }

            val foodId =
                transaction {
                    Foods.selectAll().first { it[Foods.food_name] == "apple" }[Foods.food_id]
                }

            client.post("/food_log_custom") {
                contentType(ContentType.Application.FormUrlEncoded)
                setBody(
                    listOf("foodId" to foodId.toString(), "grams" to "100").formUrlEncode(),
                )
            }

            client.post("/save_food_log")

            val count =
                transaction {
                    FoodLogs.selectAll().count()
                }

            assertEquals(1, count)
        }

    // AC-ATH-03
    // AC-ATH-04
    // AC-VEG-09
    // AC-VEG-10
    // AC-PARENT-03
    @Test
    fun should_create_and_add_saved_meal_to_food_log_and_add_to_diary() =
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
                    listOf("email" to "foodlog@test.com", "password" to "foodlog@test.com").formUrlEncode(),
                )
            }

            val foodId =
                transaction {
                    Foods.selectAll().first { it[Foods.food_name] == "apple" }[Foods.food_id]
                }

            // First add an ingredient for meal
            client.post("/food_log_custom") {
                contentType(ContentType.Application.FormUrlEncoded)
                setBody(
                    listOf("foodId" to foodId.toString(), "grams" to "100").formUrlEncode(),
                )
            }

            // Save as a meal
            client.post("/save_meal") {
                contentType(ContentType.Application.FormUrlEncoded)
                setBody(
                    listOf("mealName" to "Test Meal").formUrlEncode(),
                )
            }

            // session reset
            client.post("/food_log_reset")

            val mealId =
                transaction {
                    SavedMeals.selectAll().first { it[SavedMeals.meal_name] == "Test Meal" }[SavedMeals.meal_id]
                }

            // add saved meal
            client.post("/add_saved_meal_to_log") {
                contentType(ContentType.Application.FormUrlEncoded)
                setBody(
                    listOf("mealId" to mealId.toString()).formUrlEncode(),
                )
            }

            // save the meal to diary
            client.post("/save_food_log")

            // checkign the entry has been created
            val count =
                transaction {
                    FoodLogs.selectAll().count()
                }

            assertEquals(1, count)
        }

    @Test
    fun should_not_crash_when_grams_is_too_large() =
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
                    listOf(
                        "email" to "foodlog@test.com",
                        "password" to "foodlog@test.com",
                    ).formUrlEncode(),
                )
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
                        listOf(
                            "foodId" to foodId.toString(),
                            // It crashes now if the value is more than 6 digits, including the decimal places.
                            "grams" to "10000",
                        ).formUrlEncode(),
                    )
                }
            assertTrue(result.status.value != 500)
        }
}
