package diettracker.routing

import diettracker.CaloriesSession
import diettracker.UserSession
import diettracker.addSavedMealToLog
import diettracker.foodLogCustom
import diettracker.foodLogRecipe
import diettracker.foodLogReset
import diettracker.getSavedMeals
import diettracker.getUserIdByEmail
import diettracker.saveCurrentFoodLog
import diettracker.saveCurrentMeal
import diettracker.searchFoods
import diettracker.searchRecipes
import io.ktor.server.pebble.respondTemplate
import io.ktor.server.response.respondRedirect
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.sessions.get
import io.ktor.server.sessions.sessions

private const val DEFAULT_GRAMS = 100

/**
 * Sets up all the food related routes for the feature.
 *
 * It includes the main food log page, food log actions, recipe searches,
 * and food searches.
 */
fun Route.configureFoodRoutes() {
    configureFoodLogRoute()
    configureFoodPostRoutes()
    configureRecipeSearchRoute()
    configureFoodSearchRoute()
}

/**
 * Handles requests to the food log page.
 *
 * This route checks if the user is logged in then loads their saved meals,
 * gets the current calorie session totals and displays recipe or food
 * search results when a search query is provided.
 */
private data class NutritionSessionData(
    val calories: Int,
    val protein: Int,
    val fat: Int,
    val carbs: Int,
)

private fun getNutritionSession(call: io.ktor.server.application.ApplicationCall): NutritionSessionData {
    val session = call.sessions.get<CaloriesSession>()

    return NutritionSessionData(
        calories = session?.calories ?: 0,
        protein = session?.protein ?: 0,
        fat = session?.fat ?: 0,
        carbs = session?.carbs ?: 0,
    )
}

private fun getSavedMealsForUser(call: io.ktor.server.application.ApplicationCall): List<Any> {
    val email = call.sessions.get<UserSession>()?.email
    val clientId = email?.let { getUserIdByEmail(it) }
    return clientId?.let { getSavedMeals(it) } ?: emptyList()
}

private suspend fun respondAddFoodPage(
    call: io.ktor.server.application.ApplicationCall,
    extra: Map<String, Any> = emptyMap(),
) {
    val nutrition = getNutritionSession(call)
    val savedMeals = getSavedMealsForUser(call)

    val baseModel =
        mutableMapOf<String, Any>(
            "calories" to nutrition.calories,
            "protein" to nutrition.protein,
            "fat" to nutrition.fat,
            "carbs" to nutrition.carbs,
            "savedMeals" to savedMeals,
        )

    baseModel.putAll(extra)

    call.respondTemplate("pages/client_dash/add_food.peb", baseModel)
}

private fun Route.configureFoodLogRoute() {
    get("/food_log") {
        call.sessions.get<UserSession>() ?: return@get call.respondRedirect("/Login")

        val recipeQuery = call.request.queryParameters["query"]
        val foodQuery = call.request.queryParameters["foodquery"]
        val success = call.request.queryParameters["success"]

        when {
            !recipeQuery.isNullOrBlank() -> {
                val recipes = searchRecipes(recipeQuery)
                respondAddFoodPage(call, mapOf("recipes" to recipes))
            }

            !foodQuery.isNullOrBlank() -> {
                val foods = searchFoods(foodQuery)
                respondAddFoodPage(call, mapOf("foods" to foods))
            }

            else -> {
                val extra =
                    if (success != null) {
                        mapOf("success" to success)
                    } else {
                        emptyMap()
                    }

                respondAddFoodPage(call, extra)
            }
        }
    }
}

/**
 * Handles form submissions from the food log page.
 *
 * This includes adding recipe food, adding custom food, resetting the food log,
 * saving a meal, adding a saved meal to the log, and saving the current food log
 * to the diary.
 */
private fun Route.configureFoodPostRoutes() {
    post("/food_log_recipe") { call.foodLogRecipe() }
    post("/food_log_custom") { call.foodLogCustom() }
    post("/food_log_reset") { call.foodLogReset() }
    post("/save_meal") { call.saveCurrentMeal() }
    post("/add_saved_meal_to_log") { call.addSavedMealToLog() }
    post("/save_food_log") { call.saveCurrentFoodLog() }
}

/**
 * Handles recipe search requests from the food log page.
 *
 * It gets the recipe search query, finds matching recipes by calling a
 * helper function, loads the user's saved meals and current nutrition totals,
 * then displays the add food page with the recipe results.
 */
private fun Route.configureRecipeSearchRoute() {
    get("/recipe_search") {
        val query = call.request.queryParameters["query"] ?: ""
        val recipes = searchRecipes(query)
        val email = call.sessions.get<UserSession>()?.email
        val clientId = email?.let { getUserIdByEmail(it) }
        val savedMeals = clientId?.let { getSavedMeals(it) } ?: emptyList()

        val session = call.sessions.get<CaloriesSession>()
        val calories = session?.calories ?: 0
        val protein = session?.protein ?: 0
        val fat = session?.fat ?: 0
        val carbs = session?.carbs ?: 0

        call.respondTemplate(
            "pages/client_dash/add_food.peb",
            mapOf(
                "recipes" to recipes,
                "calories" to calories,
                "protein" to protein,
                "fat" to fat,
                "carbs" to carbs,
                "savedMeals" to savedMeals,
            ),
        )
    }
}

/**
 * Handles food search requests from the food log page.
 *
 * It gets the food search query and gram amount, finds matching foods and
 * then loads the user's saved meals and current nutrition totals,
 * then displays the add food page with the food results.
 */
private fun Route.configureFoodSearchRoute() {
    get("/food_search") {
        val query = call.request.queryParameters["foodquery"] ?: ""
        val foods = searchFoods(query)
        val grams = call.request.queryParameters["grams"]?.toIntOrNull() ?: DEFAULT_GRAMS
        val email = call.sessions.get<UserSession>()?.email
        val clientId = email?.let { getUserIdByEmail(it) }
        val savedMeals = clientId?.let { getSavedMeals(it) } ?: emptyList()

        val session = call.sessions.get<CaloriesSession>()
        val calories = session?.calories ?: 0
        val protein = session?.protein ?: 0
        val fat = session?.fat ?: 0
        val carbs = session?.carbs ?: 0

        val success = call.request.queryParameters["success"]
        call.respondTemplate(
            "pages/client_dash/add_food.peb",
            mapOf(
                "foods" to foods,
                "calories" to calories,
                "protein" to protein,
                "fat" to fat,
                "carbs" to carbs,
                "grams" to grams,
                "savedMeals" to savedMeals,
                // Non nullable default here
                "success" to (success ?: ""),
            ),
        )
    }
}
