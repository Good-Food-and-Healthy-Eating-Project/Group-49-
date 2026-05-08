package diettracker.db.tables

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.javatime.timestamp

/**
 * This table stores each chats details.
 **/
object Chats : Table("chats") {
    val chat_id = integer("chat_id").autoIncrement()
    val client_id = integer("client_id").references(Clients.client_id).uniqueIndex()
    val professional_id = integer("professional_id").references(Professionals.professional_id)
    val created_at = timestamp("created_at")

    override val primaryKey = PrimaryKey(chat_id)
}
