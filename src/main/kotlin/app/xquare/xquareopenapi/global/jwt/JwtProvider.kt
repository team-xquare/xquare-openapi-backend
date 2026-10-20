package app.xquare.xquareopenapi.global.jwt

import app.xquare.xquareopenapi.domain.user.Role
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.springframework.stereotype.Component
import java.time.Duration
import java.util.Date
import javax.crypto.SecretKey

@Component
class JwtProvider(
    private val jwtProperties: JwtProperties,
) {
    private val key: SecretKey = Keys.hmacShaKeyFor(jwtProperties.secret.toByteArray())

    fun generateAccessToken(userId: Long, role: Role): String =
        generateToken(userId, jwtProperties.accessExpiration, mapOf(TYPE_CLAIM to ACCESS_TYPE, ROLE_CLAIM to role.name))

    fun generateRefreshToken(userId: Long): RefreshToken =
        RefreshToken(
            value = generateToken(userId, jwtProperties.refreshExpiration, mapOf(TYPE_CLAIM to REFRESH_TYPE)),
            ttl = jwtProperties.refreshExpiration,
        )

    private fun generateToken(userId: Long, expiration: Duration, claims: Map<String, String>): String {
        val now = Date()
        return Jwts.builder()
            .subject(userId.toString())
            .claims(claims)
            .issuedAt(now)
            .expiration(Date(now.time + expiration.toMillis()))
            .signWith(key)
            .compact()
    }

    companion object {
        private const val TYPE_CLAIM = "type"
        private const val ROLE_CLAIM = "role"
        private const val ACCESS_TYPE = "access"
        private const val REFRESH_TYPE = "refresh"
    }
}
