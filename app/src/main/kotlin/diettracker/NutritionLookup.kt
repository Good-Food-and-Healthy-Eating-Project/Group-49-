package diettracker

import diettracker.db.tables.FoodLogItems
import diettracker.db.tables.FoodLogs
import diettracker.db.tables.Foods
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.javatime.date
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.time.LocalDate

fun calcCalcsById(
    foodid: Int,
    grams: Int,
): Int {
    val multiplier = grams / GRAMS_PER_SERVING.toDouble()
    val caloriesPer100g =
        Foods
            .selectAll()
            .where { Foods.food_id eq foodid }
            .map { row -> row[Foods.calories_per_100g].toDouble().toInt() }
            .firstOrNull() ?: return 0

    return (caloriesPer100g * multiplier).toInt()
}

fun calcProteinById(
    foodid: Int,
    grams: Int,
): Int {
    val multiplier = grams / GRAMS_PER_SERVING.toDouble()
    val proteinPer100g =
        Foods
            .selectAll()
            .where { Foods.food_id eq foodid }
            .map { row -> row[Foods.protein_per_100g].toDouble().toInt() }
            .firstOrNull() ?: return 0

    return (proteinPer100g * multiplier).toInt()
}

fun calcFatById(
    foodid: Int,
    grams: Int,
): Int {
    val multiplier = grams / GRAMS_PER_SERVING.toDouble()
    val fatPer100g =
        Foods
            .selectAll()
            .where { Foods.food_id eq foodid }
            .map { row -> row[Foods.fat_per_100g].toDouble().toInt() }
            .firstOrNull() ?: return 0

    return (fatPer100g * multiplier).toInt()
}

fun calcCarbsById(
    foodid: Int,
    grams: Int,
): Int {
    val multiplier = grams / GRAMS_PER_SERVING.toDouble()
    val carbsPer100g =
        Foods
            .selectAll()
            .where { Foods.food_id eq foodid }
            .map { row -> row[Foods.carbs_per_100g].toDouble().toInt() }
            .firstOrNull() ?: return 0

    return (carbsPer100g * multiplier).toInt()
}

data class DailyNutritionSummary(
    val totalCalories: Double,
    val totalProtein: Double,
    val totalCarbs: Double,
    val totalFat: Double,
)

fun getDailyNutritionSummary(
    userId: Int,
    today: LocalDate,
): DailyNutritionSummary {
    val dailyOverview =
        transaction {
            (FoodLogs innerJoin FoodLogItems innerJoin Foods)
                .selectAll()
                .where {
                    (FoodLogs.user_id eq userId) and (FoodLogs.log_date.date() eq today)
                }
                .map {
                    val quantity = it[FoodLogItems.quantity_g].toDouble()
                    val caloriesPer100g = it[Foods.calories_per_100g].toDouble()
                    val proteinPer100g = it[Foods.protein_per_100g].toDouble()
                    val carbsPer100g = it[Foods.carbs_per_100g].toDouble()
                    val fatPer100g = it[Foods.fat_per_100g].toDouble()
                    val convert = quantity / GRAMS_PER_SERVING.toDouble()
                    mapOf(
                        "calories" to caloriesPer100g * convert,
                        "protein" to proteinPer100g * convert,
                        "carbs" to carbsPer100g * convert,
                        "fat" to fatPer100g * convert,
                    )
                }
        }
    return DailyNutritionSummary(
        totalCalories = dailyOverview.sumOf { it["calories"] as Double },
        totalProtein = dailyOverview.sumOf { it["protein"] as Double },
        totalCarbs = dailyOverview.sumOf { it["carbs"] as Double },
        totalFat = dailyOverview.sumOf { it["fat"] as Double },
    )
}
