package diettracker.db.tables

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.javatime.date

// Table which stores user information which will later be displayed in settings
// Nullable values to allow for skipping
private const val GOAL_MAX_LEN = 50

object Clients : Table("clients") {
    val client_id = integer("client_id").references(Users.user_id)
    val data_of_birth = date("date_of_birth").nullable()
    val height_cm = integer("height_cm").nullable()
    val weight_kg = integer("weight_kg").nullable()
    val goal = varchar("goal", GOAL_MAX_LEN).nullable()

    override val primaryKey = PrimaryKey(client_id)
}
