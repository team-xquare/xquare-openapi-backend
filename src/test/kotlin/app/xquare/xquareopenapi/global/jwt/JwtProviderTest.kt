package app.xquare.xquareopenapi.global.jwt

import app.xquare.xquareopenapi.domain.user.Role
import io.jsonwebtoken.Claims
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import io.jsonwebtoken.security.WeakKeyException
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertNull

class JwtProviderTest {

    private val properties = JwtProperties(
        secret = "test-secret-key-must-be-at-least-32-bytes-long",
        accessExpiration = Duration.ofHours(1),
        refreshExpiration = Duration.ofDays(14),
    )
    private val jwtProvider = JwtProvider(properties)

    @Test
    fun `액세스 토큰에는 사용자 id, 권한, 만료시각이 담긴다`() {
        val claims = parse(jwtProvider.generateAccessToken(7L, Role.USER))

        assertEquals("7", claims.subject)
        assertEquals("access", claims["type"])
        assertEquals("USER", claims["role"])
        assertEquals(properties.accessExpiration.toMillis(), claims.expiration.time - claims.issuedAt.time)
    }

    @Test
    fun `리프레시 토큰에는 사용자 id와 만료시각만 담긴다`() {
        val refreshToken = jwtProvider.generateRefreshToken(7L)
        val claims = parse(refreshToken.value)

        assertEquals("7", claims.subject)
        assertEquals("refresh", claims["type"])
        assertNull(claims["role"])
        assertEquals(properties.refreshExpiration.toMillis(), claims.expiration.time - claims.issuedAt.time)
        assertEquals(properties.refreshExpiration, refreshToken.ttl)
    }

    @Test
    fun `secret이 256비트보다 짧으면 생성되지 않는다`() {
        assertThrows<WeakKeyException> {
            JwtProvider(properties.copy(secret = "short-secret"))
        }
    }

    private fun parse(token: String): Claims =
        Jwts.parser()
            .verifyWith(Keys.hmacShaKeyFor(properties.secret.toByteArray()))
            .build()
            .parseSignedClaims(token)
            .payload
}
