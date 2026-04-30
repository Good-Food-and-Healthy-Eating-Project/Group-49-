package diettracker

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
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

const val GRAMS_PER_SERVING = 100

@Serializable
data class CaloriesSession(
    val calories: Int,
    val protein: Int,
    val fat: Int,
    val carbs: Int,
)

suspend fun ApplicationCall.foodLogPage() {
    val caloriesSession = sessions.get<CaloriesSession>()

    respondTemplate(
        "pages/client_dash/add_food.peb",
        mapOf(
            "calories" to (caloriesSession?.calories ?: 0),
            "protein" to (caloriesSession?.protein ?: 0),
            "fat" to (caloriesSession?.fat ?: 0),
            "carbs" to (caloriesSession?.carbs ?: 0),
        ),
    )
}

suspend fun ApplicationCall.foodLogRecipe() {
    var addCalories = 0
    var addProtein = 0
    var addFat = 0
    var addCarbs = 0
    val params = receiveParameters()
    val recipeIdStr = params["recipeId"]
    val recipeid = recipeIdStr?.toIntOrNull()

    if (recipeid == null) {
        respondTemplate(
            "pages/client_dash/add_food.peb",
            mapOf(
                "calories" to 0,
                "error" to "Invalid or missing recipeId: $recipeIdStr",
            ),
        )
        return
    }
    val foodsToAdd = mutableListOf<CurrentMealFood>()
    transaction {
        val ingredients =
            RecipeIngredients
                .selectAll()
                .where { RecipeIngredients.recipe_id eq recipeid }
                .map { row -> row }

        for (i in ingredients) {
            val foodId = i[RecipeIngredients.food_id]
            val grams = i[RecipeIngredients.quantity_g].toInt()
            foodsToAdd += CurrentMealFood(foodId = foodId, grams = grams)
            val calories = calcCalcsById(foodId, grams)
            val protein = calcProteinById(foodId, grams)
            val fat = calcFatById(foodId, grams)
            val carbs = calcCarbsById(foodId, grams)
            addCalories += calories
            addProtein += protein
            addFat += fat
            addCarbs += carbs

            foodsToAdd.add(CurrentMealFood(foodId, grams))
        }
    }

    val caloriesSession = sessions.get<CaloriesSession>() ?: CaloriesSession(0, 0, 0, 0)
    val newTotalCals = caloriesSession.calories + addCalories
    val newTotalProtein = caloriesSession.protein + addProtein
    val newTotalFat = caloriesSession.fat + addFat
    val newTotalCarbs = caloriesSession.carbs + addCarbs

    sessions.set(CaloriesSession(newTotalCals, newTotalProtein, newTotalFat, newTotalCarbs))
    val currentMeal = sessions.get<CurrentMealSession>() ?: CurrentMealSession(emptyList())
    sessions.set(
        CurrentMealSession(
            currentMeal.foods + foodsToAdd,
        ),
    )
    respondRedirect("/food_log")
}

suspend fun ApplicationCall.foodLogCustom() {
    val caloriesSession = sessions.get<CaloriesSession>() ?: CaloriesSession(0, 0, 0, 0)
    val params = receiveParameters()
    val foodIdStr = params["foodId"]
    val gramsStr = params["grams"]
    val foodId = foodIdStr?.toIntOrNull()
    val grams = gramsStr?.toIntOrNull() ?: GRAMS_PER_SERVING
    var addCalories = 0
    var addProtein = 0
    var addCarbs = 0
    var addFat = 0

    if (foodId == null) {
        respondTemplate(
            "pages/client_dash/add_food.peb",
            mapOf(
                "calories" to 0,
                "error" to "Invalid or missing foodId: $foodIdStr",
            ),
        )
        return
    }

    transaction {
        val calories = calcCalcsById(foodId, grams)
        val protein = calcProteinById(foodId, grams)
        val fat = calcFatById(foodId, grams)
        val carbs = calcCarbsById(foodId, grams)
        addCalories += calories
        addProtein += protein
        addFat += fat
        addCarbs += carbs
    }

    val newTotalCals = caloriesSession.calories + addCalories
    val newTotalProtein = caloriesSession.protein + addProtein
    val newTotalFat = caloriesSession.fat + addFat
    val newTotalCarbs = caloriesSession.carbs + addCarbs

    sessions.set(CaloriesSession(newTotalCals, newTotalProtein, newTotalFat, newTotalCarbs))
    val currentMeal = sessions.get<CurrentMealSession>() ?: CurrentMealSession(emptyList())
    sessions.set(
        CurrentMealSession(
            currentMeal.foods + CurrentMealFood(foodId = foodId, grams = grams),
        ),
    )

    respondRedirect("/food_log")
}

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

suspend fun ApplicationCall.foodLogReset() {
    sessions.set(CaloriesSession(0, 0, 0, 0))
    sessions.set(CurrentMealSession(emptyList()))
    respondRedirect("/food_log")
}

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
