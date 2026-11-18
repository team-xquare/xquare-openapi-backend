package app.xquare.xquareopenapi.global.jwt

import app.xquare.xquareopenapi.domain.user.Role

data class AccessTokenPayload(
    val userId: Long,
    val role: Role,
)
