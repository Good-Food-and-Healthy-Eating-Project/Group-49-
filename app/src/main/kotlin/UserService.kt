package diettracker
import java.io.File
import org.mindrot.jbcrypt.BCrypt
import diettracker.db.tables.Users
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

const val MAX_EMAIL_LENGTH = 128
const val MIN_PASSWORD_LENGTH = 8

fun isEmailValid(email: String): Boolean{
    val preexsistingUser = UserDatabase.isEmailDuplicate(email)

    return when {
        preexsistingUser -> false
        email.length < MAX_EMAIL_LENGTH -> true
        email.length > 1 -> true
        email.all { it.isLetterOrDigit() || it in setOf('@', '.', '_') } -> true
        else -> false
    }
}

fun hashPasswordIfValid(password: String): String? {
    return if (password.length >= MIN_PASSWORD_LENGTH && password.all { !it.isWhitespace() }) {
        BCrypt.hashpw(password, BCrypt.gensalt())
    } else {
        null
    }
}
