package diettracker

import diettracker.db.repositories.getClientsForProfessional
import diettracker.db.repositories.getUserIdByEmail
import diettracker.db.repositories.getUserRoles
import diettracker.db.tables.Clients
import diettracker.db.tables.Users
import diettracker.routing.configureClientDashRoute
import diettracker.routing.configureClientProfessionalRoutes
import diettracker.routing.configureFoodRoutes
import diettracker.routing.configureMessageRoutes
import diettracker.routing.configureRecipeRoutes
import diettracker.routing.configureStatic
import diettracker.routing.foodDiaryRoutes
import diettracker.routing.hasRole
import diettracker.routing.professionalProfileRoutes
import diettracker.routing.profileRoutes
import diettracker.routing.quizRoutes
import diettracker.services.ClientDietTrend
import diettracker.services.UserSession
import diettracker.services.buildNavbarContext
import diettracker.services.dashboardPage
import diettracker.services.handleViewClientDetails
import diettracker.services.loginPage
import diettracker.services.loginProfessional
import diettracker.services.loginUser
import diettracker.services.logout
import diettracker.services.profLoginPage
import diettracker.services.profQuizPage
import diettracker.services.profSignUpPage
import diettracker.services.signUpPage
import diettracker.services.signUpProfessional
import diettracker.services.signUpUser
import diettracker.services.submitProfQuiz
import io.ktor.server.application.Application
import io.ktor.server.auth.authenticate
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
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.time.LocalDate

/**
 * Entry point for all application routing
 *
 * Registers all route groups so they are available when the app starts
 **/
fun Application.configureRouting() {
    routing {
        configureStatic()
        configurePublicRoutes()
        configureClientRoutes()
        configureProfessionalRoutes()
        configureAuthRoutes()
        configureProtectedRoutes()
    }
}

/**
 * Public routes accessible to all users - no login required
 *
 * Only includes the landing page and health check
 **/
fun Route.configurePublicRoutes() {
    // Landing page shown to unauthenticated users
    get("/") {
        call.respond(
            PebbleContent(
                "pages/landing_page/landing_page.peb",
                mapOf(),
            ),
        )
    }

    // Health check endpoint used to verify the server is running
    get("/health") {
        call.respondText("OK")
    }
}

/**
 * Client routes that require a logged-in session
 *
 * Each route redirects to /Login if no session is found
 **/
fun Route.configureClientRoutes() {
    configureClientDashRoute()
    configureFoodRoutes()
    configureMessageRoutes()
    foodDiaryRoutes()
    profileRoutes()

    get("/diary") {
        if (!call.hasRole("client")) return@get call.respondRedirect("/Login")
        val email = call.sessions.get<UserSession>()?.email
        val userId = email?.let(::getUserIdByEmail)
        call.respond(PebbleContent("pages/client_dash/food_diary.peb", buildNavbarContext(userId)))
    }
}

/**
 * Routing for authentication routes - login, signup and quiz
 **/
fun Route.configureAuthRoutes() {
    get("/Sign-Up") { call.signUpPage() }
    post("/Sign-Up") { call.signUpUser() }

    get("/Login") { call.loginPage() }
    post("/Login") { call.loginUser() }

    get("/quiz") {
        val userId = call.request.queryParameters["userId"]
        // Ensures userId exists before getting to quiz page
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
    // For additional quiz-related routes
    quizRoutes()
}

/**
 * Groups all professional-related routes together
 *
 * Includes client-professional linking, professional account management
 * and viewing client details
 **/
fun Route.configureProfessionalRoutes() {
    configureClientProfessionalRoutes()
    configureProfessionalAccountRoutes()
    configureViewClientDetailsRoutes()
}

/**
 * Routes for the professional's own account
 *
 * Handles professional dashboard, sign up, login and quiz
 **/
private fun Route.configureProfessionalAccountRoutes() {
    get("/professionals_dash") {
        // Redirect to login if not authenticated
        val session = call.sessions.get<UserSession>()
        val email = session?.email ?: return@get call.respondRedirect("/Login")
        val professionalId =
            getUserIdByEmail(email)
                ?: return@get call.respondText("User not found")
        if (!call.hasRole("professional")) return@get call.respondRedirect("/Login")
        val userRoles = getUserRoles(professionalId)
        // Get all clients linked to this professional for display on the dashboard
        val clients = getClientsForProfessional(professionalId)
        val yesterday = LocalDate.now().minusDays(1)
        val clientYesterdayStatus =
            clients.associate { client ->
                val trend = ClientDietTrend.getDietTrend(client.id).find { it.date == yesterday }
                client.id to
                    mapOf(
                        "wasOnTrackYesterday" to (trend?.colourClass == "green"),
                        "didNotLogYesterday" to (trend == null || trend.colourClass == "empty-day"),
                    )
            }
        call.respondTemplate(
            "pages/professionals/professionals_dash.peb",
            buildNavbarContext(professionalId, userRoles) +
                mapOf(
                    "clients" to clients,
                    "clientYesterdayStatus" to clientYesterdayStatus,
                ),
        )
    }

    get("/Professional-Sign-Up") { call.profSignUpPage() }
    post("/Professional-Sign-Up") { call.signUpProfessional() }

    get("/professional-quiz") {
        val userId = call.request.queryParameters["userId"]
        // Redirect back to sign up if no userId in query params
        if (userId == null) {
            call.respondRedirect("/Professional-Sign-Up")
            return@get
        }
        call.profQuizPage(userId)
    }
    post("/professional-quiz") { call.submitProfQuiz() }

    get("/Professional-Login") { call.profLoginPage() }
    post("/Professional-Login") { call.loginProfessional() }

    professionalProfileRoutes()
}

fun fetchClientData(clientId: Int): Map<String, Any?>? =
    transaction {
        (Clients innerJoin Users)
            .selectAll()
            .where { Clients.client_id eq clientId }
            .map {
                mapOf(
                    "clientId" to it[Clients.client_id],
                    "firstName" to it[Users.first_name],
                    "lastName" to it[Users.second_name],
                    "email" to it[Users.email],
                    "goal" to it[Clients.goal],
                    "calorieGoal" to it[Clients.daily_calorie_goal],
                    "age" to it[Clients.age],
                    "gender" to it[Clients.gender],
                )
            }.singleOrNull()
    }

/**
 * Route for a professional to view a specific client's diet details
 *
 * Shows client info, monthly diet trends and how many days they were on track
 * Shows monthly trend using calendar from client dashboard so professional can see if client is on track
 **/
fun Route.configureViewClientDetailsRoutes() {
    get("/professional/client/{clientId}") { call.handleViewClientDetails() }
}

fun Route.configureProtectedRoutes() {
    authenticate("group49-client_auth") {
        get("/") { call.dashboardPage() }
        get("/logout") { call.logout() }
        configureRecipeRoutes()
    }
}
