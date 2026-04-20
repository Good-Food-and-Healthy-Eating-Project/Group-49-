package diettracker

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.log
import io.ktor.server.pebble.respondTemplate
import io.ktor.server.request.receiveParameters
import io.ktor.server.response.respondRedirect
import io.ktor.server.sessions.clear
import io.ktor.server.sessions.get
import io.ktor.server.sessions.sessions
import io.ktor.server.sessions.set
import io.ktor.server.util.getOrFail
import org.mindrot.jbcrypt.BCrypt

const val MAX_EMAIL_LENGTH = 128
const val MIN_PASSWORD_LENGTH = 8

suspend fun ApplicationCall.signUpPage() {
    respondTemplate("pages/auth/signup.peb", model = emptyMap())
}

suspend fun ApplicationCall.signUpUser() {
    val credentials = getCredentials()
    val email = credentials.first
    val password = credentials.second

    val result =
        runCatching {
            UserDatabase.addUser(email, password)
        }

    when {
        result.isFailure -> {
            response.status(HttpStatusCode.BadRequest)
            respondTemplate(
                "pages/auth/signup.peb",
                model = mapOf("error" to "Something went wrong, please try again"),
            )
        }

        result.getOrDefault(false) -> {
            respondTemplate(
                "pages/auth/signup_quiz.peb",
                model = mapOf("email" to email),
            )
        }

        else -> {
            response.status(HttpStatusCode.BadRequest)
            respondTemplate(
                "pages/auth/signup.peb",
                model = mapOf("error" to "Email already used or invalid input"),
            )
        }
    }
}

suspend fun ApplicationCall.loginPage() {
    respondTemplate("pages/auth/login.peb", model = mapOf("message" to "Enter your credentials"))
}

suspend fun ApplicationCall.loginUser() {
    val credentials = getCredentials()
    val email = credentials.first
    val password = credentials.second

    val result = runCatching { UserDatabase.checkCreds(email, password) }
    when {
        result.isFailure ->
            respondTemplate(
                "pages/auth/login.peb",
                model = mapOf("error" to "Something went wrong, please try again"),
            )

        result.getOrDefault(false) -> {
            sessions.set(UserSession(email))
            respondRedirect("/client_dash")
        }

        else -> respondTemplate("pages/auth/login.peb", model = mapOf("error" to "Invalid email or password"))
    }
}

suspend fun ApplicationCall.dashboardPage() {
    val email = sessions.get<UserSession>()?.email ?: ""
    val username = email.substringBefore("@")
    val userId = getUserIdByEmail(email)
    val userRoles = userId?.let { getUserRoles(it) } ?: emptyList()
    respondTemplate(
        "client_dash/client_dash.peb",
        mapOf("username" to username, "showNavbar" to true, "userRoles" to userRoles),
    )
}

suspend fun ApplicationCall.logout() {
    val email = sessions.get<UserSession>()?.email.toString()
    application.log.info("User $email logged out")
    sessions.clear<UserSession>()
    respondRedirect("/landing_page/landing_page.peb")
}

suspend fun ApplicationCall.getCredentials(): Pair<String, String> {
    val parameters = receiveParameters()
    val email = parameters.getOrFail("email")
    val password = parameters.getOrFail("password")
    return email to password
}

fun isEmailValid(email: String): Boolean {
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
