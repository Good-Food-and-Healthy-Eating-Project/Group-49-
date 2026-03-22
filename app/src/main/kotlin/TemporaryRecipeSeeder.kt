package diettracker

import diettracker.db.tables.Foods
import diettracker.db.tables.RecipeIngredients
import diettracker.db.tables.Recipes
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.isNotDistinctFrom
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.math.BigDecimal
import java.math.RoundingMode
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets

@Suppress("unused", "SpellCheckingInspection")
object TemporaryRecipeSeeder {

    private val http = HttpClient.newHttpClient()
    private val json = Json { ignoreUnknownKeys = true }

    private const val MEAL_DB_BASE = "https://www.themealdb.com/api/json/v1/1"
    private const val USDA_BASE = "https://api.nal.usda.gov/fdc/v1"

    fun seed(
        systemUserId: Int,
        usdaApiKey: String = System.getenv("USDA_API_KEY") ?: ""
    ) {
        val categories = fetchCategories()

        for (category in categories) {
            println("Seeding category: $category")

            val meals = fetchMealsByCategory(category)

            for (mealSummary in meals) {
                try {
                    val meal = fetchMealById(mealSummary.idMeal) ?: continue
                    upsertRecipeWithIngredients(
                        meal = meal,
                        systemUserId = systemUserId,
                        usdaApiKey = usdaApiKey
                    )
                    println("Seeded: ${meal.strMeal}")
                } catch (e: Exception) {
                    println("Failed to seed meal ${mealSummary.idMeal}: ${e.message}")
                }
            }
        }
    }

    private fun upsertRecipeWithIngredients(
        meal: MealDbMeal,
        systemUserId: Int,
        usdaApiKey: String
    ) {
        val recipeId = transaction {
            val existing = Recipes
                .selectAll()
                .where { Recipes.external_mealdb_id.isNotDistinctFrom(meal.idMeal) }
                .firstOrNull()

            if (existing == null) {
                val inserted = Recipes.insert {
                    it[recipe_name] = meal.strMeal.lowercase()
                    it[instructions] = meal.strInstructions?.lowercase() ?: ""
                    it[external_mealdb_id] = meal.idMeal
                    it[category] = meal.strCategory?.lowercase()
                    it[area] = meal.strArea?.lowercase()
                    it[thumbnail_url] = meal.strMealThumb
                    it[created_by_user_id] = systemUserId
                    it[is_system_recipe] = true
                }
                inserted[Recipes.recipes_id]
            } else {
                val id = existing[Recipes.recipes_id]
                Recipes.update({ Recipes.recipes_id.isNotDistinctFrom(id) }) {
                    it[recipe_name] = meal.strMeal.lowercase()
                    it[instructions] = meal.strInstructions?.lowercase() ?: ""
                    it[category] = meal.strCategory?.lowercase()
                    it[area] = meal.strArea?.lowercase()
                    it[thumbnail_url] = meal.strMealThumb
                    it[is_system_recipe] = true
                }
                id
            }
        }

        transaction {
            RecipeIngredients.deleteWhere { RecipeIngredients.recipe_id.isNotDistinctFrom(recipeId) }
        }

        val ingredients = extractIngredients(meal)

        for ((ingredientName, measureText) in ingredients) {
            val foodId = findOrCreateFood(ingredientName, usdaApiKey)
            val quantityG = estimateQuantityInGrams(measureText, ingredientName)

            transaction {
                RecipeIngredients.insert {
                    it[recipe_id] = recipeId
                    it[food_id] = foodId
                    it[RecipeIngredients.quantity_g] = quantityG
                    it[original_measure] = measureText?.lowercase()
                }
            }
        }
    }

