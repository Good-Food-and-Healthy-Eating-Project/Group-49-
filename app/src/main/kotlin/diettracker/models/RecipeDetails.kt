package diettracker.models

/**
 * Data which represents the full details ofa recipe for display on the recipes details page
 *
 * includes all recipe metadata, including a list of ingredients with quantities
 **/
data class RecipeDetails(
    val id: Int,
    val name: String,
    val thumbnail: String?,
    val category: String?,
    val area: String?,
    val instructions: String,
    val ingredients: List<RecipeIngredientDetails>,
)

/**
 * Represents a single ingredient in a recipe, with its quantity
 *
 * [humanReadableMeasure] is actually the original measure string from the recipe source
 * for example: "2 cups" or "1 tbsp". May differ in gram quantity
 **/
data class RecipeIngredientDetails(
    val foodName: String,
    val quantityGrams: Double,
    val humanReadableMeasure: String?,
)
