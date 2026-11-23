package app.xquare.xquareopenapi.global.jwt

import app.xquare.xquareopenapi.domain.user.Role
import io.jsonwebtoken.Claims
import io.jsonwebtoken.JwtException
import io.jsonwebtoken.JwtParser
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.springframework.stereotype.Component
import java.time.Duration
import java.util.Base64
import java.util.Date
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.SecretKey

@Component
class JwtProvider(
    private val jwtProperties: JwtProperties,
) {
    private val key: SecretKey = Keys.hmacShaKeyFor(jwtProperties.secret.toByteArray())
    private val parser: JwtParser = Jwts.parser().verifyWith(key).build()

    fun generateAccessToken(userId: Long, role: Role): String =
        generateToken(userId, jwtProperties.accessExpiration, mapOf(TYPE_CLAIM to ACCESS_TYPE, ROLE_CLAIM to role.name))

    // passwordHash는 발급 근거가 된(로그인에서 검증한) 비밀번호 해시다. 재발급 때 지금 해시와 비교해 비밀번호가 바뀐 뒤면 거절한다.
    fun generateRefreshToken(userId: Long, passwordHash: String): RefreshToken =
        RefreshToken(
            value = generateToken(
                userId,
                jwtProperties.refreshExpiration,
                mapOf(TYPE_CLAIM to REFRESH_TYPE, PASSWORD_CLAIM to passwordFingerprint(passwordHash)),
            ),
            ttl = jwtProperties.refreshExpiration,
        )

    // 비밀번호 해시의 HMAC(서명 키 사용). 토큰 본문은 누구나 읽을 수 있으므로 해시를 그대로 넣지 않는다.
    // BCrypt는 매번 salt가 달라 같은 비밀번호로 다시 바꿔도 값이 달라진다.
    fun passwordFingerprint(passwordHash: String): String {
        val mac = Mac.getInstance(key.algorithm)
        mac.init(key)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(passwordHash.toByteArray()))
    }

    // 서명이 틀리거나 만료됐거나 액세스 토큰이 아니거나 sub·role이 올바르지 않으면 null을 돌려준다.
    fun parseAccessToken(token: String): AccessTokenPayload? {
        val claims = parseClaims(token, ACCESS_TYPE) ?: return null
        val userId = claims.subject?.toLongOrNull() ?: return null
        val roleName = claims[ROLE_CLAIM] as? String ?: return null
        val role = try {
            Role.valueOf(roleName)
        } catch (e: IllegalArgumentException) {
            return null
        }
        return AccessTokenPayload(userId, role)
    }

    // 서명이 틀리거나 만료됐거나 리프레시 토큰이 아니거나 sub·비밀번호 지문이 올바르지 않으면 null을 돌려준다.
    // 지문이 없는 토큰(이 검사를 넣기 전에 발급한 것)도 null이라, 그런 토큰을 가진 사용자는 다시 로그인해야 한다.
    fun parseRefreshToken(token: String): RefreshTokenPayload? {
        val claims = parseClaims(token, REFRESH_TYPE) ?: return null
        val userId = claims.subject?.toLongOrNull() ?: return null
        val passwordFingerprint = claims[PASSWORD_CLAIM] as? String ?: return null
        return RefreshTokenPayload(userId, passwordFingerprint)
    }

    private fun parseClaims(token: String, type: String): Claims? {
        val claims = try {
            parser.parseSignedClaims(token).payload
        } catch (e: JwtException) {
            return null
        } catch (e: IllegalArgumentException) {
            return null
        }
        if (claims[TYPE_CLAIM] != type) return null
        return claims
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
        private const val PASSWORD_CLAIM = "pwd"
        private const val ACCESS_TYPE = "access"
        private const val REFRESH_TYPE = "refresh"
    }
}
