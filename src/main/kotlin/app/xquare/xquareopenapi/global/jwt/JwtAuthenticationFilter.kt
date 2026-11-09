package app.xquare.xquareopenapi.global.jwt

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpHeaders
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.filter.OncePerRequestFilter

// 유효한 액세스 토큰이면 사용자 id와 권한(ROLE_USER, ROLE_ADMIN)으로 인증한다.
// 토큰이 없거나 유효하지 않으면 인증하지 않고 넘기고, 인증이 필요한 경로라면 SecurityConfig의 entry point가 401로 응답한다.
class JwtAuthenticationFilter(
    private val jwtProvider: JwtProvider,
) : OncePerRequestFilter() {

    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, filterChain: FilterChain) {
        val payload = parse(request.getHeader(HttpHeaders.AUTHORIZATION))
        if (payload != null) {
            SecurityContextHolder.getContext().authentication = UsernamePasswordAuthenticationToken.authenticated(
                payload.userId,
                null,
                listOf(SimpleGrantedAuthority("ROLE_${payload.role.name}")),
            )
        }
        filterChain.doFilter(request, response)
    }

    // 인증 scheme 이름은 대소문자를 구분하지 않는다(RFC 9110).
    private fun parse(authorization: String?): AccessTokenPayload? {
        if (authorization == null || !authorization.startsWith(BEARER_PREFIX, ignoreCase = true)) return null
        return jwtProvider.parseAccessToken(authorization.substring(BEARER_PREFIX.length))
    }

    companion object {
        private const val BEARER_PREFIX = "Bearer "
    }
}
