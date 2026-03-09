import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.Principal
import io.ktor.server.auth.session
import io.ktor.server.response.respondRedirect
import io.ktor.server.sessions.Sessions
import io.ktor.server.sessions.cookie

data class UserSession(val username: String, val user_id: Int, val email: String, val count: Int = 1) : Principal

fun Application.configureAuthentication() {
    
    install(Sessions) {
        cookie<UserSession>("Session")
    }

    install(Authentication) {

        session<UserSession>("group49-client_auth") {
        validate { session ->
            val isValid = session.username.isNotEmpty() && session.user_id > 0 &&session.email.isNotEmpty()
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