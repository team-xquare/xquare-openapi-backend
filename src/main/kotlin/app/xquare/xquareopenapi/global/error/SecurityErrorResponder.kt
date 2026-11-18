package app.xquare.xquareopenapi.global.error

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.MediaType
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.core.AuthenticationException
import org.springframework.security.web.AuthenticationEntryPoint
import org.springframework.security.web.access.AccessDeniedHandler
import org.springframework.stereotype.Component
import tools.jackson.databind.json.JsonMapper

// Security 필터에서 막힌 요청은 GlobalExceptionHandler까지 가지 않으므로, 같은 { code, message } 형식으로 여기서 응답한다.
@Component
class SecurityErrorResponder(
    private val jsonMapper: JsonMapper,
) : AuthenticationEntryPoint, AccessDeniedHandler {

    // 토큰이 없거나 유효하지 않은 요청이 인증이 필요한 경로에 오면 호출된다.
    override fun commence(request: HttpServletRequest, response: HttpServletResponse, e: AuthenticationException) =
        write(response, ErrorCode.INVALID_TOKEN)

    // 인증은 됐지만 권한이 부족하면 호출된다.
    override fun handle(request: HttpServletRequest, response: HttpServletResponse, e: AccessDeniedException) =
        write(response, ErrorCode.FORBIDDEN)

    private fun write(response: HttpServletResponse, errorCode: ErrorCode) {
        response.status = errorCode.status.value()
        response.contentType = MediaType.APPLICATION_JSON_VALUE
        response.characterEncoding = Charsets.UTF_8.name()
        response.writer.write(jsonMapper.writeValueAsString(ErrorResponse(errorCode.name, errorCode.message)))
    }
}
