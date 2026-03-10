import org.jetbrains.exposed.sql.Table

object RecipeIngredients : Table("recipe_ingredients") {
    val recipe_id = integer("recipe_id")
    val food_id = integer("food_id")
    val quantity_g = decimal("quantity_g", 6, 2)

    override val primaryKey = PrimaryKey(recipe_id, food_id)
}