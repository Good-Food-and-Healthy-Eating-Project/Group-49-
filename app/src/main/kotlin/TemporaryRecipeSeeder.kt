import diettracker.db.tables.Foods
import diettracker.db.tables.RecipeIngredients
import diettracker.db.tables.Recipes
import diettracker.db.tables.Users
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.math.BigDecimal
import java.time.Instant

object TemporaryRecipeSeeder {
    private const val SYSTEM_EMAIL = "seed-system@local"

    private data class SeedRecipe(
        val name: String,
        val category: String,
        val instructions: String,
        val ingredients: List<Pair<String, BigDecimal>>
    )

    private val seedRecipes = listOf(
        SeedRecipe(
            name = "Greek Yogurt Berry Bowl",
            category = "Breakfast",
            instructions = "Add yogurt to a bowl, top with berries and oats, then serve.",
            ingredients = listOf(
                "Greek yogurt" to BigDecimal("200.00"),
                "Blueberries" to BigDecimal("80.00"),
                "Rolled oats" to BigDecimal("30.00")
            )
        ),
        SeedRecipe(
            name = "Grilled Chicken Salad",
            category = "Lunch",
            instructions = "Grill chicken, slice it, and toss with mixed salad and dressing.",
            ingredients = listOf(
                "Chicken breast" to BigDecimal("160.00"),
                "Mixed leaves" to BigDecimal("90.00"),
                "Cherry tomatoes" to BigDecimal("70.00")
            )
        ),
        SeedRecipe(
            name = "Salmon Rice Plate",
            category = "Dinner",
            instructions = "Pan-sear salmon and serve with cooked rice and steamed greens.",
            ingredients = listOf(
                "Salmon" to BigDecimal("170.00"),
                "White rice" to BigDecimal("180.00"),
                "Broccoli" to BigDecimal("100.00")
            )
        ),
        SeedRecipe(
            name = "Overnight Chia Oats",
            category = "Breakfast",
            instructions = "Mix oats, chia, and milk. Chill overnight and top with fruit.",
            ingredients = listOf(
                "Rolled oats" to BigDecimal("40.00"),
                "Chia seeds" to BigDecimal("15.00"),
                "Milk" to BigDecimal("200.00")
            )
        ),
        SeedRecipe(
            name = "Turkey Wrap",
            category = "Lunch",
            instructions = "Fill tortilla with turkey and vegetables, then roll and slice.",
            ingredients = listOf(
                "Wholewheat tortilla" to BigDecimal("70.00"),
                "Turkey slices" to BigDecimal("100.00"),
                "Cucumber" to BigDecimal("50.00")
            )
        ),
        SeedRecipe(
            name = "Vegetable Stir-Fry",
            category = "Dinner",
            instructions = "Stir-fry vegetables over high heat and serve with noodles.",
            ingredients = listOf(
                "Bell pepper" to BigDecimal("80.00"),
                "Carrot" to BigDecimal("60.00"),
                "Egg noodles" to BigDecimal("150.00")
            )
        )
    )

    fun seedIfNeeded() {
        transaction {
            val hasAnyRecipes = Recipes.selectAll().limit(1).count() > 0
            if (hasAnyRecipes) return@transaction

            val systemUserId = ensureSystemUser()

            for (recipe in seedRecipes) {
                val recipeId = Recipes.insert {
                    it[recipe_name] = recipe.name
                    it[instructions] = recipe.instructions
                    it[category] = recipe.category
                    it[area] = "Global"
                    it[thumbnail_url] = null
                    it[external_mealdb_id] = null
                    it[created_by_user_id] = systemUserId
                    it[is_system_recipe] = true
                }[Recipes.recipes_id]

                recipe.ingredients.forEach { (foodName, quantity) ->
                    val foodId = findOrCreateFood(foodName)
                    RecipeIngredients.insert {
                        it[recipe_id] = recipeId
                        it[food_id] = foodId
                        it[quantity_g] = quantity
                        it[original_measure] = null
                    }
                }
            }
        }
    }

    private fun ensureSystemUser(): Int {
        val existing = Users
            .selectAll()
            .where { Users.email eq SYSTEM_EMAIL }
            .firstOrNull()

        if (existing != null) return existing[Users.user_id]

        return Users.insert {
            it[first_name] = "Seed"
            it[second_name] = "System"
            it[email] = SYSTEM_EMAIL
            it[password_hash] = "seed-only-account"
            it[created_at] = Instant.now()
        }[Users.user_id]
    }

    private fun findOrCreateFood(foodName: String): Int {
        val existing = Foods
            .selectAll()
            .where { Foods.food_name eq foodName }
            .firstOrNull()

        if (existing != null) return existing[Foods.food_id]

        return Foods.insert {
            it[Foods.food_name] = foodName
            it[Foods.usda_fdc_id] = null
            it[Foods.calories_per_100g] = BigDecimal.ZERO
            it[Foods.protein_per_100g] = BigDecimal.ZERO
            it[Foods.carbs_per_100g] = BigDecimal.ZERO
            it[Foods.fat_per_100g] = BigDecimal.ZERO
            it[Foods.fiber_per_100g] = null
            it[Foods.sugar_per_100g] = null
            it[Foods.sodium_mg_per_100g] = null
            it[Foods.potassium_mg_per_100g] = null
            it[Foods.calcium_mg_per_100g] = null
            it[Foods.iron_mg_per_100g] = null
            it[Foods.magnesium_mg_per_100g] = null
            it[Foods.zinc_mg_per_100g] = null
            it[Foods.vitamin_a_mcg_per_100g] = null
            it[Foods.vitamin_c_mg_per_100g] = null
            it[Foods.vitamin_d_mcg_per_100g] = null
            it[Foods.vitamin_b6_mg_per_100g] = null
            it[Foods.vitamin_b12_mcg_per_100g] = null
        }[Foods.food_id]
    }
}
