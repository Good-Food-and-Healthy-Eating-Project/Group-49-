package diettracker.db.tables

import org.jetbrains.exposed.v1.core.Table


object ClientProfessionalLink : Table("client_professional_link"){
    val client_id = integer("client_id").references(Clients.client_id)
    val professional_id = integer("professional_id").references(Professionals.professional_id)
    override val primaryKey = PrimaryKey(client_id,professional_id)
}