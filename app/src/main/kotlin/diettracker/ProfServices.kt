package diettracker

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.pebble.respondTemplate
import io.ktor.server.request.receiveParameters
import io.ktor.server.response.respondRedirect
import io.ktor.server.sessions.sessions
import io.ktor.server.sessions.set

suspend fun ApplicationCall.profSignUpPage() {
    respondTemplate("pages/professionals/profsignup.peb", model = emptyMap())
}

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

suspend fun ApplicationCall.profQuizPage(userId: String) {
    respondTemplate(
        "pages/professionals/prof_quiz.peb",
        model = mapOf(
            "userId" to userId,
            "firstName" to "",
            "lastName" to "",
            "jobTitle" to "",
            "organisation" to "",
            "bio" to "",
        ),
    )
}

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
        respondTemplate(
            "pages/professionals/prof_quiz.peb",
            model = mapOf(
                "userId" to userId.toString(),
                "error" to "All fields are required.",
                "firstName" to firstName,
                "lastName" to lastName,
                "jobTitle" to jobTitle,
                "organisation" to organisation,
                "bio" to bio,
            ),
        )
        return
    }

    ProfDatabase.updateProfessionalProfile(userId, firstName, lastName, jobTitle, organisation, bio)
    respondRedirect("/Professional-Login")
}

suspend fun ApplicationCall.profLoginPage() {
    respondTemplate("pages/professionals/proflogin.peb", model = emptyMap())
}

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
                respondRedirect("/professionals")
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
