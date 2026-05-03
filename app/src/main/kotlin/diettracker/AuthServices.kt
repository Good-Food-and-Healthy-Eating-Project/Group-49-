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

/**
 * Displays the sign-up page.
 *
 * This is used by the sign-up GET route when the user opens the
 * page. It renders the signup.peb template with an empty model because no user
 * input or error message needs to be shown yet.
 */
suspend fun ApplicationCall.signUpPage() {
    respondTemplate("pages/auth/signup.peb", model = emptyMap())
}

/**
 * Handles creating a new user account from the sign-up form.
 *
 * This is used by the sign-up POST route when the user submits their email
 * and password. It gets the form credentials, tries to add the user to the
 * database, then handles each possible result.
 *
 * If the account is created successfully, a UserSession is stored so the user
 * is treated as logged in. The user is then sent to the quiz if their user ID
 * is found, or to the client dashboard as a fallback. If sign-up fails, the
 * sign-up page is shown again with an error message.
 */
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
            sessions.set(UserSession(email))
            val userId = getUserIdByEmail(email)
            if (userId != null) {
                respondRedirect("/quiz?userId=$userId")
            } else {
                respondRedirect("/client_dash")
            }
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

/**
 * Displays the login page.
 *
 * Renders the login.peb template and passes a message telling the user
 * to enter their email and password.
 */
suspend fun ApplicationCall.loginPage() {
    respondTemplate("pages/auth/login.peb", model = mapOf("message" to "Enter your credentials"))
}

/**
 * Handles logging in a user from the login form.
 *
 * This is used by the login POST route when the user submits their email
 * and password. It gets the form credentials and checks them against the
 * stored user details in UserDatabase using a helper function defined below
 *
 * If the database check fails because of an error, the login page is shown
 * with a general error message. If the credentials are correct, a UserSession
 * is stored so the user is treated as logged in, then they are redirected to
 * the client dashboard. If the credentials are incorrect, the login page is
 * shown with an invalid login message.
 */
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

/**
 * Displays the client dashboard for the logged-in user.
 *
 * This is used by the dashboard route after the user logs in or signs up.
 * It gets the user's email from the session, creates a simple username from
 * the part before the "@" symbol, finds the users ID, and loads their roles.
 *
 * The username, navbar setting, and user roles are then passed to the dashboard
 * template so the page can display the correct user information and role-based
 * options.
 * This is needed so the dashboard can show personalised information and
 * role-based options. Without this, the dashboard would not know which user is
 * logged in or what permissions they should see.
 */
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

/**
 * Logs the current user out of the application.
 *
 * It gets the current user's email for the server log, clears the UserSession, and
 * redirects the user back to the login page.
 */

suspend fun ApplicationCall.logout() {
    val email = sessions.get<UserSession>()?.email.toString()
    application.log.info("User $email logged out")
    sessions.clear<UserSession>()
    respondRedirect("/")
}

suspend fun ApplicationCall.getCredentials(): Pair<String, String> {
    val parameters = receiveParameters()
    val email = parameters.getOrFail("email")
    val password = parameters.getOrFail("password")
    return email to password
}

/**
 * Checks whether an email can be used for sign-up.
 *
 * This is used during account creation before saving a new user. It first
 * checks if the email already exists in the database, because duplicate emails
 * should not be allowed.
 *
 * This is needed so each account has a unique email address. Without this,
 * two users could register with the same email and the login system would not
 * know which account to use.
 *
 * @param email The email address entered by the user.
 * @return True if the email can be used, otherwise false.
 */
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

/**
 * Hashes a password if it meets the password rules.
 *
 * This is used during account creation before saving the password. It checks
 * that the password is at least the minimum length and does not contain spaces,
 * then hashes it using BCrypt.
 *
 * @param password The password entered by the user.
 * @return The hashed password, or null if the password is invalid.
 */
fun hashPasswordIfValid(password: String): String? {
    return if (password.length >= MIN_PASSWORD_LENGTH && password.all { !it.isWhitespace() }) {
        BCrypt.hashpw(password, BCrypt.gensalt())
    } else {
        null
    }
}
