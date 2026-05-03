package diettracker.db.tables

import diettracker.db.MAX_LEN
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.javatime.timestamp

private const val BODY_MAX_LENGTH_MULTIPLIER = 4

object Messages : Table("messages") {
    val message_id = integer("message_id").autoIncrement()
    val chat_id = integer("chat_id").references(Chats.chat_id)
    val senders_user_id = integer("sender_user_id").references(Users.user_id)
    val body = varchar("body", MAX_LEN * BODY_MAX_LENGTH_MULTIPLIER)
    val created_at = timestamp("created_at")
    val read_at = timestamp("read_at").nullable()

    override val primaryKey = PrimaryKey(message_id)
}
