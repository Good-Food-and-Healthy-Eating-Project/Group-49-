package diettracker.db.tables

import org.jetbrains.exposed.v1.core.Table

/**
 * This table links clients to professionals and stores it in the database
 * **/
object ClientProfessionalLink : Table("client_professional_link") {
    val client_id = integer("client_id").references(Clients.client_id).uniqueIndex()
    val professional_id = integer("professional_id").references(Professionals.professional_id)
    override val primaryKey = PrimaryKey(client_id, professional_id)
}
