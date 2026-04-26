package diettracker.models

data class RecipeSummary(
    val id: Int,
    val name: String,
    val thumbnail: String?,
    val isFavourited: Boolean = false,
)
