package diettracker.db.tables

/**
 * This table stores the available roles in the system and saves them in the database
 **/

import diettracker.db.MAX_LEN
import org.jetbrains.exposed.v1.core.Table

object Roles : Table("roles") {
    val role_id = integer("role_id").autoIncrement()
    val role_name = varchar("role_name", MAX_LEN).uniqueIndex()

    override val primaryKey = PrimaryKey(role_id)
}