    private fun findOrCreateFood(
        ingredientName: String,
        usdaApiKey: String
    ): Int {
        val normalised = normaliseFoodName(ingredientName)

        val existingId = transaction {
            findCanonicalFoodIdByNormalisedName(normalised)
        }

        if (existingId != null) return existingId

        val usdaFood = if (usdaApiKey.isNotBlank()) searchUsdaFood(normalised, usdaApiKey) else null

        val calories = nutrientValue(usdaFood, "Energy")
        val protein = nutrientValue(usdaFood, "Protein")
        val carbs = nutrientValue(usdaFood, "Carbohydrate, by difference")
        val fat = nutrientValue(usdaFood, "Total lipid (fat)")

        val fiber = nutrientValue(usdaFood, "Fiber, total dietary")
        val sugar = nutrientValue(usdaFood, "Sugars, total including NLEA")

        val sodium = nutrientValue(usdaFood, "Sodium, Na")
        val potassium = nutrientValue(usdaFood, "Potassium, K")
        val calcium = nutrientValue(usdaFood, "Calcium, Ca")
        val iron = nutrientValue(usdaFood, "Iron, Fe")
        val magnesium = nutrientValue(usdaFood, "Magnesium, Mg")
        val zinc = nutrientValue(usdaFood, "Zinc, Zn")

        val vitaminA = nutrientValue(usdaFood, "Vitamin A, RAE")
        val vitaminC = nutrientValue(usdaFood, "Vitamin C, total ascorbic acid")
        val vitaminD = nutrientValue(usdaFood, "Vitamin D (D2 + D3)")
        val vitaminB6 = nutrientValue(usdaFood, "Vitamin B-6")
        val vitaminB12 = nutrientValue(usdaFood, "Vitamin B-12")

        return transaction {
            val inserted = Foods.insert {
                it[food_name] = normalised
                it[usda_fdc_id] = usdaFood?.fdcId
                it[calories_per_100g] = calories ?: BigDecimal.ZERO
                it[protein_per_100g] = protein ?: BigDecimal.ZERO
                it[carbs_per_100g] = carbs ?: BigDecimal.ZERO
                it[fat_per_100g] = fat ?: BigDecimal.ZERO

                it[fiber_per_100g] = fiber
                it[sugar_per_100g] = sugar

                it[sodium_mg_per_100g] = sodium
                it[potassium_mg_per_100g] = potassium
                it[calcium_mg_per_100g] = calcium
                it[iron_mg_per_100g] = iron
                it[magnesium_mg_per_100g] = magnesium
                it[zinc_mg_per_100g] = zinc

                it[vitamin_a_mcg_per_100g] = vitaminA
                it[vitamin_c_mg_per_100g] = vitaminC
                it[vitamin_d_mcg_per_100g] = vitaminD
                it[vitamin_b6_mg_per_100g] = vitaminB6
                it[vitamin_b12_mcg_per_100g] = vitaminB12
            }
            inserted[Foods.food_id]
        }
    }

    private fun findCanonicalFoodIdByNormalisedName(normalisedFoodName: String): Int? {
        val matchingFoods = Foods
            .selectAll()
            .filter { row -> normaliseFoodName(row[Foods.food_name]) == normalisedFoodName }
            .sortedBy { row -> row[Foods.food_id] }

        if (matchingFoods.isEmpty()) return null

        val canonicalId = matchingFoods.first()[Foods.food_id]
        val duplicateIds = matchingFoods.drop(1).map { row -> row[Foods.food_id] }

        if (duplicateIds.isNotEmpty()) {
            mergeDuplicateFoods(canonicalId, duplicateIds)
        }

        Foods.update({ Foods.food_id.isNotDistinctFrom(canonicalId) }) {
            it[food_name] = normalisedFoodName
        }

        return canonicalId
    }

