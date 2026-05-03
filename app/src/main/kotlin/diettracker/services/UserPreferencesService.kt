
package diettracker.services

import diettracker.db.tables.Clients
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update

object UserPreferencesService {
    fun savePreferences(
        userId: Int,
        height: Int?,
        weight: Int?,
        goal: String?,
    ) {
        transaction {
            Clients.insertIgnore {
                it[Clients.client_id] = userId
            }

            Clients.update({ Clients.client_id eq userId }) {
                it[Clients.height_cm] = height
                it[Clients.weight_kg] = weight
                it[Clients.goal] = goal
            }
        }
    }
}
