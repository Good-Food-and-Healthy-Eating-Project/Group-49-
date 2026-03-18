import io.ktor.server.application.*
import io.ktor.server.netty.EngineMain
import diettracker.db.DatabaseFactory

fun main(args: Array<String>) {
    EngineMain.main(args)
}

fun Application.module() {
    DatabaseFactory.init()

    val shouldSeed =
        System.getenv("SEED_RECIPES_ON_STARTUP")?.equals("true", ignoreCase = true) == true
    if (shouldSeed) {
        TemporaryRecipeSeeder.seedIfNeeded()
    }

    configureTemplating()
    configureRouting()
}