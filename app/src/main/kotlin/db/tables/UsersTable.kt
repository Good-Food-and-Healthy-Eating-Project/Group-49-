import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.javatime.timestamp

const val MAX_LEN = 128

object Users : Table("users"){
    val user_id = integer("id").autoIncrement()
    val first_name = varchar("first_name", MAX_LEN)
    val second_name = varchar("second_name", MAX_LEN)
    val email = varchar("email",MAX_LEN)
    val password_hash = varchar("password_hash", 255)
    val created_at = timestamp("created_at")

    override val primaryKey = PrimaryKey(user_id)
}