import io.ktor.server.application.*
import io.ktor.server.pebble.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Application.configureRouting() {
    routing {
        get("/") {
            call.respondRedirect("/recipes")
        }

        get("/health") {
            call.respondText("OK")
        }

        get("/recipes") { 
            call.respond(
                PebbleContent(
                    "pages/recipes/recipes.peb",
                    mapOf("pageTitle" to "Good Food | Recipes")
                )
            )
        }
    }
}