package diettracker.models

data class Professional(
    val id: Int,
    val email: String,
    // Nullable to avoid exposing password hashes in non-auth contexts
    val passwordHash: String? = null,
    val firstName: String,
    val lastName: String,
    val jobTitle: String?,
    val bio: String?,
    val organization: String?,
)
