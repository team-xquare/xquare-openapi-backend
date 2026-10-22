package app.xquare.xquareopenapi.global.jwt

import app.xquare.xquareopenapi.domain.user.Role
import io.jsonwebtoken.JwtException
import io.jsonwebtoken.JwtParser
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.springframework.stereotype.Component
import java.time.Duration
import java.util.Date
import java.util.UUID
import javax.crypto.SecretKey

@Component
class JwtProvider(
    private val jwtProperties: JwtProperties,
) {
    private val key: SecretKey = Keys.hmacShaKeyFor(jwtProperties.secret.toByteArray())
    private val parser: JwtParser = Jwts.parser().verifyWith(key).build()

    fun generateAccessToken(userId: Long, role: Role): String =
        generateToken(userId, jwtProperties.accessExpiration, mapOf(TYPE_CLAIM to ACCESS_TYPE, ROLE_CLAIM to role.name))

    fun generateRefreshToken(userId: Long): RefreshToken =
        RefreshToken(
            value = generateToken(userId, jwtProperties.refreshExpiration, mapOf(TYPE_CLAIM to REFRESH_TYPE)),
            ttl = jwtProperties.refreshExpiration,
        )

    // 서명이 틀리거나 만료됐거나 리프레시 토큰이 아니면 null을 돌려준다.
    fun parseRefreshToken(token: String): Long? {
        val claims = try {
            parser.parseSignedClaims(token).payload
        } catch (e: JwtException) {
            return null
        } catch (e: IllegalArgumentException) {
            return null
        }
        if (claims[TYPE_CLAIM] != REFRESH_TYPE) return null
        return claims.subject.toLong()
    }

    private fun generateToken(userId: Long, expiration: Duration, claims: Map<String, String>): String {
        val now = Date()
        return Jwts.builder()
            // 같은 초에 발급한 토큰끼리도 달라야 재발급으로 폐기한 토큰을 구분할 수 있다.
            .id(UUID.randomUUID().toString())
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
