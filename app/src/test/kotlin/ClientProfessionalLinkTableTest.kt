import diettracker.db.tables.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.deleteAll
import org.jetbrains.exposed.v1.jdbc.update
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.core.and
import java.time.LocalDate
import java.time.Instant
import java.math.BigDecimal
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ClientProfessionalLinkTableTest{
    @BeforeEach
    fun setUP(){
        TestDatabaseFactory.init()
        transaction{
            ClientProfessionalLink.deleteAll()
            Clients.deleteAll()
            Professionals.deleteAll()
            Users.deleteAll()
        }
    }
    private fun insertTestData():Pair<Int,Int>{
            val time = Instant.now()
            val userId = Users.insert{
                it[first_name] = "Sponge"
                it[second_name] = "Bob"
                it[email] = "test@test.com"
                it[password_hash] = "password_hash"
                it[created_at] = time
            }get Users.user_id
            Clients.insert{
                it[client_id] = userId
                it[data_of_birth] = LocalDate.of(1999,9,12)
                it[height_cm] = 180
                it[weight_kg] = 80
            }
            val proUser = Users.insert{
                it[first_name] = "Pro"
                it[second_name] = "User"
                it[email] = "Pro@test.com"
                it[password_hash] = "password_hash"
                it[created_at] = time
            }get Users.user_id
            Professionals.insert{
                it[professional_id] = proUser
                it[job_title] = "tester"
                it[organistation] = "test"
                it[bio] = "test"
            }
            return Pair(userId, proUser)
        }
    @Test
    fun should_insert_link_success(){
        transaction{
            val (clientId, professionalId) = insertTestData()
            ClientProfessionalLink.insert{
                it[client_id] = clientId
                it[professional_id] = professionalId
            }
            val link = ClientProfessionalLink.selectAll().toList()
            assertEquals(1,link.size)
            assertEquals(clientId,link[0][ClientProfessionalLink.client_id])
            assertEquals(professionalId,link[0][ClientProfessionalLink.professional_id])
        }
    }
    @Test
    fun should_delet_link_success(){
        transaction{
            val (clientId, professionalId) = insertTestData()
            ClientProfessionalLink.insert{
                it[client_id] = clientId
                it[professional_id] = professionalId
            }
            ClientProfessionalLink.deleteWhere{ClientProfessionalLink.client_id eq clientId}
             val link = ClientProfessionalLink
             .selectAll()
             .where{ClientProfessionalLink.client_id eq clientId}
             .toList()
             assertTrue(link.isEmpty())
        }
    }
    @Test
    fun should_update_professional__id_success(){
        transaction{
            val (clientId, professionalId) = insertTestData()
            val time = Instant.now()
            ClientProfessionalLink.insert{
                it[client_id] = clientId
                it[professional_id] = professionalId
            }
            val newPro = Users.insert{
                it[first_name] = "New"
                it[second_name] = "Pro"
                it[email] = "Newpro@test.com"
                it[password_hash] = "password_hash"
                it[created_at] = time
            }get Users.user_id
            Professionals.insert{
                it[professional_id] = newPro
                it[job_title] = "tester_1"
                it[organistation] = "test_1"
                it[bio] = "test_1"
            }
            ClientProfessionalLink.update({ClientProfessionalLink.client_id eq clientId}){
                it[professional_id] = newPro
            }
            val updateLink = ClientProfessionalLink
            .selectAll()
            .where{ClientProfessionalLink.client_id eq clientId}
            .single()
            assertEquals(clientId,updateLink[ClientProfessionalLink.client_id])
            assertEquals(newPro, updateLink[ClientProfessionalLink.professional_id])
        } 
    }
    @Test
    fun should_get_link_by_client_id(){
        transaction{
            val (clientId, professionalId) = insertTestData()
            ClientProfessionalLink.insert{
                it[client_id] = clientId
                it[professional_id] = professionalId
            }
            val link = ClientProfessionalLink
            .selectAll()
            .where{ClientProfessionalLink.client_id eq clientId}
            .singleOrNull()
            assertNotNull(link)
            assertEquals(clientId,link[ClientProfessionalLink.client_id])
            assertEquals(professionalId,link[ClientProfessionalLink.professional_id])

        }
    }
    @Test
    fun should_fail_when_insert_same_link(){
        transaction{
            val (clientId, professionalId) = insertTestData()
            ClientProfessionalLink.insert{
                it[client_id] = clientId
                it[professional_id] = professionalId
            }
            assertFailsWith<Exception>{
                ClientProfessionalLink.insert{
                    it[client_id] = clientId
                    it[professional_id] = professionalId
                }
            }
        }
    }
}