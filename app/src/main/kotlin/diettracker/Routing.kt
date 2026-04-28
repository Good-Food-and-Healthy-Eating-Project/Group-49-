package diettracker

import diettracker.db.tables.Clients
import diettracker.routes.quizRoutes
import diettracker.routing.configureFoodRoutes
import diettracker.routing.foodDiaryRoutes
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.auth.authenticate
import io.ktor.server.http.content.staticResources
import io.ktor.server.pebble.PebbleContent
import io.ktor.server.pebble.respondTemplate
import io.ktor.server.request.receiveParameters
import io.ktor.server.response.respond
import io.ktor.server.response.respondRedirect
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.sessions.get
import io.ktor.server.sessions.sessions
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.time.LocalDate

private const val MAX_REVIEW_RATING = 5

fun Application.configureRouting() {
    routing {
        staticResources("/static", "static")
        configurePublicRoutes()
        configureProfessionalRoutes()
        configureAuthRoutes()
        configureProtectedRoutes()
    }
}

fun Route.configurePublicRoutes() {
    get("/") {
        call.respond(
            PebbleContent(
                "pages/landing_page/landing_page.peb",
                mapOf(),
            ),
        )
    }

    configureClientDashRoute()
    configureFoodRoutes()
    foodDiaryRoutes()

    authenticate("group49-client_auth") {
        get("/") { call.dashboardPage() }
        get("/logout") { call.logout() }
    }

    get("/diary") {
        call.respond(PebbleContent("pages/client_dash/food_diary.peb", mapOf("showNavbar" to true)))
    }

    configureRecipeRoutes()

    get("/health") {
        call.respondText("OK")
    }
}

private fun Route.configureClientDashRoute() {
    get("/client_dash") {
        val email = call.sessions.get<UserSession>()?.email
        val userId = email?.let { getUserIdByEmail(it) }

        if (userId == null) {
            call.respondRedirect("/Login")
            return@get
        }

        call.respond(PebbleContent("pages/client_dash/client_dash.peb", buildClientDashModel(userId)))
    }
}

private fun buildClientDashModel(userId: Int): Map<String, Any> {
    val userRoles = getUserRoles(userId)
    val dailyCalorieGoal = getClientCalorieGoal(userId)
    val trends = ClientDietTrend.getDietTrend(userId)
    val today = LocalDate.now()
    val currentYear = today.year
    val currentMonth = today.month
    val daysInMonth = today.lengthOfMonth()
    val leadingEmptyDays = today.withDayOfMonth(1).dayOfWeek.value - 1

    val client =
        transaction {
            Clients
                .selectAll()
                .where { Clients.client_id eq userId }
                .single()
        }
    val calorieGoal = client[Clients.daily_calorie_goal]
    val goal = client[Clients.goal]
    val nutrition = getDailyNutritionSummary(userId, today)
    val status = if (calorieGoal != null && nutrition.totalCalories > calorieGoal) "Over target" else "On track"

    val totalCaloriesInt = nutrition.totalCalories.toInt()
    val totalMacros = nutrition.totalProtein + nutrition.totalFat + nutrition.totalCarbs
    val proteinPercent = if (totalMacros > 0) nutrition.totalProtein / totalMacros else 0.0
    val fatPercent = if (totalMacros > 0) nutrition.totalFat / totalMacros else 0.0
    val carbsPercent = if (totalMacros > 0) nutrition.totalCarbs / totalMacros else 0.0
    val guidanceMessages =
        buildGuidanceMessages(
            calorieGoal,
            totalCaloriesInt,
            proteinPercent,
            fatPercent,
            carbsPercent,
        )

    return mapOf(
        "showNavbar" to true,
        "userRoles" to userRoles,
        "isProfessional" to userRoles.contains("professional"),
        "userId" to (userId as Any? ?: ""),
        "dailyCalorieGoal" to (dailyCalorieGoal ?: ""),
        "trends" to trends,
        "currentYear" to currentYear,
        "currentMonth" to currentMonth,
        "daysInMonth" to daysInMonth,
        "leadingEmptyDays" to leadingEmptyDays,
        "totalCalories" to nutrition.totalCalories,
        "totalProtein" to nutrition.totalProtein,
        "totalCarbs" to nutrition.totalCarbs,
        "totalFat" to nutrition.totalFat,
        "calorieGoal" to (calorieGoal as Any? ?: ""),
        "goal" to (goal as Any? ?: ""),
        "status" to status,
        "messages" to guidanceMessages,
    )
}

fun Route.configureAuthRoutes() {
    get("/Sign-Up") { call.signUpPage() }
    post("/Sign-Up") { call.signUpUser() }

    get("/Login") { call.loginPage() }
    post("/Login") { call.loginUser() }

    get("/quiz") {
        val userId = call.request.queryParameters["userId"]
        if (userId == null) {
            call.respondRedirect("/Sign-Up")
            return@get
        }
        call.respond(
            PebbleContent(
                "pages/auth/signup_quiz.peb",
                mapOf("userId" to userId as Any),
            ),
        )
    }

    quizRoutes()
}

