package diettracker.models

data class ClientInfo(
    val id: Int,
    val firstName: String,
    val lastName: String,
    val email: String,
    val goal: String?,
    val heightCm: Int?,
    val weightKg: Int?,
)
