package diettracker.db.tables

import org.jetbrains.exposed.v1.core.Table

object SavedMealFoods : Table("saved_meal_foods") {
    val saved_meal_food_id = integer("saved_meal_food_id").autoIncrement()
    val meal_id = integer("meal_id")
    val food_id = integer("food_id")
    val grams = integer("grams")

    override val primaryKey = PrimaryKey(saved_meal_food_id)
}