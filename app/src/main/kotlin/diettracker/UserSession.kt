package diettracker

import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.session
import io.ktor.server.response.respondRedirect
import kotlinx.serialization.Serializable

/**
 * Stores the logged-in user's email inside the Ktor session.
 *
 * After a user logs in successfully, it holds the email so that other routes
 * can check if the user is logged in and who they are.
 * This allows the app to maintain the user's logged-in state across multiple
 * requests, and lets other parts of the app access the user's email to retrieve
 * their data from the database or display it in the UI.
 *
 * @param email The email address of the logged-in user.
 */
@Serializable
data class UserSession(val email: String)

fun Application.configureAuthentication() {
    install(Authentication) {
        session<UserSession>("group49-client_auth") {
            validate { session ->
                val isValid = session.email.isNotEmpty()
                if (isValid) {
                    session
                } else {
                    null
                }
            }
            challenge { call.respondRedirect("/Login") }
        }
    }
}
