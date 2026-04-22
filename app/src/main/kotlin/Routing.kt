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
                "pages/landing_page/landing_page.peb",
                mapOf<String, Any>()
            )
            )
        }

        get("/client_dash") {
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

        get("/recipes") {
            val query = call.request.queryParameters["query"]?.trim() ?: ""
            val recipes = RecipeDatabaseQuery.searchRecipes(query)
            val email = call.sessions.get<UserSession>()?.email

            val favouriteIds = if (email != null) {
                val userId = UserDatabase.getUserIdByEmail(email)
                if (userId != null) RecipeDatabaseQuery.getFavourites(userId) else emptyList()
            } else emptyList()

            call.respondTemplate("pages/recipes_page/recipes.peb", mapOf(
                "recipes"  to recipes,
                "query" to query,
                "favouriteIds" to favouriteIds
            ))
        }

        post("/recipes/favourite/{recipeId}") {
            val recipeId = call.parameters["recipeId"]?.toIntOrNull()
            val email = call.sessions.get<UserSession>()?.email

            if (recipeId != null && email != null) {
                val userId = UserDatabase.getUserIdByEmail(email)
                if (userId != null) {
                    RecipeDatabaseQuery.addFavourite(userId, recipeId)
                }
            }

            call.respond(HttpStatusCode.OK)
        }

        post("/recipes/unfavourite/{recipeId}") {
            val recipeId = call.parameters["recipeId"]?.toIntOrNull()
            val email = call.sessions.get<UserSession>()?.email
            if (recipeId != null && email != null) {
                val userId = UserDatabase.getUserIdByEmail(email)
                if (userId != null) {
                    RecipeDatabaseQuery.removeFavourite(userId, recipeId)
                }
            }
            call.respond(HttpStatusCode.OK)
        }

        authenticate("group49-client_auth") {
            get("/") { call.DashboardPage() } //change get dashboard when made.
            get("/logout") { call.Logout() } 
            // get("/profile") { ... } 
        }
    }
}



