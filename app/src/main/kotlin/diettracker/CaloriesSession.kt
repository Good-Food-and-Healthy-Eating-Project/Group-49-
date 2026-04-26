package diettracker

import diettracker.db.tables.FoodLogItems
import diettracker.db.tables.FoodLogs
import diettracker.db.tables.Foods
import diettracker.db.tables.RecipeIngredients
import diettracker.db.tables.Recipes
import diettracker.models.Food
import diettracker.models.Recipe
import io.ktor.server.application.ApplicationCall
import io.ktor.server.pebble.respondTemplate
import io.ktor.server.request.receiveParameters
import io.ktor.server.response.respondRedirect
import io.ktor.server.sessions.get
import io.ktor.server.sessions.sessions
import io.ktor.server.sessions.set
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.like
import org.jetbrains.exposed.v1.core.lowerCase
import org.jetbrains.exposed.v1.javatime.date
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.time.Instant
import java.time.LocalDate

const val GRAMS_PER_SERVING = 100

@Serializable
data class CaloriesSession(val calories: Int)

suspend fun ApplicationCall.foodLogPage() {
    val caloriesSession = sessions.get<CaloriesSession>()
    val calories = caloriesSession?.calories ?: 0
    respondTemplate("pages/client_dash/add_food.peb", model = mapOf("calories" to calories))
}

suspend fun ApplicationCall.foodLogRecipe() {
    var addCalories = 0
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
            val calories = calcCalcsById(foodId, quantity.toInt())
            addCalories += calories

            if (logId != null) {
                FoodLogItems.insert {
                    it[FoodLogItems.food_log_id] = logId
                    it[FoodLogItems.food_id] = foodId
                    it[FoodLogItems.quantity_g] = quantity
                }
            }
        }
    }

    // Get or create session
    val caloriesSession = sessions.get<CaloriesSession>() ?: CaloriesSession(0)
    val newTotal = caloriesSession.calories + addCalories
    sessions.set(CaloriesSession(newTotal))
    respondRedirect("/food_log")
}

suspend fun ApplicationCall.foodLogCustom() {
    val caloriesSession = sessions.get<CaloriesSession>() ?: CaloriesSession(0)
    val params = receiveParameters()
    val foodIdStr = params["foodId"]
    val gramsStr = params["grams"]
    val foodId = foodIdStr?.toIntOrNull()
    val grams = gramsStr?.toIntOrNull() ?: GRAMS_PER_SERVING
    var addCalories = 0

    if (foodId == null) {
        respondTemplate(
            "pages/client_dash/add_food.peb",
            mapOf("calories" to 0, "error" to "Invalid or missing foodId: $foodIdStr"),
        )
        return
    }

    val email = sessions.get<UserSession>()?.email
    val userId = email?.let { getUserIdByEmail(it) }

    transaction {
        val calories = calcCalcsById(foodId, grams)
        addCalories += calories

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
    }

    val newTotal = caloriesSession.calories + addCalories
    sessions.set(CaloriesSession(newTotal))

    respondTemplate(
        "pages/client_dash/add_food.peb",
        mapOf("calories" to newTotal),
    )
}

fun searchRecipes(query: String): List<Recipe> =
    transaction {
        val searchTerm = query.lowercase()

        if (searchTerm.isBlank()) {
            return@transaction emptyList<Recipe>()
        }

        if (searchTerm.any { it.isDigit() }) {
            return@transaction emptyList<Recipe>()
        }

        val recipes =
            Recipes
                .selectAll()
                .where { (Recipes.recipe_name.lowerCase() like "%$searchTerm%") }
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

        if (searchTerm.isBlank()) {
            return@transaction emptyList<Food>()
        }

        if (searchTerm.any { it.isDigit() }) {
            return@transaction emptyList<Food>()
        }

        val foods =
            Foods
                .selectAll()
                .where { (Foods.food_name.lowerCase() like "%$searchTerm%") }
                .map { row ->
                    Food(
                        id = row[Foods.food_id],
                        name = row[Foods.food_name],
                        calories = row[Foods.calories_per_100g].toDouble().toInt(),
                    )
                }

        return@transaction foods
    }

/*fun getRecipeById(id: Int): Recipe? { // add ? in case user press add without selecting a recipe
    return Recipes
        .selectAll()
        .where { Recipes.recipes_id eq id }
        .map { row -> Recipe(
            id = row[Recipes.recipes_id],
            name = row[Recipes.recipe_name],
        ) }
        .firstOrNull()
} */

fun calcCalcsById(
    foodid: Int,
    grams: Int,
): Int {
    val multiplier = grams / GRAMS_PER_SERVING.toDouble()
    val caloriesPer100g =
        Foods
            .selectAll()
            .where { Foods.food_id eq foodid }
            .map { row -> row[Foods.calories_per_100g].toDouble().toInt() }
            .firstOrNull() ?: return 0

    return (caloriesPer100g * multiplier).toInt()
}

suspend fun ApplicationCall.foodLogReset() {
    sessions.set(CaloriesSession(0))
    respondTemplate(
        "pages/client_dash/add_food.peb",
        mapOf("calories" to 0),
    )
}

data class DailyNutritionSummary(
    val totalCalories: Double,
    val totalProtein: Double,
    val totalCarbs: Double,
    val totalFat: Double,
)

fun getDailyNutritionSummary(
    userId: Int,
    today: LocalDate,
): DailyNutritionSummary {
    val dailyOverview =
        transaction {
            (FoodLogs innerJoin FoodLogItems innerJoin Foods)
                .selectAll()
                .where {
                    (FoodLogs.user_id eq userId) and (FoodLogs.log_date.date() eq today)
                }
                .map {
                    val quantity = it[FoodLogItems.quantity_g].toDouble()
                    val caloriesPer100g = it[Foods.calories_per_100g].toDouble()
                    val proteinPer100g = it[Foods.protein_per_100g].toDouble()
                    val carbsPer100g = it[Foods.carbs_per_100g].toDouble()
                    val fatPer100g = it[Foods.fat_per_100g].toDouble()
                    val convert = quantity / GRAMS_PER_SERVING.toDouble()
                    mapOf(
                        "calories" to caloriesPer100g * convert,
                        "protein" to proteinPer100g * convert,
                        "carbs" to carbsPer100g * convert,
                        "fat" to fatPer100g * convert,
                    )
                }
        }
    return DailyNutritionSummary(
        totalCalories = dailyOverview.sumOf { it["calories"] as Double },
        totalProtein = dailyOverview.sumOf { it["protein"] as Double },
        totalCarbs = dailyOverview.sumOf { it["carbs"] as Double },
        totalFat = dailyOverview.sumOf { it["fat"] as Double },
    )
}
