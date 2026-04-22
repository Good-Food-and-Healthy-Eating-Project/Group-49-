package diettracker.db.tables

import org.jetbrains.exposed.v1.core.Table

object UserFavouritedRecipes : Table("user_favourite_recipes") {
    val user_id = integer("user_id").references(Users.user_id)
    val recipe_id = integer("recipe_id").references(Recipes.recipes_id)
    override val primaryKey = PrimaryKey(user_id, recipe_id)
}