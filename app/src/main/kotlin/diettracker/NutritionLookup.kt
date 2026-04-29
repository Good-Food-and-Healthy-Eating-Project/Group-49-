package diettracker

import diettracker.db.tables.Foods
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll

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
