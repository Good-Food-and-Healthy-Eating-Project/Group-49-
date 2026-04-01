package diettracker.db.tables

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.javatime.date

object Clients : Table("clients") {
    val client_id = integer("client_id").references(Users.user_id)
    val data_of_birth = date("date_of_birth")
    val height_cm = integer("height_cm")
    val weight_kg = integer("weight_kg")

    override val primaryKey = PrimaryKey(client_id)
}
