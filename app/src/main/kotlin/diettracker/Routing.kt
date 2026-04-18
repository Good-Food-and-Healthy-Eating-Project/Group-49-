package diettracker

import diettracker.db.tables.ClientProfessionalLink
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
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

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

        call.respond(
            PebbleContent(
                "pages/client_dash/client_dash.peb",
                mapOf("showNavbar" to true, "userRoles" to userRoles),
            ),
        )
    }

    configureFoodRoutes()

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

    post("/select-professional") {
        val session = call.sessions.get<UserSession>()
        val email = session?.email ?: return@post call.respondRedirect("/Login")

        val clientIdString = getUserIdByEmail(email)

        // Convert the client ID to an integer for database use.
        // If conversion fails, return an error to prevent invalid data being stored.
        val clientId = clientIdString?.toString()?.toIntOrNull()
            ?: return@post call.respondText(
                "Invalid client ID",
                status = HttpStatusCode.InternalServerError
            )

        val professionalId =
            call.receiveParameters()["professional_id"]?.toIntOrNull()
                ?: return@post call.respondText (
                    "Invalid professional",
                    status = HttpStatusCode.BadRequest,
                )

        linkClientToProfessional(clientId, professionalId)

        call.respondRedirect("/client_dash")
    }
    get("/professionals_dash") {
        val session = call.sessions.get<UserSession>()
        val email = session?.email ?: return@get call.respondRedirect("/Login")

        val professionalId = getUserIdByEmail(email)
            ?: return@get call.respondText("User not found")

        val clients = getClientsForProfessional(professionalId)

        call.respondTemplate(
            "pages/professionals/professionals_dash.peb",
            mapOf("clients" to clients)
        )
    }
}

fun linkClientToProfessional(clientId: Int, professionalId: Int) {
    transaction {
        ClientProfessionalLink.insertIgnore {
            it[ClientProfessionalLink.client_id] = clientId
            it[ClientProfessionalLink.professional_id] = professionalId
        }
        }
}


fun getClientsForProfessional(professionalId: Int): List<Int> {
    return transaction {
        ClientProfessionalLink
            .selectAll()
            .where { ClientProfessionalLink.professional_id eq professionalId }
            .map { it[ClientProfessionalLink.client_id] }
    }
}

fun Route.configureProtectedRoutes() {
    authenticate("group49-client_auth") {
        get("/") { call.dashboardPage() }

        get("/logout") { call.logout() }
    }
}
