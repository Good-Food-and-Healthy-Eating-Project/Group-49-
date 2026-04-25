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
            val category = call.request.queryParameters["category"]?.trim() ?: ""
            val ingredient = call.request.queryParameters["ingredient"]?.trim() ?: ""
            val email = call.sessions.get<UserSession>()?.email

            val favouriteIds = if (email != null) {
                val userId = UserDatabase.getUserIdByEmail(email)
                if (userId != null) RecipeDatabaseQuery.getFavourites(userId) else emptyList()
            } else emptyList()

            val recipes = if (ingredient.isNotBlank()) {
                RecipeDatabaseQuery.searchByIngredient(ingredient)
            } else {
                RecipeDatabaseQuery.searchRecipes(query, favouriteIds, category)
            }

            val favouriteRecipes = if (favouriteIds.isNotEmpty()) {
                RecipeDatabaseQuery.getFavouriteRecipes(favouriteIds)
            } else emptyList()

            val categories = RecipeDatabaseQuery.getCategories()

            println("Recipes with favourited status: ${recipes.filter { it.isFavourited }.map { it.name }}")
            call.respondTemplate("pages/recipes_page/recipes.peb", mapOf(
                "recipes"  to recipes,
                "query" to query,
                "favouriteRecipes" to favouriteRecipes,
                "category" to category,
                "categories" to categories,
                "ingredient" to ingredient,
            ))
        }

        get("/recipes/{id}") {
            val recipeId = call.parameters["id"]?.toIntOrNull()
            if (recipeId == null) {
                call.respond(HttpStatusCode.BadRequest)
                return@get
            }
            val recipe = RecipeDatabaseQuery.getRecipeById(recipeId)
            if (recipe == null) {
                call.respond(HttpStatusCode.NotFound)
                return@get
            }
            val reviews = RecipeDatabaseQuery.getReviewsForRecipe(recipeId)
            val averageRating = RecipeDatabaseQuery.getAverageRating(recipeId)

            call.respondTemplate("pages/recipes_page/recipe_detail.peb", mapOf(
                "recipe" to recipe,
                "reviews" to reviews,
                "averageRating" to (averageRating ?: 0.0)
            ))
        }

        post("/recipes/{id}/review") {
            val recipeId = call.parameters["id"]?.toIntOrNull()
            val email = call.sessions.get<UserSession>()?.email

            if (recipeId != null && email != null) {
                val userId = UserDatabase.getUserIdByEmail(email)

                if (userId != null) {
                    val parameters = call.receiveParameters()
                    val rating = parameters["rating"]?.toIntOrNull()
                    val comment = parameters["comment"]?.trim() ?: ""

                    if (rating != null && rating in 1..5 && comment.isNotBlank()) {
                        RecipeDatabaseQuery.addReview(userId, recipeId, rating, comment)
                    }
                }
            }

            call.respondRedirect("/recipes/$recipeId")
        }

        post("/recipes/favourite/{recipeId}") {
            val recipeId = call.parameters["recipeId"]?.toIntOrNull()
            val email = call.sessions.get<UserSession>()?.email

            println("[/] Favourite requested recipeId: $recipeId, email: $email")

            if (recipeId != null && email != null) {
                val userId = UserDatabase.getUserIdByEmail(email)

                println("[/] Found userId: $userId")

                if (userId != null) {
                    RecipeDatabaseQuery.addFavourite(userId, recipeId)

                    println("[/] Favourite added successfully")
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



