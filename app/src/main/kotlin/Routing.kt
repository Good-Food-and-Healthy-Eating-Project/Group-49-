package diettracker

import diettracker.db.tables.Users
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.auth.*
import io.ktor.server.pebble.respondTemplate
import io.ktor.server.sessions.*
import io.ktor.server.request.*
import io.ktor.server.util.getOrFail
import io.ktor.server.pebble.PebbleContent
import io.ktor.http.*
import io.ktor.server.http.content.resources
import io.ktor.server.http.content.static

fun Application.configureRouting() {
    routing {
        static("/static") {
            resources("static")
        }
        get("/") {
            call.respond(
                PebbleContent(
                "pages/client_dash/client_dash.peb",
                mapOf<String, Any>()
            )
            )
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