    private fun mergeDuplicateFoods(canonicalFoodId: Int, duplicateFoodIds: List<Int>) {
        for (duplicateFoodId in duplicateFoodIds) {
            val duplicateIngredients = RecipeIngredients
                .selectAll()
                .where { RecipeIngredients.food_id.isNotDistinctFrom(duplicateFoodId) }
                .toList()

            for (duplicateIngredient in duplicateIngredients) {
                val recipeId = duplicateIngredient[RecipeIngredients.recipe_id]
                val duplicateQty = duplicateIngredient[RecipeIngredients.quantity_g]
                val duplicateMeasure = duplicateIngredient[RecipeIngredients.original_measure]

                val canonicalIngredient = RecipeIngredients
                    .selectAll()
                    .where { RecipeIngredients.recipe_id.isNotDistinctFrom(recipeId) }
                    .firstOrNull { it[RecipeIngredients.food_id] == canonicalFoodId }

                if (canonicalIngredient == null) {
                    RecipeIngredients.update({
                        RecipeIngredients.recipe_id.isNotDistinctFrom(recipeId) and
                            RecipeIngredients.food_id.isNotDistinctFrom(duplicateFoodId)
                    }) {
                        it[food_id] = canonicalFoodId
                    }
                    continue
                }

                val combinedQty = canonicalIngredient[RecipeIngredients.quantity_g]
                    .add(duplicateQty)
                    .setScale(2, RoundingMode.HALF_UP)

                val canonicalMeasure = canonicalIngredient[RecipeIngredients.original_measure]

                RecipeIngredients.update({
                    RecipeIngredients.recipe_id.isNotDistinctFrom(recipeId) and
                        RecipeIngredients.food_id.isNotDistinctFrom(canonicalFoodId)
                }) {
                    it[quantity_g] = combinedQty
                    if (canonicalMeasure.isNullOrBlank() && !duplicateMeasure.isNullOrBlank()) {
                        it[original_measure] = duplicateMeasure.lowercase()
                    }
                }

                RecipeIngredients.deleteWhere {
                    RecipeIngredients.recipe_id.isNotDistinctFrom(recipeId) and
                        RecipeIngredients.food_id.isNotDistinctFrom(duplicateFoodId)
                }
            }

            Foods.deleteWhere { Foods.food_id.isNotDistinctFrom(duplicateFoodId) }
        }
    }

    private fun extractIngredients(meal: MealDbMeal): List<Pair<String, String?>> {
        val raw = listOf(
            meal.strIngredient1 to meal.strMeasure1,
            meal.strIngredient2 to meal.strMeasure2,
            meal.strIngredient3 to meal.strMeasure3,
            meal.strIngredient4 to meal.strMeasure4,
            meal.strIngredient5 to meal.strMeasure5,
            meal.strIngredient6 to meal.strMeasure6,
            meal.strIngredient7 to meal.strMeasure7,
            meal.strIngredient8 to meal.strMeasure8,
            meal.strIngredient9 to meal.strMeasure9,
            meal.strIngredient10 to meal.strMeasure10,
            meal.strIngredient11 to meal.strMeasure11,
            meal.strIngredient12 to meal.strMeasure12,
            meal.strIngredient13 to meal.strMeasure13,
            meal.strIngredient14 to meal.strMeasure14,
            meal.strIngredient15 to meal.strMeasure15,
            meal.strIngredient16 to meal.strMeasure16,
            meal.strIngredient17 to meal.strMeasure17,
            meal.strIngredient18 to meal.strMeasure18,
            meal.strIngredient19 to meal.strMeasure19,
            meal.strIngredient20 to meal.strMeasure20
        )

        return raw.mapNotNull { (ingredient, measure) ->
            val cleanIngredient = ingredient?.trim()?.lowercase().orEmpty()
            if (cleanIngredient.isBlank()) null else cleanIngredient to measure?.trim()?.lowercase()
        }
    }

    private fun fetchCategories(): List<String> {
        val body = get("$MEAL_DB_BASE/categories.php")
        val response = json.decodeFromString<MealDbCategoriesResponse>(body)
        return response.categories.map { it.strCategory }
    }

    private fun fetchMealsByCategory(category: String): List<MealDbMealSummary> {
        val body = get("$MEAL_DB_BASE/filter.php?c=${urlEncode(category)}")
        val response = json.decodeFromString<MealDbMealsResponse>(body)
        return response.meals ?: emptyList()
    }

    private fun fetchMealById(id: String): MealDbMeal? {
        val body = get("$MEAL_DB_BASE/lookup.php?i=${urlEncode(id)}")
        val response = json.decodeFromString<MealDbMealLookupResponse>(body)
        return response.meals?.firstOrNull()
    }

    private fun searchUsdaFood(query: String, apiKey: String): UsdaFood? {
        return try {
            val body = get(
                "$USDA_BASE/foods/search?api_key=$apiKey&query=${urlEncode(query)}&pageSize=1"
            )
            val response = json.decodeFromString<UsdaSearchResponse>(body)
            response.foods.firstOrNull()
        } catch (e: Exception) {
            println("USDA lookup failed for '$query': ${e.message}")
            null
        }
    }

