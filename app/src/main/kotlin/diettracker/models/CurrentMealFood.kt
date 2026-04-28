package diettracker.models
import kotlinx.serialization.Serializable

@Serializable
data class CurrentMealFood(
    val foodId: Int,
    val grams: Int,
)
