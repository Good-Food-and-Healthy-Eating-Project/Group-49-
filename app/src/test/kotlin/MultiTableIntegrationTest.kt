import diettracker.diettracker.db.tables.ClientProfessionalLink
import diettracker.diettracker.db.tables.Clients
import diettracker.diettracker.db.tables.FoodLogItems
import diettracker.diettracker.db.tables.FoodLogs
import diettracker.diettracker.db.tables.Foods
import diettracker.diettracker.db.tables.Professionals
import diettracker.diettracker.db.tables.RecipeIngredients
import diettracker.diettracker.db.tables.Recipes
import diettracker.diettracker.db.tables.Roles
import diettracker.diettracker.db.tables.UserRoles
import diettracker.diettracker.db.tables.Users
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteAll
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class MultiTableIntegrationTest {
    @BeforeEach
    fun setUP() {
        TestDatabaseFactory.init()
        transaction {
            ClientProfessionalLink.deleteAll()
            FoodLogItems.deleteAll()
            FoodLogs.deleteAll()
            RecipeIngredients.deleteAll()
            UserRoles.deleteAll()
            Professionals.deleteAll()
            Recipes.deleteAll()
            Roles.deleteAll()
            Clients.deleteAll()
            Users.deleteAll()
            Foods.deleteAll()
        }
    }

    private fun createTestUserData(email: String): Int {
        val time = Instant.now()
        val userId =
            Users.insert {
                it[first_name] = "Sponge"
                it[second_name] = "Bob"
                it[Users.email] = email
                it[password_hash] = "password_hash"
                it[created_at] = time
            } get Users.user_id
        return userId
    }

    private fun createClient(userId: Int) {
        Clients.insert {
            it[client_id] = userId
            it[data_of_birth] = LocalDate.of(1999, 9, 12)
            it[height_cm] = 180
            it[weight_kg] = 80
        }
    }

    private fun createFood(usdaFdcIdValue: Long): Int {
        return Foods.insert {
            it[food_name] = "Test"
            it[usda_fdc_id] = usdaFdcIdValue
            it[calories_per_100g] = BigDecimal("100.00")
            it[protein_per_100g] = BigDecimal("10.00")
            it[carbs_per_100g] = BigDecimal("13.00")
            it[fat_per_100g] = BigDecimal("2.10")
            it[fiber_per_100g] = BigDecimal("3.00")
            it[sugar_per_100g] = BigDecimal("4.00")
            it[sodium_mg_per_100g] = BigDecimal("11.00")
            it[potassium_mg_per_100g] = BigDecimal("130.00")
            it[calcium_mg_per_100g] = BigDecimal("25.40")
            it[iron_mg_per_100g] = BigDecimal("1.20")
            it[magnesium_mg_per_100g] = BigDecimal("30.60")
            it[zinc_mg_per_100g] = BigDecimal("1.00")
            it[vitamin_a_mcg_per_100g] = BigDecimal("10.00")
            it[vitamin_c_mg_per_100g] = BigDecimal("5.00")
            it[vitamin_d_mcg_per_100g] = BigDecimal("0.33")
            it[vitamin_b6_mg_per_100g] = BigDecimal("0.1")
            it[vitamin_b12_mcg_per_100g] = BigDecimal("0.22")
        } get Foods.food_id
    }

    private fun crateFoodLog(userId: Int): Int {
        val time = Instant.now()
        val foodLogId =
            FoodLogs.insert {
                it[users_id] = userId
                it[log_date] = time
                it[meal_type] = "Launch"
                it[notes] = "test_notes"
            } get FoodLogs.food_log_id
        return foodLogId
    }

    private fun crateFoodLogItem(
        foodLogId: Int,
        foodId: Int,
    ) {
        FoodLogItems.insert {
            it[food_log_id] = foodLogId
            it[food_id] = foodId
            it[quantity_g] = BigDecimal("200.00")
        }
    }

    private fun createRole(roleName: String): Int {
        val roleId = Roles.insert { it[Roles.role_name] = roleName } get Roles.role_id
        return roleId
    }

    private fun crateRecipe(userId: Int): Int {
        val recipeId =
            Recipes.insert {
                it[recipe_name] = "Test"
                it[instructions] = "test_instruction"
                it[category] = "Launch"
                it[area] = "UK"
                it[created_by_user_id] = userId
                it[is_system_recipe] = false
            } get Recipes.recipes_id
        return recipeId
    }

    private fun createRecipeIngredient(
        recipeId: Int,
        foodId: Int,
    ) {
        RecipeIngredients.insert {
            it[recipe_id] = recipeId
            it[RecipeIngredients.food_id] = foodId
            it[quantity_g] = BigDecimal("120.00")
            it[original_measure] = "a bowl"
        }
    }

    private fun createProfessional(userId: Int) {
        Professionals.insert {
            it[professional_id] = userId
            it[job_title] = "tester"
            it[organistation] = "test"
            it[bio] = "testing"
        }
    }

    @Test
    fun should_assign_role_to_user_success() {
        transaction {
            val userId = createTestUserData("test@test.com")
            val roleId = createRole("tester")
            UserRoles.insert {
                it[UserRoles.user_id] = userId
                it[UserRoles.role_id] = roleId
            }
            val userRole =
                UserRoles.selectAll()
                    .where {
                        (UserRoles.user_id eq userId) and (UserRoles.role_id eq roleId)
                    }
                    .singleOrNull()
            assertNotNull(userRole)
            assertEquals(userId, userRole[UserRoles.user_id])
            assertEquals(roleId, userRole[UserRoles.role_id])
        }
    }

    @Test
    fun should_create_food_log_with_item() {
        transaction {
            val userId = createTestUserData("test_1@test.com")
            createClient(userId)
            val foodLogId = crateFoodLog(userId)
            val foodId = createFood(9999L)
            crateFoodLogItem(foodLogId, foodId)
            val foodLog =
                FoodLogs.selectAll().where { FoodLogs.food_log_id eq foodLogId }.singleOrNull()
            val item =
                FoodLogItems.selectAll()
                    .where { FoodLogItems.food_log_id eq foodLogId }
                    .toList()
            assertNotNull(foodLog)
            assertEquals(1, item.size)
            assertEquals(userId, foodLog[FoodLogs.users_id])
            assertEquals(foodId, item[0][FoodLogItems.food_id])
            assertEquals(BigDecimal("200.00"), item[0][FoodLogItems.quantity_g])
        }
    }

    @Test
    fun should_crate_recipe_with_ingredients() {
        transaction {
            val userId = createTestUserData("test2@test.com")
            val foodId = createFood(10000L)
            val recipeId = crateRecipe(userId)
            createRecipeIngredient(recipeId, foodId)
            val recipe = Recipes.selectAll().where { Recipes.recipes_id eq recipeId }.singleOrNull()
            val ingredient =
                RecipeIngredients.selectAll()
                    .where {
                        (RecipeIngredients.food_id eq foodId) and (RecipeIngredients.recipe_id eq recipeId)
                    }
                    .singleOrNull()
            assertNotNull(recipe)
            assertNotNull(ingredient)
            assertEquals("Test", recipe[Recipes.recipe_name])
            assertEquals(recipeId, ingredient[RecipeIngredients.recipe_id])
            assertEquals(foodId, ingredient[RecipeIngredients.food_id])
            assertEquals(BigDecimal("120.00"), ingredient[RecipeIngredients.quantity_g])
            assertEquals("a bowl", ingredient[RecipeIngredients.original_measure])
        }
    }

    @Test
    fun should_link_client_with_profesional() {
        transaction {
            val clientId = createTestUserData("test3@test.com")
            val professionalId = createTestUserData("test4@test.com")
            createClient(clientId)
            createProfessional(professionalId)
            ClientProfessionalLink.insert {
                it[ClientProfessionalLink.client_id] = clientId
                it[ClientProfessionalLink.professional_id] = professionalId
            }
            val link =
                ClientProfessionalLink.selectAll()
                    .where {
                        (ClientProfessionalLink.client_id eq clientId) and (ClientProfessionalLink.professional_id eq professionalId)
                    }
                    .singleOrNull()
            assertNotNull(link)
            assertEquals(clientId, link[ClientProfessionalLink.client_id])
            assertEquals(professionalId, link[ClientProfessionalLink.professional_id])
        }
    }
}
