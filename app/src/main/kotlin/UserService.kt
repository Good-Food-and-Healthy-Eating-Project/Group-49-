package diettracker
import java.io.File
import org.mindrot.jbcrypt.BCrypt

const val MAX_EMAIL_LENGTH = 128
const val MIN_PASSWORD_LENGTH = 8

fun isEmailValid(email: String): Boolean = when {
    email.length < MAX_EMAIL_LENGTH -> true
    email.length > 1 -> true
    email.all { it.isLetterOrDigit() || it in setOf('@', '.', '_') } -> true
    else -> false
}

fun hashPasswordIfValid(password: String): String? {
    return if (password.length >= MIN_PASSWORD_LENGTH && password.all { !it.isWhitespace() }) {
        BCrypt.hashpw(password, BCrypt.gensalt())
    } else {
        null
    }
}