fun Route.configureProfessionalRoutes() {
    configureClientProfessionalRoutes()
    configureProfessionalAccountRoutes()
    configureViewClientDetailsRoutes()
}

private fun Route.configureClientProfessionalRoutes() {
    get("/professionals") {
        val email = call.sessions.get<UserSession>()?.email ?: return@get call.respondRedirect("/Login")
        val userId = email.let { getUserIdByEmail(it) }
        val userRoles = userId?.let { getUserRoles(it) } ?: emptyList()
        val professionals = getAllProfessionals()
        val hasCompletedQuiz = userId?.let { getClientCalorieGoal(it) } != null

        call.respondTemplate(
            "pages/professionals/professionals.peb",
            mapOf(
                "professionals" to professionals,
                "isProfessional" to userRoles.contains("professional"),
                "showNavbar" to true,
                "hasCompletedQuiz" to hasCompletedQuiz,
                "userId" to (userId ?: ""),
            ),
        )
    }

    post("/select-professional") {
        val session = call.sessions.get<UserSession>()
        val email = session?.email ?: return@post call.respondRedirect("/Login")

        val clientIdString = getUserIdByEmail(email)

        // Convert the client ID to an integer for database use.
        // If conversion fails, return an error to prevent invalid data being stored.
        val clientId =
            clientIdString?.toString()?.toIntOrNull()
                ?: return@post call.respondText(
                    "Invalid client ID",
                    status = HttpStatusCode.InternalServerError,
                )

        if (getClientCalorieGoal(clientId) == null) {
            return@post call.respondRedirect("/quiz?userId=$clientId")
        }

        val professionalId =
            call.receiveParameters()["professional_id"]?.toIntOrNull()
                ?: return@post call.respondText(
                    "Invalid professional",
                    status = HttpStatusCode.BadRequest,
                )

        linkClientToProfessional(clientId, professionalId)
        call.respondRedirect("/client_dash")
    }
}

private fun Route.configureProfessionalAccountRoutes() {
    get("/professionals_dash") {
        val session = call.sessions.get<UserSession>()
        val email = session?.email ?: return@get call.respondRedirect("/Login")
        val professionalId =
            getUserIdByEmail(email)
                ?: return@get call.respondText("User not found")
        val userRoles = getUserRoles(professionalId)
        val clients = getClientsForProfessional(professionalId)
        call.respondTemplate(
            "pages/professionals/professionals_dash.peb",
            mapOf(
                "showNavbar" to true,
                "isProfessional" to userRoles.contains("professional"),
                "clients" to clients,
            ),
        )
    }

    get("/Professional-Sign-Up") { call.profSignUpPage() }
    post("/Professional-Sign-Up") { call.signUpProfessional() }

    get("/professional-quiz") {
        val userId = call.request.queryParameters["userId"]
        if (userId == null) {
            call.respondRedirect("/Professional-Sign-Up")
            return@get
        }
        call.profQuizPage(userId)
    }
    post("/professional-quiz") { call.submitProfQuiz() }

    get("/Professional-Login") { call.profLoginPage() }
    post("/Professional-Login") { call.loginProfessional() }
}

fun Route.configureViewClientDetailsRoutes() {
    get("/professional/client/{clientId}") {
        val session = call.sessions.get<UserSession>()
        val email = session?.email ?: return@get call.respondRedirect("/Login")
        val professionalId = getUserIdByEmail(email) ?: return@get call.respondText("User not found")
        val userRoles = getUserRoles(professionalId)
        val clients = getClientsForProfessional(professionalId)
        val clientId = call.parameters["clientId"]?.toIntOrNull()

        if (clientId == null) {
            call.respondText("Invalid client ID")
            return@get
        }

        val clientData =
            transaction {
                Clients
                    .selectAll()
                    .where { Clients.client_id eq clientId }
                    .map {
                        mapOf(
                            "clientId" to it[Clients.client_id],
                            "goal" to it[Clients.goal],
                            "calorieGoal" to it[Clients.daily_calorie_goal],
                            "age" to it[Clients.age],
                            "gender" to it[Clients.gender],
                        )
                    }.singleOrNull()
            }

        call.respondTemplate(
            "pages/professionals/view_client_details.peb",
            mapOf(
                "showNavbar" to true,
                "isProfessional" to userRoles.contains("professional"),
                "clients" to clients,
                "client" to (clientData ?: emptyMap<String, Any?>()),
            ),
        )
    }
}

fun Route.configureProtectedRoutes() {
    authenticate("group49-client_auth") {
        get("/") { call.dashboardPage() }
        get("/logout") { call.logout() }
    }
}

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