    private fun nutrientValue(food: UsdaFood?, nutrientName: String): BigDecimal? {
        val raw = food?.foodNutrients
            ?.firstOrNull { it.nutrientName.equals(nutrientName, ignoreCase = true) }
            ?.value ?: return null

        return try {
            BigDecimal.valueOf(raw).setScale(2, RoundingMode.HALF_UP)
        } catch (_: Exception) {
            null
        }
    }

    private fun estimateQuantityInGrams(measureText: String?, ingredientName: String): BigDecimal {
        if (measureText.isNullOrBlank()) return BigDecimal("100.00")

        val text = measureText.lowercase().trim()

        parseMass(text)?.let { return it }
        parseVolume(text)?.let { return it }
        parseCount(text, ingredientName)?.let { return it }

        return BigDecimal("100.00")
    }

    private fun parseMass(text: String): BigDecimal? {
        val regex = Regex("""(\d+(?:\.\d+)?|\d+/\d+)\s*(kg|g)""")
        val match = regex.find(text) ?: return null
        val amount = parseNumber(match.groupValues[1]) ?: return null
        val unit = match.groupValues[2]

        val grams = when (unit) {
            "kg" -> amount.multiply(BigDecimal("1000"))
            "g" -> amount
            else -> return null
        }

        return grams.setScale(2, RoundingMode.HALF_UP)
    }

    private fun parseVolume(text: String): BigDecimal? {
        val regex = Regex("""(\d+(?:\.\d+)?|\d+/\d+)\s*(ml|l|tsp|tbsp|cup)""")
        val match = regex.find(text) ?: return null
        val amount = parseNumber(match.groupValues[1]) ?: return null
        val unit = match.groupValues[2]

        val grams = when (unit) {
            "ml" -> amount
            "l" -> amount.multiply(BigDecimal("1000"))
            "tsp" -> amount.multiply(BigDecimal("5"))
            "tbsp" -> amount.multiply(BigDecimal("15"))
            "cup" -> amount.multiply(BigDecimal("240"))
            else -> return null
        }

        return grams.setScale(2, RoundingMode.HALF_UP)
    }

    private fun parseCount(text: String, ingredientName: String): BigDecimal? {
        val regex = Regex("""(\d+(?:\.\d+)?|\d+/\d+)""")
        val match = regex.find(text) ?: return null
        val count = parseNumber(match.groupValues[1]) ?: return null

        val perUnit = when {
            ingredientName.contains("egg", ignoreCase = true) -> BigDecimal("50")
            ingredientName.contains("garlic", ignoreCase = true) -> BigDecimal("5")
            ingredientName.contains("onion", ignoreCase = true) -> BigDecimal("110")
            ingredientName.contains("tomato", ignoreCase = true) -> BigDecimal("120")
            ingredientName.contains("carrot", ignoreCase = true) -> BigDecimal("60")
            else -> BigDecimal("100")
        }

        return count.multiply(perUnit).setScale(2, RoundingMode.HALF_UP)
    }

