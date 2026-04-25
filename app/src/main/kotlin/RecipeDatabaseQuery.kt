package diettracker

import diettracker.db.tables.Recipes
import diettracker.models.RecipeSummary
import diettracker.db.tables.UserFavouritedRecipes
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction


object RecipeDatabaseQuery {
    fun searchRecipes(query: String, favouriteIds: List<Int> = emptyList()): List<RecipeSummary> = transaction {
        Recipes.selectAll()
        .where {
            if (query.isBlank()) Op.TRUE
            else Recipes.recipe_name.lowerCase() like "%${query.lowercase()}%"
        }
        .apply { if (query.isBlank()) orderBy(Random()).limit(9) }

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
}