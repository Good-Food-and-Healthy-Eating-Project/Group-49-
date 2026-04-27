package diettracker

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

    transaction {
        val ingredients =
            RecipeIngredients
                .selectAll()
                .where { RecipeIngredients.recipe_id eq recipeid }
                .map { row -> row }

        for (i in ingredients) {
            val foodId = i[RecipeIngredients.food_id]
            val grams = i[RecipeIngredients.quantity_g].toInt()
            val calories = calcCalcsById(foodId, grams)
            val protein = calcProteinById(foodId, grams)
            val fat = calcFatById(foodId, grams)
            val carbs = calcCarbsById(foodId, grams)
            addCalories += calories
            addProtein += protein
            addFat += fat
            addCarbs += carbs
        }
    }

    val caloriesSession = sessions.get<CaloriesSession>() ?: CaloriesSession(0, 0, 0, 0)
    val newTotalCals = caloriesSession.calories + addCalories
    val newTotalProtein = caloriesSession.protein + addProtein
    val newTotalFat = caloriesSession.fat + addFat
    val newTotalCarbs = caloriesSession.carbs + addCarbs

    sessions.set(CaloriesSession(newTotalCals, newTotalProtein, newTotalFat, newTotalCarbs))
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

        if (searchTerm.isBlank()) {
            return@transaction emptyList<Recipe>()
        }

        if (searchTerm.any { it.isDigit() }) {
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

        if (searchTerm.isBlank()) {
            return@transaction emptyList<Food>()
        }

        if (searchTerm.any { it.isDigit() }) {
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

fun calcProteinById(
    foodid: Int,
    grams: Int,
): Int {
    val multiplier = grams / GRAMS_PER_SERVING.toDouble()
    val proteinPer100g =
        Foods
            .selectAll()
            .where { Foods.food_id eq foodid }
            .map { row -> row[Foods.protein_per_100g].toDouble().toInt() }
            .firstOrNull() ?: return 0

    return (proteinPer100g * multiplier).toInt()
}

fun calcFatById(
    foodid: Int,
    grams: Int,
): Int {
    val multiplier = grams / GRAMS_PER_SERVING.toDouble()
    val fatPer100g =
        Foods
            .selectAll()
            .where { Foods.food_id eq foodid }
            .map { row -> row[Foods.fat_per_100g].toDouble().toInt() }
            .firstOrNull() ?: return 0

    return (fatPer100g * multiplier).toInt()
}

fun calcCarbsById(
    foodid: Int,
    grams: Int,
): Int {
    val multiplier = grams / GRAMS_PER_SERVING.toDouble()
    val carbsPer100g =
        Foods
            .selectAll()
            .where { Foods.food_id eq foodid }
            .map { row -> row[Foods.carbs_per_100g].toDouble().toInt() }
            .firstOrNull() ?: return 0

    return (carbsPer100g * multiplier).toInt()
}

suspend fun ApplicationCall.foodLogReset() {
    sessions.set(CaloriesSession(0, 0, 0, 0))
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
