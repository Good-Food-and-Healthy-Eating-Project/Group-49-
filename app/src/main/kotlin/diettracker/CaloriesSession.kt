package diettracker

import diettracker.db.tables.FoodLogItems
import diettracker.db.tables.FoodLogs
import diettracker.db.tables.Foods
import diettracker.db.tables.RecipeIngredients
import diettracker.db.tables.Recipes
import diettracker.models.CurrentMealFood
import diettracker.models.CurrentMealSession
import diettracker.models.Food
import diettracker.models.Recipe
import diettracker.services.DiaryService.getMealTypeByTime
import diettracker.services.DiaryService.saveFoodLog
import io.ktor.server.application.ApplicationCall
import io.ktor.server.pebble.respondTemplate
import io.ktor.server.request.receiveParameters
import io.ktor.server.response.respondRedirect
import io.ktor.server.sessions.get
import io.ktor.server.sessions.sessions
import io.ktor.server.sessions.set
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.like
import org.jetbrains.exposed.v1.core.lowerCase
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.time.Instant

const val GRAMS_PER_SERVING = 100

/**
 * Stores the user's current nutrition totals in the session.
 *
 * @param calories The current total calories.
 * @param protein The current total protein.
 * @param fat The current total fat.
 * @param carbs The current total carbohydrates.
 */
@Serializable
data class CaloriesSession(val calories: Int = 0, val protein: Int = 0, val fat: Int = 0, val carbs: Int = 0)

/**
 * Displays the food log page with the current nutrition totals.
 *
 * It gets the saved calorie session values and passes them to the page.
 */
suspend fun ApplicationCall.foodLogPage() {
    val caloriesSession = sessions.get<CaloriesSession>() ?: CaloriesSession()
    respondTemplate(
        "pages/client_dash/add_food.peb",
        mapOf(
            "calories" to caloriesSession.calories,
            "protein" to caloriesSession.protein,
            "fat" to caloriesSession.fat,
            "carbs" to caloriesSession.carbs,
        ),
    )
}

/**
 * Stores the foods and nutrients added from a recipe.
 *
 * This is used by logRecipeIngredients() so it can return both the recipe
 * foods added to the current meal and the total nutrition values.
 *
 * @param foods The list of recipe foods added to the current meal.
 * @param nutrients The total nutrition values calculated from the recipe.
 */
private data class RecipeLogResult(val foods: List<CurrentMealFood>, val nutrients: NutrientValues)

private fun logRecipeIngredients(recipeid: Int): RecipeLogResult {
    var addCalories = 0
    var addProtein = 0
    var addFat = 0
    var addCarbs = 0
    val foodsToAdd = mutableListOf<CurrentMealFood>()
    transaction {
        val ingredients =
            RecipeIngredients
                .selectAll()
                .where { RecipeIngredients.recipe_id eq recipeid }
                .map { row -> row }

        /**val logId =
         if (userId != null) {
         FoodLogs.insert {
         it[FoodLogs.user_id] = userId
         it[FoodLogs.log_date] = Instant.now()
         it[FoodLogs.meal_type] = "recipe"
         it[FoodLogs.notes] = ""
         } get FoodLogs.food_log_id
         } else {
         null
         }
         */

        for (i in ingredients) {
            val foodId = i[RecipeIngredients.food_id]
            val quantity = i[RecipeIngredients.quantity_g]
            val grams = i[RecipeIngredients.quantity_g].toInt()
            foodsToAdd += CurrentMealFood(foodId = foodId, grams = grams)
            val n = calcNutrients(foodId, quantity.toInt())
            addCalories += n.calories
            addProtein += n.protein
            addFat += n.fat
            addCarbs += n.carbs
            /**logId?.let {
             FoodLogItems.insert { row ->
             row[FoodLogItems.food_log_id] = it
             row[FoodLogItems.food_id] = foodId
             row[FoodLogItems.quantity_g] = quantity
             }
             }
             */
        }
    }
    return RecipeLogResult(foodsToAdd, NutrientValues(addCalories, addProtein, addFat, addCarbs))
}

/**
 * Handles adding a selected recipe to the current food log session.
 *
 * This is used in configureFoodPostRoutes() by the food_log_recipe post
 * route .This is used when a user selects a recipe. It gets the
 * recipe ID from the form, adds the recipe foods to the current meal
 * session, updates the current nutrition totals, and redirects back to the
 * food log page.
 */
