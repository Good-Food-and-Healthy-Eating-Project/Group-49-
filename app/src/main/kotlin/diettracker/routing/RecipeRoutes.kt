package diettracker.routing

import diettracker.RecipeDatabaseQuery
import diettracker.RecipeFavouriteQuery
import diettracker.RecipeReviewQuery
import diettracker.UserSession
import diettracker.buildNavbarContext
import diettracker.getUserIdByEmail
import diettracker.db.repositories.RecipeDatabaseQuery
import diettracker.db.repositories.getUserIdByEmail
import diettracker.services.UserSession
import diettracker.services.buildNavbarContext
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

/**
 * Groups all recipe related routes together
 *
 * Includes browsing, detail view, favouriting and reviews
 **/

fun Route.configureRecipeRoutes() {
    configureRecipeListRoutes()
    configureRecipeFavouriteRoutes()
    configureRecipeReviewRoutes()
}

/**
 * Groups recipe BROWSING routes together
 *
 * Includes main recipe search page, and individual recipe details
 **/
fun Route.configureRecipeListRoutes() {
    configureRecipeSearchRoutes()
    configureRecipeDetailRoutes()
}

/**
 * Route for main recipes page
 *
 * Supports searching by name, filtering by category, OR searching by ingredients
 * But not more than one simultaneously
 * Also loads a user's favourited recipes if they are logged in
 * defaults to a random selection of 9 recipes if no search or filter is used
 **/
fun Route.configureRecipeSearchRoutes() {
    get("/recipes") {
        val query = call.request.queryParameters["query"]?.trim() ?: ""
        val category = call.request.queryParameters["category"]?.trim() ?: ""
        val ingredient = call.request.queryParameters["ingredient"]?.trim() ?: ""
        val email = call.sessions.get<UserSession>()?.email
        val userId = email?.let(::getUserIdByEmail)

        val favouriteIds =
            if (userId != null) {
                RecipeFavouriteQuery.getFavourites(userId)
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
                RecipeFavouriteQuery.getFavouriteRecipes(favouriteIds)
            } else {
                emptyList()
            }

        val categories = RecipeDatabaseQuery.getCategories()

        call.respondTemplate(
            "pages/recipes_page/recipes.peb",
            buildNavbarContext(userId) +
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
}

/**
 * Route for viewing a single recipe's full details
 *
 * Shows ingredients, instructions, category, area, reviews and average rating
 * returns error 400 if recipe ID is invalid, 404 if the recipe is not found
 **/
fun Route.configureRecipeDetailRoutes() {
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

        val reviews = RecipeReviewQuery.getReviewsForRecipe(recipeId)
        val averageRating = RecipeReviewQuery.getAverageRating(recipeId)
        val email = call.sessions.get<UserSession>()?.email

        call.respondTemplate(
            "pages/recipes_page/recipe_detail.peb",
            buildNavbarContext(email?.let(::getUserIdByEmail)) +
                mapOf(
                    "recipe" to recipe,
                    "reviews" to reviews,
                    "averageRating" to (averageRating ?: 0.0),
                ),
        )
    }
}

/**
 * Route for adding and removing recipes from a user's favourites
 *
 * Both routes require the user ti be logged in via session
 * Returns 200 OK on success, silently does nothing if user is not logged in
 **/
fun Route.configureRecipeFavouriteRoutes() {
    post("/recipes/favourite/{recipeId}") {
        val recipeId = call.parameters["recipeId"]?.toIntOrNull()
        val email = call.sessions.get<UserSession>()?.email
        if (recipeId != null && email != null) {
            val userId = getUserIdByEmail(email)
            if (userId != null) {
                RecipeFavouriteQuery.addFavourite(userId, recipeId)
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
                RecipeFavouriteQuery.removeFavourite(userId, recipeId)
            }
        }
        call.respond(HttpStatusCode.OK)
    }
}

/**
 * Route for submitting a review on a recipe
 *
 * Requires the user to be logged in via session
 * Rating must be between 1 and 5, review must not be blank
 * Only allows one review per user per recipe
 * Users cannot edit or delete reviews
 * Redirects back to recipe detail page after submitting
 **/
fun Route.configureRecipeReviewRoutes() {
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
                    RecipeReviewQuery.addReview(userId, recipeId, rating, comment)
                }
            }
        }
        call.respondRedirect("/recipes/$recipeId")
    }
}
