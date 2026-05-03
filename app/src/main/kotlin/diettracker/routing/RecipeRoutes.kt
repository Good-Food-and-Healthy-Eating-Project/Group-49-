package diettracker.routing

import diettracker.RecipeDatabaseQuery
import diettracker.UserSession
import diettracker.getUserIdByEmail
import io.ktor.http.HttpStatusCode
import io.ktor.server.pebble.respondTemplate
import io.ktor.server.request.receiveParameters
import io.ktor.server.response.respond
import io.ktor.server.response.respondRedirect
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.sessions.get
import io.ktor.server.sessions.sessions

private const val MAX_REVIEW_RATING = 5

@Suppress("LongMethod", "CyclomaticComplexMethod")
fun Route.configureRecipeRoutes() {
    get("/recipes") {
        val query = call.request.queryParameters["query"]?.trim() ?: ""
        val category = call.request.queryParameters["category"]?.trim() ?: ""
        val ingredient = call.request.queryParameters["ingredient"]?.trim() ?: ""
        val email = call.sessions.get<UserSession>()?.email

        val favouriteIds =
            if (email != null) {
                val userId = getUserIdByEmail(email)
                if (userId != null) RecipeDatabaseQuery.getFavourites(userId) else emptyList()
            } else {
                emptyList()
            }

        val recipes =
            if (ingredient.isNotBlank()) {
                RecipeDatabaseQuery.searchByIngredient(ingredient)
            } else {
                RecipeDatabaseQuery.searchRecipes(query, favouriteIds, category)
            }

        val favouriteRecipes =
            if (favouriteIds.isNotEmpty()) {
                RecipeDatabaseQuery.getFavouriteRecipes(favouriteIds)
            } else {
                emptyList()
            }

        val categories = RecipeDatabaseQuery.getCategories()

        call.respondTemplate(
            "pages/recipes_page/recipes.peb",
            mapOf(
                "recipes" to recipes,
                "query" to query,
                "favouriteRecipes" to favouriteRecipes,
                "category" to category,
                "categories" to categories,
                "ingredient" to ingredient,
            ),
        )
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
        call.respondTemplate(
            "pages/recipes_page/recipe_detail.peb",
            mapOf(
                "recipe" to recipe,
                "reviews" to reviews,
                "averageRating" to (averageRating ?: 0.0),
            ),
        )
    }

    post("/recipes/{id}/review") {
        val recipeId = call.parameters["id"]?.toIntOrNull()
        val email = call.sessions.get<UserSession>()?.email
        if (recipeId != null && email != null) {
            val userId = getUserIdByEmail(email)
            if (userId != null) {
                val parameters = call.receiveParameters()
                val rating = parameters["rating"]?.toIntOrNull()
                val comment = parameters["comment"]?.trim() ?: ""
                if (rating != null && rating in 1..MAX_REVIEW_RATING && comment.isNotBlank()) {
                    RecipeDatabaseQuery.addReview(userId, recipeId, rating, comment)
                }
            }
        }
        call.respondRedirect("/recipes/$recipeId")
    }

    post("/recipes/favourite/{recipeId}") {
        val recipeId = call.parameters["recipeId"]?.toIntOrNull()
        val email = call.sessions.get<UserSession>()?.email
        if (recipeId != null && email != null) {
            val userId = getUserIdByEmail(email)
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
            val userId = getUserIdByEmail(email)
            if (userId != null) {
                RecipeDatabaseQuery.removeFavourite(userId, recipeId)
            }
        }
        call.respond(HttpStatusCode.OK)
    }
}
