package diettracker.models
import kotlinx.serialization.Serializable

@Serializable
data class CurrentMealSession(
    val foods: List<CurrentMealFood>,
)
