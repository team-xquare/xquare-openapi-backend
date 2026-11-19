package app.xquare.xquareopenapi.domain.apikey

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.filter.OncePerRequestFilter

// X-API-Key 헤더의 키가 발급된 키면 키 주인의 userId와 API_KEY 권한으로 인증한다.
// 키가 없거나 틀리면 인증하지 않고 넘기고, SecurityConfig의 entry point가 401 INVALID_API_KEY로 응답한다.
// 조회 API용 필터 체인에만 들어가므로 키로는 등록·수정·삭제나 키 재발급을 할 수 없다.
class ApiKeyAuthenticationFilter(
    private val apiKeyRepository: ApiKeyRepository,
) : OncePerRequestFilter() {

    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, filterChain: FilterChain) {
        val rawKey = request.getHeader(HEADER)
        if (rawKey != null) {
            val apiKey = apiKeyRepository.findByKeyHash(ApiKeySecret.hash(rawKey))
            if (apiKey != null) {
                SecurityContextHolder.getContext().authentication = UsernamePasswordAuthenticationToken.authenticated(
                    apiKey.userId,
                    null,
                    listOf(SimpleGrantedAuthority(AUTHORITY)),
                )
            }
        }
        filterChain.doFilter(request, response)
    }

    companion object {
        const val HEADER = "X-API-Key"
        const val AUTHORITY = "API_KEY"
    }
}
