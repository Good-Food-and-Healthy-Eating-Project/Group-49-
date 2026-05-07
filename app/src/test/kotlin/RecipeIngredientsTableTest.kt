/**
 * Database table tests using the TestDatabaseFactory.
 * Each test resets the in-memory H2 test database, then uses Exposed transactions
 * to insert, query, update, and delete rows directly against the schema.
 */
import diettracker.db.tables.Foods
import diettracker.db.tables.RecipeIngredients
import diettracker.db.tables.Recipes
import diettracker.db.tables.Users
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteAll
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class RecipeIngredientsTableTest {
    @BeforeEach
    fun setUP() {
        TestDatabaseFactory.init()
        transaction {
            RecipeIngredients.deleteAll()
            Foods.deleteAll()
            Recipes.deleteAll()
            Users.deleteAll()
        }
    }

    // AC-DB-04
    // AC-VEG-08
    @Test
    fun should_insert_recipe_ingredient_success() {
        transaction {
            val time = Instant.now()
            val userId =
                Users.insert {
                    it[first_name] = "Sponge"
                    it[second_name] = "Bob"
                    it[email] = "test@test.com"
                    it[password_hash] = "password_hash"
                    it[created_at] = time
                } get Users.user_id

            val recipeId =
                Recipes.insert {
                    it[recipe_name] = "test_recipe"
                    it[instructions] = "test instructions"
                    it[created_by_user_id] = userId
                    it[is_system_recipe] = false
                } get Recipes.recipes_id

            val foodId =
                Foods.insert {
                    it[food_name] = "banana"
                    it[calories_per_100g] = BigDecimal("50.10")
                    it[protein_per_100g] = BigDecimal("7.00")
                    it[carbs_per_100g] = BigDecimal("15.20")
                    it[fat_per_100g] = BigDecimal("2.00")
                } get Foods.food_id

            RecipeIngredients.insert {
                it[RecipeIngredients.recipe_id] = recipeId
                it[RecipeIngredients.food_id] = foodId
                it[quantity_g] = BigDecimal("120.00")
            }

            val result =
                RecipeIngredients
                    .selectAll()
                    .where {
                        (RecipeIngredients.recipe_id eq recipeId) and (RecipeIngredients.food_id eq foodId)
                    }.singleOrNull()
            assertNotNull(result)
            assertEquals(BigDecimal("120.00"), result[RecipeIngredients.quantity_g])
        }
    }

    @Test
    fun should_delet_recipe_ingredient_success() {
        transaction {
            val time = Instant.now()
            val userId =
                Users.insert {
                    it[user_id] = 1
                    it[first_name] = "Sponge"
                    it[second_name] = "Bob"
                    it[email] = "test@test.com"
                    it[password_hash] = "password_hash"
                    it[created_at] = time
                } get Users.user_id

            val recipeId =
                Recipes.insert {
                    it[recipe_name] = "test_recipe_name"
                    it[instructions] = "test_instructions"
                    it[created_by_user_id] = userId
                    it[is_system_recipe] = false
                } get Recipes.recipes_id

            val foodId =
                Foods.insert {
                    it[food_name] = "banana"
                    it[calories_per_100g] = BigDecimal("50.10")
                    it[protein_per_100g] = BigDecimal("7.00")
                    it[carbs_per_100g] = BigDecimal("15.20")
                    it[fat_per_100g] = BigDecimal("2.00")
                } get Foods.food_id

            RecipeIngredients.insert {
                it[RecipeIngredients.recipe_id] = recipeId
                it[RecipeIngredients.food_id] = foodId
                it[quantity_g] = BigDecimal("120.00")
            }
            RecipeIngredients.deleteWhere {
                (RecipeIngredients.recipe_id eq recipeId) and (RecipeIngredients.food_id eq foodId)
            }
            val result =
                RecipeIngredients
                    .selectAll()
                    .where {
                        (RecipeIngredients.recipe_id eq recipeId) and
                            (RecipeIngredients.food_id eq foodId)
                    }.singleOrNull()
            assertNull(result)
        }
    }

    @Test
    fun should_update_recipe_ingredient_success() {
        transaction {
            val time = Instant.now()
            val userId =
                Users.insert {
                    it[user_id] = 1
                    it[first_name] = "Sponge"
                    it[second_name] = "Bob"
                    it[email] = "test@test.com"
                    it[password_hash] = "password_hash"
                    it[created_at] = time
                } get Users.user_id

            val recipeId =
                Recipes.insert {
                    it[recipe_name] = "test_recipe_name"
                    it[instructions] = "test_instructions"
                    it[created_by_user_id] = userId
                    it[is_system_recipe] = false
                } get Recipes.recipes_id

            val foodId =
                Foods.insert {
                    it[food_name] = "banana"
                    it[calories_per_100g] = BigDecimal("50.10")
                    it[protein_per_100g] = BigDecimal("7.00")
                    it[carbs_per_100g] = BigDecimal("15.20")
                    it[fat_per_100g] = BigDecimal("2.00")
                } get Foods.food_id

            RecipeIngredients.insert {
                it[RecipeIngredients.recipe_id] = recipeId
                it[RecipeIngredients.food_id] = foodId
                it[quantity_g] = BigDecimal("120.00")
            }

            RecipeIngredients.update({
                (RecipeIngredients.recipe_id eq recipeId) and (RecipeIngredients.food_id eq foodId)
            }) { it[quantity_g] = BigDecimal("300.00") }
            val result =
                RecipeIngredients
                    .selectAll()
                    .where {
                        (RecipeIngredients.recipe_id eq recipeId) and
                            (RecipeIngredients.food_id eq foodId)
                    }.singleOrNull()
            assertNotNull(result)
            assertEquals(BigDecimal("300.00"), result[RecipeIngredients.quantity_g])
        }
    }

    // AC-DB-04
    @Test
    fun should_get_recipe_ingredients_by_recipe_id() {
        transaction {
            val time = Instant.now()
            val userId =
                Users.insert {
                    it[user_id] = 1
                    it[first_name] = "Sponge"
                    it[second_name] = "Bob"
                    it[email] = "test@test.com"
                    it[password_hash] = "password_hash"
                    it[created_at] = time
                } get Users.user_id

            val recipeId =
                Recipes.insert {
                    it[recipe_name] = "test_recipe_name"
                    it[instructions] = "test_instructions"
                    it[created_by_user_id] = userId
                    it[is_system_recipe] = false
                } get Recipes.recipes_id

            val foodId =
                Foods.insert {
                    it[food_name] = "banana"
                    it[calories_per_100g] = BigDecimal("50.10")
                    it[protein_per_100g] = BigDecimal("7.00")
                    it[carbs_per_100g] = BigDecimal("15.20")
                    it[fat_per_100g] = BigDecimal("2.00")
                } get Foods.food_id

            RecipeIngredients.insert {
                it[RecipeIngredients.recipe_id] = recipeId
                it[RecipeIngredients.food_id] = foodId
                it[quantity_g] = BigDecimal("120.00")
            }
            val result =
                RecipeIngredients
                    .selectAll()
                    .where {
                        (RecipeIngredients.recipe_id eq recipeId) and
                            (RecipeIngredients.food_id eq foodId)
                    }.toList()
            assertEquals(1, result.size)
            assertEquals(foodId, result[0][RecipeIngredients.food_id])
            assertEquals(BigDecimal("120.00"), result[0][RecipeIngredients.quantity_g])
        }
    }

    // AC-DB-05
    @Test
    fun should_fail_when_insert_same_recipe_ingredient() {
        transaction {
            val time = Instant.now()
            val userId =
                Users.insert {
                    it[user_id] = 1
                    it[first_name] = "Sponge"
                    it[second_name] = "Bob"
                    it[email] = "test@test.com"
                    it[password_hash] = "password_hash"
                    it[created_at] = time
                } get Users.user_id

            val recipeId =
                Recipes.insert {
                    it[recipe_name] = "test_recipe_name"
                    it[instructions] = "test_instructions"
                    it[created_by_user_id] = userId
                    it[is_system_recipe] = false
                } get Recipes.recipes_id

            val foodId =
                Foods.insert {
                    it[food_name] = "banana"
                    it[calories_per_100g] = BigDecimal("50.10")
                    it[protein_per_100g] = BigDecimal("7.00")
                    it[carbs_per_100g] = BigDecimal("15.20")
                    it[fat_per_100g] = BigDecimal("2.00")
                } get Foods.food_id

            RecipeIngredients.insert {
                it[RecipeIngredients.recipe_id] = recipeId
                it[RecipeIngredients.food_id] = foodId
                it[quantity_g] = BigDecimal("120.00")
            }
            assertFailsWith<Exception> {
                RecipeIngredients.insert {
                    it[RecipeIngredients.recipe_id] = recipeId
                    it[RecipeIngredients.food_id] = foodId
                    it[quantity_g] = BigDecimal("300.00")
                }
            }
        }
    }

    // AC-DB-05
    @Test
    fun should_fail_when_recipe_not_exist() {
        transaction {
            assertFailsWith<Exception> {
                RecipeIngredients.insert {
                    it[recipe_id] = 9999
                    it[food_id] = 9999
                    it[quantity_g] = BigDecimal("300.00")
                }
            }
        }
    }
}
