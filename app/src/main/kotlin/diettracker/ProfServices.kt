package diettracker

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.pebble.respondTemplate
import io.ktor.server.response.respondRedirect
import io.ktor.server.sessions.sessions
import io.ktor.server.sessions.set

suspend fun ApplicationCall.profSignUpPage() {
    respondTemplate("pages/professionals/profsignup.peb", model = emptyMap())
}

suspend fun ApplicationCall.signUpProfessional() {
    val credentials = getCredentials()
    val email = credentials.first
    val password = credentials.second

    val result =
        runCatching {
            ProfDatabase.addProfessional(email, password)
        }

    when {
        result.isFailure -> {
            println("Sign-up error: ${result.exceptionOrNull()}")
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
    val result =
        runCatching {
            UserDatabase.checkCreds(email, password)
        }
    when {
        result.isFailure -> {
            respondTemplate(
                "pages/professionals/proflogin.peb",
                model = mapOf("error" to "Something went wrong, please try again"),
            )
        }
        result.getOrDefault(false) -> {
            val userId = getUserIdByEmail(email)
            val roles = getUserRoles(userId ?: -1)
            if (roles.contains("professional")) {
                sessions.set(UserSession(email))
                respondRedirect("/professionals_dash")
            } else {
                respondTemplate(
                    "pages/professionals/proflogin.peb",
                    model = mapOf("error" to "You are not registered as a professional"),
                )
            }
        }
        else -> {
            respondTemplate(
                "pages/professionals/proflogin.peb",
                model = mapOf("error" to "Invalid email or password"),
            )
        }
    }
}
