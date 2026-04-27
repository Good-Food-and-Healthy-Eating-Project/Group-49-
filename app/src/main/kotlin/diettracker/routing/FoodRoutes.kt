package diettracker.routing

import diettracker.getUserIdByEmail
import diettracker.getSavedMeals
import diettracker.addSavedMealToLog
import diettracker.CaloriesSession
import diettracker.UserSession
import diettracker.foodLogCustom
import diettracker.foodLogPage
import diettracker.foodLogRecipe
import diettracker.foodLogReset
import diettracker.saveCurrentMeal
import diettracker.searchFoods
import diettracker.searchRecipes
import io.ktor.server.pebble.respondTemplate
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.sessions.get
import io.ktor.server.sessions.sessions

private const val DEFAULT_GRAMS = 100

fun Route.configureFoodRoutes() {
    configureFoodLogRoute()
    configureFoodPostRoutes()
    configureRecipeSearchRoute()
    configureFoodSearchRoute()
}

private fun Route.configureFoodLogRoute() {
    get("/food_log") {
        val recipeQuery = call.request.queryParameters["query"]
        val foodQuery = call.request.queryParameters["foodquery"]
        val email = call.sessions.get<UserSession>()?.email
        val clientId = email?.let { getUserIdByEmail(it) }
        val savedMeals = clientId?.let { getSavedMeals(it) } ?: emptyList()

        val session = call.sessions.get<CaloriesSession>()
        val calories = session?.calories ?: 0
        val protein = session?.protein ?: 0
        val fat = session?.fat ?: 0
        val carbs = session?.carbs ?: 0

        when {
            recipeQuery != null && recipeQuery.isNotBlank() -> {
                val recipes = searchRecipes(recipeQuery)
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

            foodQuery != null && foodQuery.isNotBlank() -> {
                val foods = searchFoods(foodQuery)
                call.respondTemplate(
                    "pages/client_dash/add_food.peb",
                    mapOf(
                        "foods" to foods,
                        "calories" to calories,
                        "protein" to protein,
                        "fat" to fat,
                        "carbs" to carbs,
                        "savedMeals" to savedMeals,
                    ),
                )
            }

            else -> {
                call.respondTemplate(
                    "pages/client_dash/add_food.peb",
                    mapOf(
                        "calories" to calories,
                        "protein" to protein,
                        "fat" to fat,
                        "carbs" to carbs,
                        "savedMeals" to savedMeals,
                    ),
                )
            }
        }
    }
}

private fun Route.configureFoodPostRoutes() {
    post("/food_log_recipe") { call.foodLogRecipe() }
    post("/food_log_custom") { call.foodLogCustom() }
    post("/food_log_reset") { call.foodLogReset() }
    post("/save_meal") { call.saveCurrentMeal() }
    post("/add_saved_meal_to_log") { call.addSavedMealToLog() }
}

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
            ),
        )
    }
}
