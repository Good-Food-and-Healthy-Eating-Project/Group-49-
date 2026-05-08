package diettracker

import diettracker.db.DatabaseFactory
import diettracker.db.repositories.backfillClientRoles
import diettracker.models.CurrentMealSession
import diettracker.services.CaloriesSession
import diettracker.services.UserSession
import diettracker.services.configureAuthentication
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.netty.EngineMain
import io.ktor.server.sessions.Sessions
import io.ktor.server.sessions.cookie

fun main(args: Array<String>) {
    EngineMain.main(args)
}

/**
 * Main setup function for the Ktor application.
 *
 * Sets up the database connection, session cookies, authentication,
 * routing, and Pebble templates.
 *
 * The database is only initialised when testing is false, so tests can run
 * without starting the normal database setup. The session cookies store the
 * logged-in user, the current nutrition totals, and the current meal foods etc
 * while the user is using the site.
 *
 * Assigning roles logic was added after some users had already been added to the database
 * So backfillClientRoles help to assign roles who have not been filled with client so these users
 * can pass the role based authentication
 * There is no risk with assigning professionals to client role because
 * professionals have always been assigned to professional role
 * @param testing Whether the application is running in test mode.
 */

fun Application.module(testing: Boolean = false) {
    if (!testing) {
        DatabaseFactory.init()
        backfillClientRoles()
    }
    install(Sessions) {
        cookie<UserSession>("Session")
        cookie<CaloriesSession>("CaloriesSession")
        cookie<CurrentMealSession>("CurrentMealSession")
    }
    configureAuthentication()
    configureRouting()
    configureTemplates()
}