suspend fun ApplicationCall.foodLogRecipe() {
    val params = receiveParameters()
    val recipeIdStr = params["recipeId"]
    val recipeid = recipeIdStr?.toIntOrNull()
    if (recipeid == null) {
        respondTemplate(
            "pages/client_dash/add_food.peb",
            mapOf("calories" to 0, "error" to "Invalid or missing recipeId: $recipeIdStr"),
        )
        return
    }

    val result = logRecipeIngredients(recipeid)

    val caloriesSession = sessions.get<CaloriesSession>() ?: CaloriesSession(0, 0, 0, 0)
    sessions.set(
        CaloriesSession(
            caloriesSession.calories + result.nutrients.calories,
            caloriesSession.protein + result.nutrients.protein,
            caloriesSession.fat + result.nutrients.fat,
            caloriesSession.carbs + result.nutrients.carbs,
        ),
    )
    val currentMeal = sessions.get<CurrentMealSession>() ?: CurrentMealSession(emptyList())
    sessions.set(CurrentMealSession(currentMeal.foods + result.foods))
    respondRedirect("/food_log")
}

/**
 * Calculates the nutrition values for a custom food item.
 *
 * This is used in foodLogCustom() when the user adds a custom food.
 * It calculates the calories, protein, fat, and carbs for the
 * selected food and gram amount by calling calcNutrients(). If a user ID is
 * provided, it also saves the custom food item to the food log tables.
 *
 * @param userId The ID of the logged-in user, or null if unavailable.
 * @param foodId The ID of the selected food item.
 * @param grams The amount of the food item in grams.
 * @return The calculated nutrition values for the selected food and gram amount.
 */
private fun calcAndLogCustomFood(
    userId: Int?,
    foodId: Int,
    grams: Int,
): NutrientValues =
    transaction {
        val nutrients = calcNutrients(foodId, grams)
        if (userId != null) {
            val logId =
                FoodLogs.insert {
                    it[FoodLogs.user_id] = userId
                    it[FoodLogs.log_date] = Instant.now()
                    it[FoodLogs.meal_type] = "custom"
                    it[FoodLogs.notes] = ""
                } get FoodLogs.food_log_id
            FoodLogItems.insert {
                it[FoodLogItems.food_log_id] = logId
                it[FoodLogItems.food_id] = foodId
                it[FoodLogItems.quantity_g] = grams.toBigDecimal()
            }
        }
        nutrients
    }

/**
 * Handles adding a custom food item to the current food log session.
 *
 * This is used in configureFoodPostRoutes() by food_log_custom Post
 * route when the user adds a custom food. It gets the
 * food ID and gram amount from the form, calculates the nutrition values,
 * updates the current nutrition totals, adds the food to the current meal
 * session, and displays the updated food log page.
 */
suspend fun ApplicationCall.foodLogCustom() {
    val caloriesSession = sessions.get<CaloriesSession>() ?: CaloriesSession(0, 0, 0, 0)
    val params = receiveParameters()
    val foodIdStr = params["foodId"]
    val foodId = foodIdStr?.toIntOrNull()
    val grams = params["grams"]?.toIntOrNull() ?: GRAMS_PER_SERVING
    if (foodId == null) {
        respondTemplate(
            "pages/client_dash/add_food.peb",
            mapOf("calories" to 0, "error" to "Invalid or missing foodId: $foodIdStr"),
        )
        return
    }

    val email = sessions.get<UserSession>()?.email
    val userId = email?.let { getUserIdByEmail(it) }
    val nutrients = calcAndLogCustomFood(userId, foodId, grams)

    val newTotalCals = caloriesSession.calories + nutrients.calories
    val newTotalProtein = caloriesSession.protein + nutrients.protein
    val newTotalFat = caloriesSession.fat + nutrients.fat
    val newTotalCarbs = caloriesSession.carbs + nutrients.carbs

    sessions.set(CaloriesSession(newTotalCals, newTotalProtein, newTotalFat, newTotalCarbs))
    val currentMeal = sessions.get<CurrentMealSession>() ?: CurrentMealSession(emptyList())
    sessions.set(CurrentMealSession(currentMeal.foods + CurrentMealFood(foodId = foodId, grams = grams)))

    respondRedirect("/food_log")
}

/**
 * Searches the recipe database using the user's search text.
 *
 * This is used by configureFoodLogRoute() and configureRecipeSearchRoute()
 * when the user searches for recipes from the food log page. It returns an
 * empty list if the search text is blank or contains numbers.
 *
 * @param query The recipe search text entered by the user.
 * @return A list of matching recipes.
 */
fun searchRecipes(query: String): List<Recipe> =
    transaction {
        val searchTerm = query.lowercase()

        if (searchTerm.isBlank() || searchTerm.any { it.isDigit() }) {
            return@transaction emptyList<Recipe>()
        }

        val recipes =
            Recipes
                .selectAll()
                .where { Recipes.recipe_name.lowerCase() like "%$searchTerm%" }
                .map { row ->
                    Recipe(
                        id = row[Recipes.recipes_id],
                        name = row[Recipes.recipe_name],
                    )
                }

        return@transaction recipes
    }

