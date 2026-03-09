package diettracker.db.tables

import org.jetbrains.exposed.v1.core.Table
import diettracker.db.MAX_LEN


object Professionals : Table("professionals"){
    val professional_id = integer("professional_id").references(Users.user_id)
    val job_title = varchar("job_title", MAX_LEN)
    val organistation = varchar("organisation",MAX_LEN)
    val bio = text("bio")

    override val primaryKey = PrimaryKey(professional_id)
}