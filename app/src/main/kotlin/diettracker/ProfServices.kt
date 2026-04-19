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


suspend fun ApplicationCall.profSignUpPage() {
    respondTemplate("pages/professionals/profsignup.peb", model = emptyMap())
}

suspend fun ApplicationCall.signUpProfessional() {
    val credentials = getCredentials()
    val email = credentials.first
    val password = credentials.second

    val result =
        runCatching {
            // UserDatabase.addUser(email, password)
            true
        }

    when {
        result.isFailure -> {
            response.status(HttpStatusCode.BadRequest)
            respondTemplate(
                "pages/professionals/profsignup.peb",
                model = mapOf("error" to "Something went wrong, please try again"),
            )
        }

        result.getOrDefault(false) -> {
            respondTemplate(
                "pages/professionals/profsignup.peb",
                model = mapOf("success" to true),
            )
        }

        else -> {
            response.status(HttpStatusCode.BadRequest)
            respondTemplate(
                "pages/professionals/profsignup.peb",
                model = mapOf("error" to "Email already used or invalid input"),
            )
        }
    }
}

suspend fun ApplicationCall.profLoginPage() {
    respondTemplate("pages/professionals/proflogin.peb", model = emptyMap())
}

suspend fun ApplicationCall.loginProfessional() {
    val credentials = getCredentials()
    val email = credentials.first
    val password = credentials.second

    val result = runCatching { 
        // UserDatabase.checkCreds(email, password)
        true
    }
    when {
        result.isFailure ->
            respondTemplate(
                "pages/professionals/proflogin.peb",
                model = mapOf("error" to "Something went wrong, please try again"),
            )

        result.getOrDefault(false) -> {
            sessions.set(UserSession(email))
            respondRedirect("/prof_dashboard")
        }

        else -> respondTemplate("pages/professionals/proflogin.peb", model = mapOf("error" to "Invalid email or password"))
    }
}


