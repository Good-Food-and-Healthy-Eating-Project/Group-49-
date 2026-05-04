package diettracker

import diettracker.db.tables.Clients
import diettracker.db.tables.Users
import diettracker.routes.quizRoutes
import diettracker.routing.configureClientDashRoute
import diettracker.routing.configureClientProfessionalRoutes
import diettracker.routing.configureFoodRoutes
import diettracker.routing.configureMessageRoutes
import diettracker.routing.configureRecipeRoutes
import diettracker.routing.foodDiaryRoutes
import diettracker.routing.professionalProfileRoutes
import diettracker.routing.profileRoutes
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
import kotlin.math.abs

private const val ON_TRACK_TOLERANCE = 100
private const val MIN_YEAR = 1900
private const val MAX_YEAR = 2100
private const val MIN_MONTH = 1
private const val MAX_MONTH = 12

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
 * Only includes the landing page, recipes and health check
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

    // Recipes are publicly browsable without an account
    configureRecipeRoutes()

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
        val userRoles = getUserRoles(professionalId)
        // Get all clients linked to this professional for display on the dashboard
        val clients = getClientsForProfessional(professionalId)
        call.respondTemplate(
            "pages/professionals/professionals_dash.peb",
            buildNavbarContext(professionalId, userRoles) +
                mapOf(
                    "clients" to clients,
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
    get("/professional/client/{clientId}") {
        // Redirect to login if not authenticated
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

        // Fetch the client's basic info from the database
        val clientData = fetchClientData(clientId)

        val today = LocalDate.now()
        // Default to current month if no query params provided
        val selectedYear = call.request.queryParameters["year"]?.toIntOrNull() ?: today.year
        val selectedMonth = call.request.queryParameters["month"]?.toIntOrNull() ?: today.monthValue
        // Validate the date range to prevent invalid dates being used
        val selectedDate =
            if (selectedMonth in MIN_MONTH..MAX_MONTH && selectedYear in MIN_YEAR..MAX_YEAR) {
                LocalDate.of(selectedYear, selectedMonth, 1)
            } else {
                today.withDayOfMonth(1)
            }

        val currentYear = selectedDate.year
        val currentMonth = selectedDate.month
        val previousMonthDate = selectedDate.minusMonths(1)
        val nextMonthDate = selectedDate.plusMonths(1)
        val daysInMonth = selectedDate.lengthOfMonth()
        // Used to offset the calendar grid so days align to the correct weekday
        val leadingEmptyDays = selectedDate.withDayOfMonth(1).dayOfWeek.value - 1

        val allTrends = ClientDietTrend.getDietTrend(clientId)
        // Filter trends to only show the selected month
        val trends = allTrends.filter { it.date.year == currentYear && it.date.month == currentMonth }

        val todayCalories = allTrends.find { it.date == today }?.totalCalorie?.toInt() ?: 0
        // Count days where calorie intake was within the tolerance of the target
        val onTrackDays = trends.count { abs(it.totalCalorie - it.targetCalorie) <= ON_TRACK_TOLERANCE }

        call.respondTemplate(
            "pages/professionals/view_client_details.peb",
            buildNavbarContext(professionalId, userRoles) +
                mapOf(
                    "clients" to clients,
                    "client" to (clientData ?: emptyMap<String, Any?>()),
                    "trends" to trends,
                    "currentYear" to currentYear,
                    "currentMonth" to currentMonth,
                    "daysInMonth" to daysInMonth,
                    "leadingEmptyDays" to leadingEmptyDays,
                    "previousYear" to previousMonthDate.year,
                    "previousMonth" to previousMonthDate.monthValue,
                    "nextYear" to nextMonthDate.year,
                    "nextMonth" to nextMonthDate.monthValue,
                    "todayCalories" to todayCalories,
                    "onTrackDays" to onTrackDays,
                    "totalTrackedDays" to trends.size,
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
