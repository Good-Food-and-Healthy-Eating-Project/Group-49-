import io.ktor.server.application.*
import io.ktor.server.pebble.*
import io.ktor.server.plugins.*

fun Application.configureTemplating() {
    install(Pebble)
}
