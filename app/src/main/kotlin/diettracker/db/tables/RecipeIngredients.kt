package diettracker.db.tables

import diettracker.db.MAX_LEN
import org.jetbrains.exposed.v1.core.Table

private const val QUANTITY_PRECISION = 8
private const val QUANTITY_SCALE = 2

object RecipeIngredients : Table("recipe_ingredients") {
    val recipe_id = integer("recipe_id").references(Recipes.recipes_id)
    val food_id = integer("food_id").references(Foods.food_id)
    val quantity_g = decimal("quantity_g", QUANTITY_PRECISION, QUANTITY_SCALE)
    val original_measure = varchar("original_measure", MAX_LEN).nullable()

    override val primaryKey = PrimaryKey(recipe_id, food_id)
}
