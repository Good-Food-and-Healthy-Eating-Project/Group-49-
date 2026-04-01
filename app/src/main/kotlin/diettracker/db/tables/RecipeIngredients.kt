package diettracker.db.tables

import diettracker.db.MAX_LEN
import org.jetbrains.exposed.v1.core.Table

object RecipeIngredients : Table("recipe_ingredients") {
    val recipe_id = integer("recipe_id").references(Recipes.recipes_id)
    val food_id = integer("food_id").references(Foods.food_id)
    val quantity_g = decimal("quantity_g", 8, 2)
    val original_measure = varchar("original_measure", MAX_LEN).nullable()

    override val primaryKey = PrimaryKey(recipe_id, food_id)
}
