package diettracker.models

data class RecipeIngredientDetails(
    val foodName: String,
    val quantityGrams: Double,
    val humanReadableMeasure: String?,
)

data class RecipeDetails(
    val id: Int,
    val name: String,
    val thumbnail: String?,
    val category: String?,
    val area: String?,
    val instructions: String,
    val ingredients: List<RecipeIngredientDetails>,
)
