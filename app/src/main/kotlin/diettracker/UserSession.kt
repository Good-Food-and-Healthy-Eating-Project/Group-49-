package diettracker

import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.session
import io.ktor.server.response.respondRedirect
import kotlinx.serialization.Serializable

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
            challenge { call.respondRedirect("/login") }
        }
    }
}
