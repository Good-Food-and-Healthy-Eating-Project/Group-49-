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

@Serializable
data class CaloriesSession(val calories: Int = 0, val protein: Int = 0, val fat: Int = 0, val carbs: Int = 0)

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

private data class RecipeLogResult(val foods: List<CurrentMealFood>, val nutrients: NutrientValues)

private fun logRecipeIngredients(
    recipeid: Int,
    userId: Int?,
): RecipeLogResult {
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

        val logId =
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
            logId?.let {
                FoodLogItems.insert { row ->
                    row[FoodLogItems.food_log_id] = it
                    row[FoodLogItems.food_id] = foodId
                    row[FoodLogItems.quantity_g] = quantity
                }
            }
        }
    }
    return RecipeLogResult(foodsToAdd, NutrientValues(addCalories, addProtein, addFat, addCarbs))
}

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

    val email = sessions.get<UserSession>()?.email
    val userId = email?.let { getUserIdByEmail(it) }
    val result = logRecipeIngredients(recipeid, userId)

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

private fun calcAndLogCustomFood(
    userId: Int?,
    foodId: Int,
    grams: Int,
): NutrientValues =
    transaction {
        val n = calcNutrients(foodId, grams)
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
        n
    }

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

    respondTemplate(
        "pages/client_dash/add_food.peb",
        mapOf(
            "calories" to newTotalCals,
            "protein" to newTotalProtein,
            "fat" to newTotalFat,
            "carbs" to newTotalCarbs,
        ),
    )
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

data class NutrientValues(val calories: Int, val protein: Int, val fat: Int, val carbs: Int)

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

suspend fun ApplicationCall.foodLogReset() {
    sessions.set(CaloriesSession(0, 0, 0, 0))
    sessions.set(CurrentMealSession(emptyList()))
    respondTemplate(
        "pages/client_dash/add_food.peb",
        mapOf(
            "calories" to 0,
            "protein" to 0,
            "fat" to 0,
            "carbs" to 0,
        ),
    )
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

