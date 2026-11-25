package app.xquare.xquareopenapi.domain.apikey

import app.xquare.xquareopenapi.global.error.SecurityErrorResponder
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.filter.OncePerRequestFilter
import java.time.Clock
import java.time.LocalDateTime

// X-API-Key 헤더의 키가 어느 프로젝트의 키면 프로젝트 주인의 userId로, 승인(APPROVED)된 분야마다 ApiKeyField.authority 권한을 주어 인증한다.
// 대기·반려된 분야는 권한을 주지 않는다.
// 키가 없거나 틀리면 인증하지 않고 넘기고 SecurityConfig의 entry point가 401 INVALID_API_KEY로,
// 만료된 키면 요청에 표시만 남기고 인증하지 않아 같은 entry point가 401 API_KEY_EXPIRED로,
// 키는 맞지만 경로의 분야가 승인되지 않았으면 access denied handler가 403 API_KEY_FIELD_FORBIDDEN으로 응답한다.
// 조회 API용 필터 체인에만 들어가므로 키로는 등록·수정·삭제나 키 재발급을 할 수 없다.
class ApiKeyAuthenticationFilter(
    private val projectRepository: ApiProjectRepository,
    private val fieldRepository: ApiProjectFieldRepository,
    private val clock: Clock,
) : OncePerRequestFilter() {

    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, filterChain: FilterChain) {
        // 빈 문자열·공백 헤더는 키가 없는 것과 같게 보고 DB를 조회하지 않는다.
        val rawKey = request.getHeader(HEADER)?.takeIf { it.isNotBlank() }
        val project = rawKey?.let { projectRepository.findByKeyHash(ApiKeySecret.hash(it)) }
        if (project != null) {
            val expiresAt = project.expiresAt
            // 만료 시각이 되는 순간부터 쓸 수 없다.
            if (expiresAt != null && !LocalDateTime.now(clock).isBefore(expiresAt)) {
                request.setAttribute(SecurityErrorResponder.API_KEY_EXPIRED_ATTRIBUTE, true)
            } else {
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
