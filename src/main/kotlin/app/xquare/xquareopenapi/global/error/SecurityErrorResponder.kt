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

    // 조회 API에 API 키가 없거나 틀리면 호출된다. 조회 API용 필터 체인의 entry point로 쓴다.
    // 키는 맞지만 만료돼 인증하지 않은 요청(API_KEY_EXPIRED_ATTRIBUTE가 true)이면 API_KEY_EXPIRED로 구분한다.
    val invalidApiKey = AuthenticationEntryPoint { request, response, _ ->
        val expired = request.getAttribute(API_KEY_EXPIRED_ATTRIBUTE) == true
        write(response, if (expired) ErrorCode.API_KEY_EXPIRED else ErrorCode.INVALID_API_KEY)
    }

    // 조회 API에 유효한 키를 보냈지만 그 경로의 분야가 승인되지 않았으면 호출된다. 조회 API용 필터 체인의 access denied handler로 쓴다.
    val apiKeyFieldForbidden = AccessDeniedHandler { _, response, _ -> write(response, ErrorCode.API_KEY_FIELD_FORBIDDEN) }

    // 인증은 됐지만 권한이 부족하면 호출된다.
    override fun handle(request: HttpServletRequest, response: HttpServletResponse, e: AccessDeniedException) =
        write(response, ErrorCode.FORBIDDEN)

    private fun write(response: HttpServletResponse, errorCode: ErrorCode) {
        response.status = errorCode.status.value()
        response.contentType = MediaType.APPLICATION_JSON_VALUE
        response.characterEncoding = Charsets.UTF_8.name()
        response.writer.write(jsonMapper.writeValueAsString(ErrorResponse(errorCode.name, errorCode.message)))
    }

    companion object {
        // ApiKeyAuthenticationFilter가 만료된 키를 만나면 요청에 이 속성을 true로 남긴다.
        const val API_KEY_EXPIRED_ATTRIBUTE = "xquare.apiKey.expired"
    }
}
