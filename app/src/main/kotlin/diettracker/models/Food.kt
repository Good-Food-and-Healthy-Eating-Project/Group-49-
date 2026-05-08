package diettracker.models

data class Food(
    val id: Int,
    val name: String,
    val calories: Int,
    val protein: Int,
    val fat: Int,
    val carbs: Int,
)
