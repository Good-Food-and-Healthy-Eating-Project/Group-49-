package diettracker.routes

import diettracker.db.tables.Clients
import io.ktor.server.request.receiveParameters
import io.ktor.server.response.respondRedirect
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update

fun Route.quizRoutes() {
    post("/quiz") {
        val params = call.receiveParameters()

        val userId = params["userId"]?.toIntOrNull()
        if (userId == null) {
            call.respondRedirect("/Sign-Up")
            return@post
        }

        val height = params["height"]?.toIntOrNull()
        val weight = params["weight"]?.toIntOrNull()
        val goal = params["goal"]

        transaction {
            val exists =
                Clients
                    .selectAll()
                    .where { Clients.client_id eq userId }
                    .empty()
                    .not()
            if (!exists) {
                Clients.insert {
                    it[Clients.client_id] = userId
                    it[Clients.height_cm] = height
                    it[Clients.weight_kg] = weight
                    it[Clients.goal] = goal
                }
            } else {
                Clients.update({ Clients.client_id eq userId }) {
                    it[Clients.height_cm] = height
                    it[Clients.weight_kg] = weight
                    it[Clients.goal] = goal
                }
            }
        }

        call.respondRedirect("/client_dash")
    }
}
