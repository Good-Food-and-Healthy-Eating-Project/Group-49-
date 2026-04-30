package diettracker

import io.ktor.server.http.content.staticResources
import io.ktor.server.routing.Route

fun Route.configureStatic() {
    staticResources("/static", "static")
}
