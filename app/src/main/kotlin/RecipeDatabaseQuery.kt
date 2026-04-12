package diettracker

import diettracker.models.RecipeSummary
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.lowerCase
import org.jetbrains.exposed.v1.core.jdbc.selectAll
import org.jetbrains.exposed.v1.core.jdbc.transactions.transaction

object RecipeDatabaseQuery {
    fun searchRecipes(query: String): List<RecipeSummary> = transaction {
        Recipes.selectAll()
        .where {
            if (query.isBlank()) Op.true
            else Recipes.recipe_name.lowerCase() like "%${query.lowercase()}%"
        }
        .map { row ->
        RecipeSummary(
            id = row[Recipes.recipes_id],
            name = row[Recipes.recipe_name],
            thumbnail = row[Recipes.thumbnail_url]
        )}
    }
}