package diettracker

import diettracker.routes.quizRoutes
import diettracker.routing.foodDiaryRoutes
import io.ktor.server.application.Application
import io.ktor.server.auth.authenticate
import io.ktor.server.http.content.staticResources
import io.ktor.server.pebble.PebbleContent
import io.ktor.server.pebble.respondTemplate
import io.ktor.server.response.respond
import io.ktor.server.response.respondRedirect
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.sessions.get
import io.ktor.server.sessions.sessions

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

        call.respond(
            PebbleContent(
                "pages/client_dash/client_dash.peb",
                mapOf(
                    "showNavbar" to true,
                    "userRoles" to userRoles,
                    "userId" to (userId as Any? ?: ""),
                    "dailyCalorieGoal" to (dailyCalorieGoal as Any? ?: ""),
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

        if (recipeQuery != null && recipeQuery.isNotBlank()) {
            val recipes = searchRecipes(recipeQuery)
            call.respondTemplate(
                "pages/client_dash/add_food.peb",
                mapOf("recipes" to recipes, "calories" to calories),
            )
        } else if (foodQuery != null && foodQuery.isNotBlank()) {
            val foods = searchFoods(foodQuery)
            call.respondTemplate(
                "pages/client_dash/add_food.peb",
                mapOf("foods" to foods, "calories" to calories),
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
        call.respondTemplate("pages/client_dash/add_food.peb", mapOf("recipes" to recipes, "calories" to calories))
    }

    get("/food_search") {
        val query = call.request.queryParameters["foodquery"] ?: ""
        val foods = searchFoods(query)
        val grams = call.request.queryParameters["grams"]?.toIntOrNull() ?: DEFAULT_GRAMS
        val calories = call.sessions.get<CaloriesSession>()?.calories ?: 0

        call.respondTemplate(
            "pages/client_dash/add_food.peb",
            mapOf(
                "foods" to foods,
                "calories" to calories,
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
    get("/professionals") {
        val email = call.sessions.get<UserSession>()?.email
        val userId = email?.let { getUserIdByEmail(it) }
        val userRoles = userId?.let { getUserRoles(it) } ?: emptyList()
        val professionals = getAllProfessionals()

        call.respondTemplate(
            "pages/professionals/professionals.peb",
            mapOf(
                "professionals" to professionals,
                "userRoles" to userRoles,
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

fun Route.configureProtectedRoutes() {
    authenticate("group49-client_auth") {
        get("/") { call.dashboardPage() }

        get("/logout") { call.logout() }
    }
}
