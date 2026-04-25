package diettracker

import diettracker.db.tables.Clients
import diettracker.routes.quizRoutes
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

private const val DEFAULT_GRAMS = 100

fun Application.configureRouting() {
    routing {
        configureStatic()
        configurePublicRoutes()
        configureProfessionalRoutes()
        configureAuthRoutes()
        configureProtectedRoutes()
    }
}

fun Route.configureStatic() {
    staticResources("/static", "static")
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

    get("/client_dash") {
        val email = call.sessions.get<UserSession>()?.email
        val userId = email?.let { getUserIdByEmail(it) }
        val userRoles = userId?.let { getUserRoles(it) } ?: emptyList()
        val dailyCalorieGoal = userId?.let { getClientCalorieGoal(it) }

        val trends = userId?.let { ClientDietTrend.getDietTrend(it) } ?: emptyList<DailyDietTrend>()
        val today = LocalDate.now()
        val currentYear = today.year
        val currentMonth = today.month
        val daysInMonth = today.lengthOfMonth()
        val firstDay = today.withDayOfMonth(1)
        val leadingEmptyDays = firstDay.dayOfWeek.value - 1

        call.respond(
            PebbleContent(
                "pages/client_dash/client_dash.peb",
                mapOf(
                    "showNavbar" to true,
                    "userRoles" to userRoles,
                    "isProfessional" to userRoles.contains("professional"),
                    "userId" to (userId as Any? ?: ""),
                    "dailyCalorieGoal" to (dailyCalorieGoal as Any? ?: ""),
                    "trends" to trends,
                    "currentYear" to currentYear,
                    "currentMonth" to currentMonth,
                    "daysInMonth" to daysInMonth,
                    "leadingEmptyDays" to leadingEmptyDays,
                ),
            ),
        )
    }

    configureFoodRoutes()
    foodDiaryRoutes()

    authenticate("group49-client_auth") {
        get("/") { call.dashboardPage() }
        get("/logout") { call.logout() }
    }

    get("/diary") {
        call.respond(PebbleContent("pages/client_dash/food_diary.peb", mapOf("showNavbar" to true)))
    }

    get("/recipes") {
        val recipes = getAllRecipes()
        call.respond(PebbleContent("pages/recipes/recipes.peb", mapOf("showNavbar" to true, "recipes" to recipes)))
    }

    get("/health") {
        call.respondText("OK")
    }
}

fun Route.configureFoodRoutes() {
    get("/food_log") {
        val recipeQuery = call.request.queryParameters["query"]
        val foodQuery = call.request.queryParameters["foodquery"]
        val calories = call.sessions.get<CaloriesSession>()?.calories ?: 0
        val protein = call.sessions.get<CaloriesSession>()?.protein ?: 0
        val fat = call.sessions.get<CaloriesSession>()?.fat ?: 0
        val carbs = call.sessions.get<CaloriesSession>()?.carbs ?: 0

        if (recipeQuery != null && recipeQuery.isNotBlank()) {
            val recipes = searchRecipes(recipeQuery)
            call.respondTemplate(
                "pages/client_dash/add_food.peb",
                mapOf("recipes" to recipes, "calories" to calories, "protein" to protein, "fat" to fat, "carbs" to carbs),
            )
        } else if (foodQuery != null && foodQuery.isNotBlank()) {
            val foods = searchFoods(foodQuery)
            call.respondTemplate(
                "pages/client_dash/add_food.peb",
                mapOf("foods" to foods, "calories" to calories, "protein" to protein, "fat" to fat, "carbs" to carbs),
            )
        } else {
            call.foodLogPage()
        }
    }

    post("/food_log_recipe") {
        call.foodLogRecipe()
    }

    post("/food_log_custom") {
        call.foodLogCustom()
    }

    post("/food_log_reset") {
        call.foodLogReset()
    }

    get("/recipe_search") {
        val query = call.request.queryParameters["query"] ?: ""
        val recipes = searchRecipes(query)
        val calories = call.sessions.get<CaloriesSession>()?.calories ?: 0
        val protein = call.sessions.get<CaloriesSession>()?.protein ?: 0
        val fat = call.sessions.get<CaloriesSession>()?.fat ?: 0
        val carbs = call.sessions.get<CaloriesSession>()?.carbs ?: 0
        call.respondTemplate("pages/client_dash/add_food.peb", mapOf("recipes" to recipes, "calories" to calories, "protein" to protein, "fat" to fat, "carbs" to carbs))
    }

    get("/food_search") {
        val query = call.request.queryParameters["foodquery"] ?: ""
        val foods = searchFoods(query)
        val grams = call.request.queryParameters["grams"]?.toIntOrNull() ?: DEFAULT_GRAMS
        val calories = call.sessions.get<CaloriesSession>()?.calories ?: 0
        val protein = call.sessions.get<CaloriesSession>()?.protein ?: 0
        val fat = call.sessions.get<CaloriesSession>()?.fat ?: 0
        val carbs = call.sessions.get<CaloriesSession>()?.carbs ?: 0

        call.respondTemplate(
            "pages/client_dash/add_food.peb",
            mapOf(
                "foods" to foods,
                "calories" to calories,
                "protein" to protein,
                "fat" to fat,
                "carbs" to carbs,
                "grams" to grams,
            ),
        )
    }
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
                mapOf(
                    "userId" to userId as Any,
                ),
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
        val email = call.sessions.get<UserSession>()?.email
        val userId = email?.let { getUserIdByEmail(it) }
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

        // Handling error case
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
                    }
                    .singleOrNull()
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
