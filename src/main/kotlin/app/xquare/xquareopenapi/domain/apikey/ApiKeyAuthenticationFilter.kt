package app.xquare.xquareopenapi.domain.apikey

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.filter.OncePerRequestFilter

// X-API-Key 헤더의 키가 어느 프로젝트의 키면 프로젝트 주인의 userId로, 승인(APPROVED)된 분야마다 ApiKeyField.authority 권한을 주어 인증한다.
// 대기·반려된 분야는 권한을 주지 않는다.
// 키가 없거나 틀리면 인증하지 않고 넘기고 SecurityConfig의 entry point가 401 INVALID_API_KEY로,
// 키는 맞지만 경로의 분야가 승인되지 않았으면 access denied handler가 403 API_KEY_FIELD_FORBIDDEN으로 응답한다.
// 조회 API용 필터 체인에만 들어가므로 키로는 등록·수정·삭제나 키 재발급을 할 수 없다.
class ApiKeyAuthenticationFilter(
    private val projectRepository: ApiProjectRepository,
    private val fieldRepository: ApiProjectFieldRepository,
) : OncePerRequestFilter() {

    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, filterChain: FilterChain) {
        val rawKey = request.getHeader(HEADER)
        if (rawKey != null) {
            val project = projectRepository.findByKeyHash(ApiKeySecret.hash(rawKey))
            if (project != null) {
                val authorities = fieldRepository.findFieldsByProjectIdAndStatus(project.id!!, ApplicationStatus.APPROVED)
                    .map { SimpleGrantedAuthority(it.authority) }
                SecurityContextHolder.getContext().authentication =
                    UsernamePasswordAuthenticationToken.authenticated(project.userId, null, authorities)
            }
        }
        filterChain.doFilter(request, response)
    }

    companion object {
        const val HEADER = "X-API-Key"
    }
}