    private fun parseNumber(value: String): BigDecimal? {
        return try {
            if (value.contains("/")) {
                val parts = value.split("/")
                if (parts.size != 2) return null
                val numerator = BigDecimal(parts[0])
                val denominator = BigDecimal(parts[1])
                numerator.divide(denominator, 4, RoundingMode.HALF_UP)
            } else {
                BigDecimal(value)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun normaliseFoodName(name: String): String {
        return name.trim().lowercase().replace(Regex("""\s+"""), " ")
    }

    private fun get(url: String): String {
        val request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .GET()
            .build()

        val response = http.send(request, HttpResponse.BodyHandlers.ofString())

        if (response.statusCode() !in 200..299) {
            error("HTTP ${response.statusCode()} for $url")
        }

        return response.body()
    }

    private fun urlEncode(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8)
}

@Serializable
data class MealDbCategoriesResponse(
    val categories: List<MealDbCategory>
)

@Serializable
data class MealDbCategory(
    @SerialName("strCategory") val strCategory: String
)

@Serializable
data class MealDbMealsResponse(
    val meals: List<MealDbMealSummary>? = null
)

@Serializable
data class MealDbMealSummary(
    @SerialName("idMeal") val idMeal: String,
    @SerialName("strMeal") val strMeal: String,
    @SerialName("strMealThumb") val strMealThumb: String? = null
)

@Serializable
data class MealDbMealLookupResponse(
    val meals: List<MealDbMeal>? = null
)

@Serializable
data class MealDbMeal(
    @SerialName("idMeal") val idMeal: String,
    @SerialName("strMeal") val strMeal: String,
    @SerialName("strCategory") val strCategory: String? = null,
    @SerialName("strArea") val strArea: String? = null,
    @SerialName("strInstructions") val strInstructions: String? = null,
    @SerialName("strMealThumb") val strMealThumb: String? = null,

    @SerialName("strIngredient1") val strIngredient1: String? = null,
    @SerialName("strIngredient2") val strIngredient2: String? = null,
    @SerialName("strIngredient3") val strIngredient3: String? = null,
    @SerialName("strIngredient4") val strIngredient4: String? = null,
    @SerialName("strIngredient5") val strIngredient5: String? = null,
    @SerialName("strIngredient6") val strIngredient6: String? = null,
    @SerialName("strIngredient7") val strIngredient7: String? = null,
    @SerialName("strIngredient8") val strIngredient8: String? = null,
    @SerialName("strIngredient9") val strIngredient9: String? = null,
    @SerialName("strIngredient10") val strIngredient10: String? = null,
    @SerialName("strIngredient11") val strIngredient11: String? = null,
    @SerialName("strIngredient12") val strIngredient12: String? = null,
    @SerialName("strIngredient13") val strIngredient13: String? = null,
    @SerialName("strIngredient14") val strIngredient14: String? = null,
    @SerialName("strIngredient15") val strIngredient15: String? = null,
    @SerialName("strIngredient16") val strIngredient16: String? = null,
    @SerialName("strIngredient17") val strIngredient17: String? = null,
    @SerialName("strIngredient18") val strIngredient18: String? = null,
    @SerialName("strIngredient19") val strIngredient19: String? = null,
    @SerialName("strIngredient20") val strIngredient20: String? = null,

    @SerialName("strMeasure1") val strMeasure1: String? = null,
    @SerialName("strMeasure2") val strMeasure2: String? = null,
    @SerialName("strMeasure3") val strMeasure3: String? = null,
    @SerialName("strMeasure4") val strMeasure4: String? = null,
    @SerialName("strMeasure5") val strMeasure5: String? = null,
    @SerialName("strMeasure6") val strMeasure6: String? = null,
    @SerialName("strMeasure7") val strMeasure7: String? = null,
    @SerialName("strMeasure8") val strMeasure8: String? = null,
    @SerialName("strMeasure9") val strMeasure9: String? = null,
    @SerialName("strMeasure10") val strMeasure10: String? = null,
    @SerialName("strMeasure11") val strMeasure11: String? = null,
    @SerialName("strMeasure12") val strMeasure12: String? = null,
    @SerialName("strMeasure13") val strMeasure13: String? = null,
    @SerialName("strMeasure14") val strMeasure14: String? = null,
    @SerialName("strMeasure15") val strMeasure15: String? = null,
    @SerialName("strMeasure16") val strMeasure16: String? = null,
    @SerialName("strMeasure17") val strMeasure17: String? = null,
    @SerialName("strMeasure18") val strMeasure18: String? = null,
    @SerialName("strMeasure19") val strMeasure19: String? = null,
    @SerialName("strMeasure20") val strMeasure20: String? = null
)

@Serializable
data class UsdaSearchResponse(
    val foods: List<UsdaFood> = emptyList()
)

@Serializable
data class UsdaFood(
    val fdcId: Long,
    val description: String? = null,
    val foodNutrients: List<UsdaFoodNutrient> = emptyList()
)

@Serializable
data class UsdaFoodNutrient(
    val nutrientName: String? = null,
    val value: Double? = null,
    val unitName: String? = null
)
