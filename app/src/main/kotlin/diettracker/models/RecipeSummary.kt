package diettracker.models

/**
 * Data for a summary view of a recipe for displaying in the recipes list
 *
 * Used when showing recipe cards on the main recipes page
 * [isFavourited] indicates whether the current user has favourited this recipe
 **/
data class RecipeSummary(
    val id: Int,
    val name: String,
    val thumbnail: String?,
    val isFavourited: Boolean = false,
)
