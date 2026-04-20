package diettracker

import diettracker.db.tables.Recipes
import diettracker.models.RecipeSummary
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

object RecipeDatabaseQuery {
    fun searchRecipes(query: String): List<RecipeSummary> = transaction {
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
            thumbnail = row[Recipes.thumbnail_url]
        )}
    }
}