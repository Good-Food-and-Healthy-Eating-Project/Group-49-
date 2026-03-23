package diettracker
import diettracker.db.tables.Recipes
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.pebble.respondTemplate
import io.ktor.server.request.*
import org.jetbrains.exposed.v1.core.like
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import diettracker.models.Recipe
import diettracker.db.tables.Foods
import diettracker.db.tables.RecipeIngredients
import diettracker.models.Food
import org.jetbrains.exposed.v1.core.*

suspend fun ApplicationCall.FoodLogPage() {
    respondTemplate("pages/client_dash/add_food.peb", model = emptyMap())
}



suspend fun ApplicationCall.FoodLogRecipe() {
    var totalCalories = 0
    val params = receiveParameters()
    val recipeIdStr = params["recipeId"]
    val recipeid = recipeIdStr?.toIntOrNull()
    if (recipeid == null) {
        respondTemplate(
            "pages/client_dash/add_food.peb",
            mapOf("calories" to 0, "error" to "Invalid or missing recipeId: $recipeIdStr")
        )
        return
    }

    transaction {
        val ingredients = RecipeIngredients
            .selectAll()
            .where { RecipeIngredients.recipe_id eq recipeid }
            .map { row -> row }

        for (i in ingredients) {
            val foodId = i[RecipeIngredients.food_id]
            val grams = i[RecipeIngredients.quantity_g].toInt()
            val calories = calcCalcsById(foodId, grams)
            totalCalories += calories
        }
    }

    respondTemplate(
        "pages/client_dash/add_food.peb",
        mapOf("calories" to totalCalories)
    )
}

suspend fun ApplicationCall.FoodLogCustom() {
    respondRedirect("/food_log")
}




fun SearchRecipes(query: String): List<Recipe> = transaction {
    val searchTerm = query.lowercase()

    if (searchTerm.isBlank()) {
        return@transaction emptyList<Recipe>()
    }

    if (searchTerm.any { it.isDigit() }) {
        return@transaction emptyList<Recipe>()
    }

    val recipes = Recipes
    .selectAll()
    .where { (Recipes.recipe_name like "%$searchTerm%")}
    .map { row -> Recipe(
        id = row[Recipes.recipes_id],
        name = row[Recipes.recipe_name],
    ) }

    return@transaction recipes
}

fun SearchFoods(foodquery: String): List<Food> = transaction {
    val searchTerm = foodquery.lowercase()

    if (searchTerm.isBlank()) {
        return@transaction emptyList<Food>()
    }

    if (searchTerm.any { it.isDigit() }) {
        return@transaction emptyList<Food>()
    }

    val foods = Foods
    .selectAll()
    .where { (Foods.food_name like "%$searchTerm%")}
    .map { row -> Food(
        id = row[Foods.food_id],
        name = row[Foods.food_name],
        calories = row[Foods.calories_per_100g].toDouble().toInt()
    ) }

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

fun calcCalcsById(foodid: Int, grams: Int): Int {
    val multiplier = grams / 100.0
    val caloriesPer100g = Foods
    .selectAll()
    .where { Foods.food_id eq foodid }
    .map { row -> row[Foods.calories_per_100g].toDouble().toInt() }
    .firstOrNull() ?: return 0

    return (caloriesPer100g * multiplier).toInt()
}

