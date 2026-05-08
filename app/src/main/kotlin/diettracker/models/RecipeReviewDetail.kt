package diettracker.models

/**
 * Data for a single user review for a recipe
 *
 * Used when displaying reviews on the recipe detail page
 * [rating] is between 1 and 5, [createdAt] is an ISO timestamp string
 **/
data class RecipeReviewDetail(
    val rating: Int,
    val comment: String,
    val userEmail: String,
    val createdAt: String,
)
