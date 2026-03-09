import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.auth.*
import io.ktor.server.pebble.respondTemplate
import io.ktor.server.sessions.*
import io.ktor.server.request.*
import io.ktor.server.util.getOrFail
import io.ktor.http.*

fun Application.configureRouting() {
    routing {
        get("/") {
            call.respondText("Good Food & Healthy Eating is running")
        }

        get("/health") {
            call.respondText("OK")
        }

        get("/Sign-Up") { 
            call.SignUpPage() 
        }

        post("/Sign-Up") { 
            call.SignUpUser() 
        }

        get("/Login") { 
            call.LoginPage() 
        }
        
        post("/Login") { 
            call.LoginUser() 
        }

        authenticate("group49-client_auth") {
            get("/") { call.DashboardPage() } //change get dashboard when made.
            get("/logout") { call.Logout() } 
            // get("/profile") { ... } 
        }
    }
}

suspend fun ApplicationCall.SignUpPage() {
    respondTemplate("auth/signup.peb", model = emptyMap())
}

suspend fun ApplicationCall.SignUpUser() {
    respond(HttpStatusCode.NotImplemented, "SignUpUser not implemented yet")
}

suspend fun ApplicationCall.LoginPage() {
    respondTemplate("auth/login.peb", model = mapOf("message" to "Enter your credentials"))
}

suspend fun ApplicationCall.LoginUser() {
    respond(HttpStatusCode.NotImplemented, "LoginUser not implemented yet")
}

suspend fun ApplicationCall.DashboardPage() {
    val username = sessions.get<UserSession>()?.username ?: ""
    respondTemplate("client_dash/client_dash.peb", mapOf("username" to username))
}

suspend fun ApplicationCall.Logout() {
    val username = sessions.get<UserSession>()?.username.toString()
    application.log.info("User $username logged out")
    sessions.clear<UserSession>()
    respondRedirect("/landing_page/landing_page.peb")
}



private suspend fun ApplicationCall.getCredentials(): UserPasswordCredential {
    val parameters = receiveParameters()
    val username = parameters.getOrFail("username")
    val password = parameters.getOrFail("password")
    return UserPasswordCredential(username, password)
}

/* 
private fun findOrCreateMemberUserId(username: String): Int? {
    // TODO: Implement database integration here
    // Example:
    // return transaction {
    //     val user = Users.find { UsersTable.name eq username }.firstOrNull()
    //         ?: Users.new {
    //             name = username
    //             email = "${username}@example.com"
    //         }
    //     user.id.value
    // }
    return null
}*/