package diettracker.db.tables

/**
 * This table stores meals saved by clients and stores them in the database
 **/

import diettracker.db.MAX_LEN
import org.jetbrains.exposed.v1.core.Table

object SavedMeals : Table("saved_meals") {
    val meal_id = integer("meal_id").autoIncrement()
    val client_id = integer("client_id").references(Clients.client_id)
    val meal_name = varchar("meal_name", MAX_LEN)

    override val primaryKey = PrimaryKey(meal_id)
}
