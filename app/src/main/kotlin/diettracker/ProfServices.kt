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
    respondTemplate("pages/professionals/profsingup.peb", model = emptyMap())
}

suspend fun ApplicationCall.profLoginPage() {
    respondTemplate("pages/professionals/proflogin.peb", model = emptyMap())
}
