package diettracker.db.tables

import org.jetbrains.exposed.v1.core.Table

/**
 * This table links clients to professionals and stores it in the database
 * Giving consent before linking to a professional was added
 * So that users know that their data will be shared according to UK GDPR regulations
 * Ensure transparency with how data is used
 * **/
object ClientProfessionalLink : Table("client_professional_link") {
    val client_id = integer("client_id").references(Clients.client_id)
    val professional_id = integer("professional_id").references(Professionals.professional_id)
    val consent_given = bool("consent_given").default(false)
    override val primaryKey = PrimaryKey(client_id, professional_id)
}