/**
 * Searches the food database using the user's search text.
 *
 * This is used by configureFoodLogRoute() and configureFoodSearchRoute()
 * when the user searches for food items it then returns
 * an empty list if the search text is blank or contains numbers or
 * the foods thats match the query.
 *
 * @param foodquery The food search text entered by the user.
 * @return A list of matching food items with their nutrition values per 100g.
 */
fun searchFoods(foodquery: String): List<Food> =
    transaction {
        val searchTerm = foodquery.lowercase()

        if (searchTerm.isBlank() || searchTerm.any { it.isDigit() }) {
            return@transaction emptyList<Food>()
        }

        val foods =
            Foods
                .selectAll()
                .where { Foods.food_name.lowerCase() like "%$searchTerm%" }
                .map { row ->
                    Food(
                        id = row[Foods.food_id],
                        name = row[Foods.food_name],
                        calories = row[Foods.calories_per_100g].toDouble().toInt(),
                        protein = row[Foods.protein_per_100g].toDouble().toInt(),
                        fat = row[Foods.fat_per_100g].toDouble().toInt(),
                        carbs = row[Foods.carbs_per_100g].toDouble().toInt(),
                    )
                }

        return@transaction foods
    }

/**
 * Stores calculated nutrition totals in one object.
 *
 * This is needed because several functions calculate calories, protein, fat,
 * and carbs together. Instead of returning or passing four separate values,
 * this class keeps them grouped as one result. This makes it easier for
 * calcNutrients(), logRecipeIngredients(), foodLogRecipe(), foodLogCustom(),
 * and calcAndLogCustomFood() to share nutrition values and keeps my code a bit neater.
 *
 * @param calories The total calories.
 * @param protein The total protein.
 * @param fat The total fat.
 * @param carbs The total carbohydrates.
 */
data class NutrientValues(val calories: Int, val protein: Int, val fat: Int, val carbs: Int)

/**
 * Calculates nutrition values for a food item based on the gram amount.
 *
 * This is used by logRecipeIngredients() and calcAndLogCustomFood() when food
 * is added to the current food log session. It gets the food's nutrition values
 * per 100g from the database, scales them using the selected gram amount by using
 * the multiplier, and returns the calculated calories, protein, fat, and carbs
 * together as NutrientValues.
 *
 * @param foodId The ID of the food item being calculated.
 * @param grams The amount of the food item in grams.
 * @return The calculated nutrition values, or zero values if the food is not found.
 */
fun calcNutrients(
    foodId: Int,
    grams: Int,
): NutrientValues {
    val multiplier = grams / GRAMS_PER_SERVING.toDouble()
    val row =
        Foods
            .selectAll()
            .where { Foods.food_id eq foodId }
            .firstOrNull() ?: return NutrientValues(0, 0, 0, 0)
    return NutrientValues(
        calories = (row[Foods.calories_per_100g].toDouble() * multiplier).toInt(),
        protein = (row[Foods.protein_per_100g].toDouble() * multiplier).toInt(),
        fat = (row[Foods.fat_per_100g].toDouble() * multiplier).toInt(),
        carbs = (row[Foods.carbs_per_100g].toDouble() * multiplier).toInt(),
    )
}

/**
 * Resets the current food log session.
 *
 * Used in configureFoodPostRoutes() by the food_log_reset POST
 * route when the user presses the reset button. It resets the nutrition
 * totals and removes all foods from the current meal session, then displays
 * the food log page with zero values.
 */
suspend fun ApplicationCall.foodLogReset() {
    sessions.set(CaloriesSession(0, 0, 0, 0))
    sessions.set(CurrentMealSession(emptyList()))
    respondRedirect("/food_log")
}

/**
 * Saves the current food log session to the user's diary.
 *
 * This is used in configureFoodPostRoutes() by the save_food_log POST
 * route when the user presses the save to diary button. It checks the user is
 * logged in, checks there are foods in the current meal session, saves those
 * foods to the diary using saveFoodLog(), then clears the current session.
 */
suspend fun ApplicationCall.saveCurrentFoodLog() {
    val mealType = getMealTypeByTime()
    val notes = ""
    val userSession = sessions.get<UserSession>()
    val userId = userSession?.let { getUserIdByEmail(it.email) }
    val currentMealSession = sessions.get<CurrentMealSession>()

    when {
        userId == null -> respondRedirect("/login")
        currentMealSession == null || currentMealSession.foods.isEmpty() -> respondRedirect("/food_log")
        else -> {
            saveFoodLog(
                userId = userId,
                mealType = mealType,
                notes = notes,
                foods = currentMealSession.foods,
            )

            sessions.set(CaloriesSession(0, 0, 0, 0))
            sessions.set(CurrentMealSession(emptyList()))

            respondRedirect("/food_log")
        }
    }
}
