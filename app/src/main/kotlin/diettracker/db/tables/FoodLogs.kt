package diettracker.db.tables

/**
 * This table stores a user's food log entries and saves them in the database
 **/

import diettracker.db.MAX_LEN
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.javatime.timestamp

object FoodLogs : Table("food_logs") {
    val food_log_id = integer("food_log_id").autoIncrement()
    val user_id = integer("user_id").references(Users.user_id)
    val log_date = timestamp("log_date")
    val meal_type = varchar("meal_type", MAX_LEN)
    val notes = text("notes")

    override val primaryKey = PrimaryKey(food_log_id)
}
