package diettracker.db.tables

/**
 * This table links saved meals to their food items and stores quantities in the database
 **/

import org.jetbrains.exposed.v1.core.Table

object SavedMealFoods : Table("saved_meal_foods") {
    val saved_meal_food_id = integer("saved_meal_food_id").autoIncrement()
    val meal_id = integer("meal_id").references(SavedMeals.meal_id)
    val food_id = integer("food_id").references(Foods.food_id)
    val grams = integer("grams")

    override val primaryKey = PrimaryKey(saved_meal_food_id)
}
