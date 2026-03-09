package diettracker.db.tables

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.javatime.timestamp
import diettracker.db.MAX_LEN

object Users : Table("users"){
    val user_id = integer("user_id").autoIncrement()
    val first_name = varchar("first_name", MAX_LEN)
    val second_name = varchar("second_name", MAX_LEN)
    val email = varchar("email",MAX_LEN).uniqueIndex()
    val password_hash = varchar("password_hash", 255)
    val created_at = timestamp("created_at")

    override val primaryKey = PrimaryKey(user_id)
}