package diettracker.db.tables

/**
 * This table stores professional-specific details and links them to the users table in the database
 **/

import diettracker.db.MAX_LEN
import org.jetbrains.exposed.v1.core.Table

object Professionals : Table("professionals") {
    val professional_id = integer("professional_id").references(Users.user_id)
    val job_title = varchar("job_title", MAX_LEN)
    val organistation = varchar("organisation", MAX_LEN)
    val bio = text("bio")

    override val primaryKey = PrimaryKey(professional_id)
}
