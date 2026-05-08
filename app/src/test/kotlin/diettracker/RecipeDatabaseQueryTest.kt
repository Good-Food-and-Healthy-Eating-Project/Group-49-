/**
 * Database query tests using the TestDatabaseFactory.
 * Each test resets and seeds the in-memory H2 test database, then calls recipe query
 * functions directly and verifies the results from isolated test data.
 */
package diettracker

import diettracker.db.repositories.RecipeDatabaseQuery
import diettracker.db.repositories.RecipeReviewDatabaseQuery
import diettracker.db.tables.Foods
import diettracker.db.tables.RecipeIngredients
import diettracker.db.tables.RecipeReviews
import diettracker.db.tables.Recipes
import diettracker.db.tables.UserFavouritedRecipes
import diettracker.db.tables.Users
import org.jetbrains.exposed.v1.jdbc.deleteAll
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.BeforeEach
import org.mindrot.jbcrypt.BCrypt
import java.math.BigDecimal
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class RecipeDatabaseQueryTest {
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
                    it[email] = "foodlog@test.com"
                    it[password_hash] = BCrypt.hashpw("foodlog@test.com", BCrypt.gensalt())
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

    // AC-PARENT-05
    // AC-PARENT-06
    @Test
    fun should_search_recipe_by_name() {
        val result = RecipeDatabaseQuery.searchRecipes("Test")
        assertEquals(1, result.size)
        assertEquals("Test", result.first().name)
    }

    // AC-PARENT-05
    // AC-VEG-01
    @Test
    fun should_search_recipe_by_ingredient() {
        val result = RecipeDatabaseQuery.searchByIngredient("Apple")
        assertEquals(1, result.size)
        assertEquals("Test", result.first().name)
    }

    // AC-VEG-01
    @Test
    fun should_fiter_recipe_by_category() {
        val result = RecipeDatabaseQuery.searchRecipes("", category = "Launch")
        assertTrue(result.any { it.name == "Test" })
    }

    // AC-API-02
    // AC-ATH-04
    @Test
    fun should_get_recipe_detil_by_ingredient() {
        val result = RecipeDatabaseQuery.getRecipeById(recipeId)

        assertNotNull(result)
        assertEquals("Test", result.name)
        assertEquals("Apple", result.ingredients.first().foodName)
        assertEquals(1, result.ingredients.size)
    }

    // AC-ATH-03
    // AC-VEG-09
    @Test
    fun should_add_and_get_favourite_recipe() {
        RecipeDatabaseQuery.addFavourite(userId, recipeId)
        val favourites = RecipeDatabaseQuery.getFavourites(userId)
        assertEquals(listOf(recipeId), favourites)
    }

    @Test
    fun should_remove_favourite_recipe() {
        RecipeDatabaseQuery.addFavourite(userId, recipeId)
        RecipeDatabaseQuery.removeFavourite(userId, recipeId)
        val favourites = RecipeDatabaseQuery.getFavourites(userId)
        assertTrue(favourites.isEmpty())
    }

    @Test
    fun should_add_comment_and_rating() {
        RecipeReviewDatabaseQuery.addReview(
            userId = userId,
            recipeId = recipeId,
            rating = 4,
            comment = "comment for test",
        )
        val reviews = RecipeReviewDatabaseQuery.getReviewsForRecipe(recipeId)
        assertEquals(1, reviews.size)
        assertEquals("comment for test", reviews.first().comment)
        assertEquals(4, reviews.first().rating)
    }
}
