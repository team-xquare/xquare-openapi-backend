package app.xquare.xquareopenapi.global.jwt

data class RefreshTokenPayload(
    val userId: Long,
    val passwordFingerprint: String,
)
