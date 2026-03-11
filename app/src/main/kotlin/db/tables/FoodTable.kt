package diettracker.db.tables

import org.jetbrains.exposed.v1.core.Table
import diettracker.db.MAX_LEN

object Foods : Table("foods") {
    val food_id = integer("food_id").autoIncrement()
    val food_name = varchar("food_name", MAX_LEN)

    val usda_fdc_id = long("usda_fdc_id").nullable().uniqueIndex()

    val calories_per_100g = decimal("calories_per_100g", 8, 2)
    val protein_per_100g = decimal("protein_per_100g", 8, 2)   // keeping your current spelling
    val carbs_per_100g = decimal("carbs_per_100g", 8, 2)
    val fat_per_100g = decimal("fat_per_100g", 8, 2)

    val fiber_per_100g = decimal("fiber_per_100g", 8, 2).nullable()
    val sugar_per_100g = decimal("sugar_per_100g", 8, 2).nullable()

    val sodium_mg_per_100g = decimal("sodium_mg_per_100g", 10, 2).nullable()
    val potassium_mg_per_100g = decimal("potassium_mg_per_100g", 10, 2).nullable()
    val calcium_mg_per_100g = decimal("calcium_mg_per_100g", 10, 2).nullable()
    val iron_mg_per_100g = decimal("iron_mg_per_100g", 10, 2).nullable()
    val magnesium_mg_per_100g = decimal("magnesium_mg_per_100g", 10, 2).nullable()
    val zinc_mg_per_100g = decimal("zinc_mg_per_100g", 10, 2).nullable()

    val vitamin_a_mcg_per_100g = decimal("vitamin_a_mcg_per_100g", 10, 2).nullable()
    val vitamin_c_mg_per_100g = decimal("vitamin_c_mg_per_100g", 10, 2).nullable()
    val vitamin_d_mcg_per_100g = decimal("vitamin_d_mcg_per_100g", 10, 2).nullable()
    val vitamin_b6_mg_per_100g = decimal("vitamin_b6_mg_per_100g", 10, 2).nullable()
    val vitamin_b12_mcg_per_100g = decimal("vitamin_b12_mcg_per_100g", 10, 2).nullable()

    override val primaryKey = PrimaryKey(food_id)
}