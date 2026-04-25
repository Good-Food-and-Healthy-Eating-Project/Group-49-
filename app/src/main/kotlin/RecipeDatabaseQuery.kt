package diettracker

import diettracker.db.tables.Recipes
import diettracker.db.tables.UserFavouritedRecipes
import diettracker.db.tables.Foods
import diettracker.db.tables.RecipeIngredients
import diettracker.models.RecipeSummary
import diettracker.models.RecipeDetails
import diettracker.models.RecipeIngredientDetails

import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction


object RecipeDatabaseQuery {
    fun searchRecipes(query: String, favouriteIds: List<Int> = emptyList(), category: String = ""): List<RecipeSummary> = transaction {
        Recipes.selectAll()
        .where {
            val queryCondition = if (query.isBlank()) Op.TRUE
                else Recipes.recipe_name.lowerCase() like "%${query.lowercase()}%"
            val categoryCondition = if (category.isBlank()) Op.TRUE
                else Recipes.category eq category
            queryCondition and categoryCondition
        }
        .apply { if (query.isBlank() && category.isBlank()) orderBy(Random()).limit(9) }

        .map { row ->
            RecipeSummary(
                id = row[Recipes.recipes_id],
                name = row[Recipes.recipe_name],
                thumbnail = row[Recipes.thumbnail_url],
                isFavourited = row[Recipes.recipes_id] in favouriteIds
            )
        }
    }

    fun addFavourite(userId: Int, recipeId: Int) = transaction {
        val exists = UserFavouritedRecipes
            .selectAll()
            .where { UserFavouritedRecipes.user_id eq userId and (UserFavouritedRecipes.recipe_id eq recipeId) }
            .count() > 0
        if (!exists) {
            UserFavouritedRecipes.insert {
                it[user_id] = userId
                it[recipe_id] = recipeId
            }
        }
    }

    fun removeFavourite(userId: Int, recipeId: Int) = transaction {
        UserFavouritedRecipes.deleteWhere {
            UserFavouritedRecipes.user_id eq userId and
            (UserFavouritedRecipes.recipe_id eq recipeId)
        }
    }

    fun getFavourites(userId: Int): List<Int> = transaction {
        val results = UserFavouritedRecipes
            .selectAll()
            .where { UserFavouritedRecipes.user_id eq userId }
            .map { row -> row[UserFavouritedRecipes.recipe_id] }
        println("[/] getFavourites for userId $userId: $results")
        results
    }

    fun getFavouriteRecipes(favouriteIds: List<Int>): List<RecipeSummary> = transaction {
        Recipes.selectAll()
            .where { Recipes.recipes_id inList favouriteIds }
            .map { row ->
                RecipeSummary(
                    id = row[Recipes.recipes_id],
                    name = row[Recipes.recipe_name],
                    thumbnail = row[Recipes.thumbnail_url],
                    isFavourited = true
                )
            }
    }

    fun getRecipeById(recipeId: Int): RecipeDetails? = transaction {
        val recipe = Recipes
            .selectAll()
            .where { Recipes.recipes_id eq recipeId }
            .firstOrNull() ?: return@transaction null
        
        val ingredients = (RecipeIngredients innerJoin Foods)
            .selectAll()
            .where { RecipeIngredients.recipe_id eq recipeId }
            .map { row ->
                RecipeIngredientDetails(
                    foodName = row[Foods.food_name],
                    quantityGrams = row[RecipeIngredients.quantity_g].toDouble(),
                    humanReadableMeasure = row[RecipeIngredients.original_measure]
                )
            }
        
        RecipeDetails(
            id = recipe[Recipes.recipes_id],
            name = recipe[Recipes.recipe_name],
            thumbnail = recipe[Recipes.thumbnail_url],
            category = recipe[Recipes.category],
            area = recipe[Recipes.area],
            instructions = recipe[Recipes.instructions],
            ingredients = ingredients
        )
    }

    fun getCategories(): List<String> = transaction {
        Recipes.selectAll()
            .mapNotNull { row -> row[Recipes.category] }
            .distinct()
            .sorted()
    }
}