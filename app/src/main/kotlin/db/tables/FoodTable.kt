package diettracker.db.tables
import org.jetbrains.exposed.v1.core.Table
import diettracker.db.MAX_LEN

object Foods : Table("foods"){
    val food_id = integer("food_id").autoIncrement()
    val food_name = varchar("food_name",MAX_LEN)
    val calories_per_100g = decimal("calories_per_100g",6,2)
    val protien_per_100g = decimal("protien_per_100g",6,2)
    val carbs_per_100g = decimal("carbs_per_100g",6,2)
    val fat_per_100g = decimal("fat_per_100g",6,2)
    override val primaryKey = PrimaryKey(food_id)
    
}