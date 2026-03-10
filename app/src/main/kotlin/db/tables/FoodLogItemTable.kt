package diettracker.db.tables

import org.jetbrains.exposed.v1.core.Table


object FoodLogItems : Table("food_log_items"){
    val food_log_item_id = integer("food_log_item_id").autoIncrement()
    val food_log_id = integer("food_log_id").references(FoodLogs.food_log_id)
    val food_id = integer("food_id").references(Foods.food_id)
    val quantity_g = decimal("quantity_g",6,2)
    override val primaryKey = PrimaryKey(food_log_item_id)
}