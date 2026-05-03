package diettracker.models

/**
 * Stores information about clients who have signed up with a professional.
 * **/

data class ClientInfo(
    val id: Int,
    val firstName: String,
    val lastName: String,
    val email: String,
    // Making the following nullable as the quiz is optional
    val goal: String?,
    val heightCm: Int?,
    val weightKg: Int?,
)
