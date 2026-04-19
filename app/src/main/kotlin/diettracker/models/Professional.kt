package diettracker.models

data class Professional(
    val id: Int,
    val email: String,
    val passwordHash: String,
    val firstName: String,
    val lastName: String,
    val jobTitle: String?,
    val bio: String?,
    val organization: String?,
)
