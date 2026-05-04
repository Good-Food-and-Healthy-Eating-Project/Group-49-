package diettracker.routing

import diettracker.UserSession
import diettracker.buildNavbarContext
import diettracker.db.tables.Professionals
import diettracker.db.tables.Users
import diettracker.getUserIdByEmail
import io.ktor.server.pebble.respondTemplate
import io.ktor.server.request.receiveParameters
import io.ktor.server.response.respondRedirect
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.sessions.get
import io.ktor.server.sessions.sessions
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.innerJoin
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update

fun Route.professionalProfileRoutes() {
    configureProfessionalProfilePageRoute()
    configureProfessionalProfileUpdateRoute()
}

/**
 * Handles requests to the profile page and loads the professional's profile data.
 *
 * It first checks if the professional is logged in using the session.
 * If no session or user ID found, professional is redirected to the login page.
 *
 * Checks Professionals table to retrieve info stored from sign up
 * Maps result to template / .peb page so can be accessed and displayed
 */
private fun Route.configureProfessionalProfilePageRoute() {
    get("/professional-profile") {
        val email = call.sessions.get<UserSession>()?.email
        if (email == null) {
            call.respondRedirect("/Professional-Login")
            return@get
        }

        val userId = getUserIdByEmail(email)
        if (userId == null) {
            call.respondRedirect("/Professional-Login")
            return@get
        }

        if (!call.hasRole("professional")) return@get call.respondRedirect("/Login")

        // Fetch professional and user details in a single joined query
        // Single used because only one record in the database is expected
        val userinfo =
            transaction {
                (Professionals innerJoin Users)
                    .selectAll()
                    .where { Professionals.professional_id eq userId }
                    .map { row ->
                        mapOf(
                            "professional_id" to row[Professionals.professional_id],
                            "first_name" to row[Users.first_name],
                            "second_name" to row[Users.second_name],
                            "job_title" to row[Professionals.job_title],
                            "organisation" to row[Professionals.organistation],
                            "bio" to row[Professionals.bio],
                        )
                    }.single()
            }

        call.respondTemplate(
            "pages/professionals/professional_profile.peb",
            buildNavbarContext(userId, listOf("professional")) +
                mapOf(
                    "userinfo" to userinfo,
                    "email" to email,
                ),
        )
    }
}

/**
 * Handles updates to the user's profile details.
 *
 * When update button is pushed, it ensures user is logged in then uses email to find matching ID
 * The route first checks the session to make sure the user is logged in, then
 * uses the email to find the matching user ID.
 *
 * Read entries from current form and handles the case if missing entry, keeps previous one
 *
 */
private fun Route.configureProfessionalProfileUpdateRoute() {
    post("/professional-profile-update") {
        val email = call.sessions.get<UserSession>()?.email
        if (email == null) {
            call.respondRedirect("/Professional-Login")
            return@post
        }

        val userId = getUserIdByEmail(email)
        if (userId == null) {
            call.respondRedirect("/Professional-Login")
            return@post
        }

        if (!call.hasRole("professional")) return@post call.respondRedirect("/Login")

        val params = call.receiveParameters()

        val newJobTitle = params["job_title"]?.takeIf { it.isNotBlank() }
        val newOrganisation = params["organisation"]?.takeIf { it.isNotBlank() }
        val newBio = params["bio"]?.takeIf { it.isNotBlank() }

        // Get current values to use if fields were left blank
        val currentProfessional =
            transaction {
                Professionals
                    .selectAll()
                    .where { Professionals.professional_id eq userId }
                    .singleOrNull()
            }

        if (currentProfessional == null) {
            call.respondRedirect("/professional-profile")
            return@post
        }

        val checkedJobTitle = newJobTitle ?: currentProfessional[Professionals.job_title]
        val checkedOrganisation = newOrganisation ?: currentProfessional[Professionals.organistation]
        val checkedBio = newBio ?: currentProfessional[Professionals.bio]

        transaction {
            Professionals.update({ Professionals.professional_id eq userId }) {
                it[Professionals.job_title] = checkedJobTitle
                it[Professionals.organistation] = checkedOrganisation
                it[Professionals.bio] = checkedBio
            }
        }

        call.respondRedirect("/professional-profile")
    }
}
