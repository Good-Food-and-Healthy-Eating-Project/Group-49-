package diettracker.models

data class Professional(
    val id: Int,
    val firstName: String,
    val lastName: String,
    val email: String,
    val passwordHash: String,
    val jobTitle: String,
    val organisation: String,
    val bio: String,
)
