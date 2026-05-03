package diettracker.db.tables

/**
 * This table stores individual food items within a food log entry and links them to the food and log tables
 **/

import org.jetbrains.exposed.v1.core.Table

private const val QUANTITY_PRECISION = 6
private const val QUANTITY_SCALE = 2

object FoodLogItems : Table("food_log_items") {
    val food_log_item_id = integer("food_log_item_id").autoIncrement()
    val food_log_id = integer("food_log_id").references(FoodLogs.food_log_id)
    val food_id = integer("food_id").references(Foods.food_id)
    val quantity_g = decimal("quantity_g", QUANTITY_PRECISION, QUANTITY_SCALE)
    override val primaryKey = PrimaryKey(food_log_item_id)
}
