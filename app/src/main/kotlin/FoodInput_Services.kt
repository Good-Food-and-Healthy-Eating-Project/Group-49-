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
import diettracker.models.Food
import org.jetbrains.exposed.v1.core.*

suspend fun ApplicationCall.FoodLogPage() {
    respondTemplate("pages/client_dash/add_food.peb", model = emptyMap())
}



suspend fun ApplicationCall.FoodLogRecipe() {
    respondRedirect("/food_log")
        // gets recipe id and calls get recipe by id, calc calories etc and adds to calories section
        //val id = call.receiveParameters()["recipeId"]!!.toInt()
        //val recipe = getRecipeById(id)
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




fun getRecipeById(id: Int): Recipe? {
    return Recipes
        .selectAll()
        .where { Recipes.recipes_id eq id }
        .map { row -> Recipe(
            id = row[Recipes.recipes_id],
            name = row[Recipes.recipe_name],
        ) }
        .firstOrNull()
}

