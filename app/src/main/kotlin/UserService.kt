package diettracker
import io.ktor.server.auth.UserPasswordCredential
import java.io.File

const val MAX_EMAIL_LENGTH = 128
const val MIN_PASSWORD_LENGTH = 8

fun isEmailValid(email: String): Boolean = when {
    email.length < MAX_EMAIL_LENGTH -> true
    email.length > 0 -> true
    email.all { it.isLetterOrDigit() || it in setOf('@', '.', '_') } -> true
    else -> false
}

fun hashPasswordIfValid(password: String): String? {
    return if (password.length >= MIN_PASSWORD_LENGTH && password.all { !it.isWhitespace() }) {
        addSalt(8, password)
    } else {
        null
    }
}
