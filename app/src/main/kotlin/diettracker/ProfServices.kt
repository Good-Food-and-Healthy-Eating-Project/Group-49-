package diettracker

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.pebble.respondTemplate
import io.ktor.server.request.receiveParameters
import io.ktor.server.response.respondRedirect
import io.ktor.server.sessions.sessions
import io.ktor.server.sessions.set

/**
 * Just renders the professional sign-up page
 * No data needed so the model is empty
 */
suspend fun ApplicationCall.profSignUpPage() {
    respondTemplate("pages/professionals/profsignup.peb", model = emptyMap())
}

/**
 * Handles sign-up for professional users.
 *
 * This is used by the professional sign-up POST route when a professional
 * submits their email and password. It gets submitted credentials, attempts
 * to create a professional account using addProfessional() in ProfDatabase, then
 * finds the new user's ID and redirects them to the professional quiz page.
 *
 * This is needed so professional users can create an account before completing
 * their professional profile setup.
 */
suspend fun ApplicationCall.signUpProfessional() {
    val credentials = getCredentials()
    val email = credentials.first
    val password = credentials.second

    val result =
        runCatching {
            ProfDatabase.addProfessional(email, password)
        }

    when {
        result.isFailure -> {
            println("Sign-up error: ${result.exceptionOrNull()}")
            response.status(HttpStatusCode.BadRequest)
            respondTemplate(
                "pages/professionals/profsignup.peb",
                model = mapOf("error" to "Something went wrong, please try again"),
            )
        }

        result.getOrDefault(false) -> {
            val userId = getUserIdByEmail(email)
            if (userId != null) {
                respondRedirect("/professional-quiz?userId=$userId")
            } else {
                respondTemplate(
                    "pages/professionals/profsignup.peb",
                    model = mapOf("success" to true),
                )
            }
        }

        else -> {
            response.status(HttpStatusCode.BadRequest)
            respondTemplate(
                "pages/professionals/profsignup.peb",
                model = mapOf("error" to "Email already used or invalid input"),
            )
        }
    }
}

/**
 * Renders the professional profile quiz page
 * Passes the userId through so the form knows which professional to update when submitted
 * Empty strings are used as defaults so the template doesn't break if fields aren't pre-filled
 */
suspend fun ApplicationCall.profQuizPage(userId: String) {
    val model =
        mapOf(
            "userId" to userId,
            "firstName" to "",
            "lastName" to "",
            "jobTitle" to "",
            "organisation" to "",
            "bio" to "",
        )
    respondTemplate("pages/professionals/prof_quiz.peb", model = model)
}

/**
 * Handles submission of the professional profile quiz
 * Validates that all fields are filled in — professionals need a complete profile
 * so clients can see their details on the professionals page
 *
 * If anything is missing it re-renders the form with the values already entered
 * so the user doesn't have to retype everything
 * On success it updates the professional's profile in the database and sends them to login
 */
suspend fun ApplicationCall.submitProfQuiz() {
    val params = receiveParameters()
    val userId = params["userId"]?.toIntOrNull()

    if (userId == null) {
        respondRedirect("/Professional-Sign-Up")
        return
    }

    val firstName = params["firstName"]?.trim().orEmpty()
    val lastName = params["lastName"]?.trim().orEmpty()
    val jobTitle = params["jobTitle"]?.trim().orEmpty()
    val organisation = params["organisation"]?.trim().orEmpty()
    val bio = params["bio"]?.trim().orEmpty()

    val missing = listOf(firstName, lastName, jobTitle, organisation, bio).any { it.isEmpty() }
    if (missing) {
        response.status(HttpStatusCode.BadRequest)
        val errorModel =
            mapOf(
                "userId" to userId.toString(),
                "error" to "All fields are required.",
                "firstName" to firstName,
                "lastName" to lastName,
                "jobTitle" to jobTitle,
                "organisation" to organisation,
                "bio" to bio,
            )
        respondTemplate("pages/professionals/prof_quiz.peb", model = errorModel)
        return
    }

    ProfDatabase.updateProfessionalProfile(
        userId,
        ProfessionalProfile(
            firstName,
            lastName,
            jobTitle,
            organisation,
            bio,
        ),
    )
    respondRedirect("/Professional-Login")
}

/**
 * Just renders the professional login page
 * No data needed so the model is empty
 */
suspend fun ApplicationCall.profLoginPage() {
    respondTemplate("pages/professionals/proflogin.peb", model = emptyMap())
}

/**
 * Handles login for professional users.
 *
 * This is used by the professional login POST route when a professional submits
 * their email and password. It gets the submitted credentials, checks them
 * against the database, gets the user's roles, and only logs them in if they
 * have the "professional" role.
 *
 * This is needed so only registered professional users can access the
 * professional dashboard. Without this, normal clients could log in through
 * the professional login page and access professional only pages on dashboard.
 */
suspend fun ApplicationCall.loginProfessional() {
    val credentials = getCredentials()
    val email = credentials.first
    val password = credentials.second
    val result =
        runCatching {
            UserDatabase.checkCreds(email, password)
        }
    when {
        result.isFailure -> {
            respondTemplate(
                "pages/professionals/proflogin.peb",
                model = mapOf("error" to "Something went wrong, please try again"),
            )
        }

        result.getOrDefault(false) -> {
            val userId = getUserIdByEmail(email)
            val roles = getUserRoles(userId ?: -1)
            if (roles.contains("professional")) {
                sessions.set(UserSession(email))
                respondRedirect("/professionals_dash")
            } else {
                respondTemplate(
                    "pages/professionals/proflogin.peb",
                    model = mapOf("error" to "You are not registered as a professional"),
                )
            }
        }

        else -> {
            respondTemplate(
                "pages/professionals/proflogin.peb",
                model = mapOf("error" to "Invalid email or password"),
            )
        }
    }
}
