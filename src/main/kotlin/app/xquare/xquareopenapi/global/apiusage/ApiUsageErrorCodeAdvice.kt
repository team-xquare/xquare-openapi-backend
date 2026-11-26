package app.xquare.xquareopenapi.global.apiusage

import app.xquare.xquareopenapi.global.error.ErrorResponse
import org.springframework.core.MethodParameter
import org.springframework.http.MediaType
import org.springframework.http.converter.HttpMessageConverter
import org.springframework.http.server.ServerHttpRequest
import org.springframework.http.server.ServerHttpResponse
import org.springframework.http.server.ServletServerHttpRequest
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice

// MVC가 { code, message } 오류 본문을 쓸 때 그 code를 사용 기록용 요청 속성으로 남긴다.
// GlobalExceptionHandler의 처리기마다 넣지 않고 본문을 쓰는 한 곳에서 잡아, 처리기가 늘어도 빠뜨리지 않는다.
// Security 필터에서 막힌 응답은 MVC를 거치지 않으므로 SecurityErrorResponder가 따로 남긴다.
@RestControllerAdvice
class ApiUsageErrorCodeAdvice : ResponseBodyAdvice<Any> {
    override fun supports(returnType: MethodParameter, converterType: Class<out HttpMessageConverter<*>>): Boolean = true

    override fun beforeBodyWrite(
        body: Any?,
        returnType: MethodParameter,
        selectedContentType: MediaType,
        selectedConverterType: Class<out HttpMessageConverter<*>>,
        request: ServerHttpRequest,
        response: ServerHttpResponse,
    ): Any? {
        if (body is ErrorResponse && request is ServletServerHttpRequest) {
            request.servletRequest.setAttribute(ApiUsageAttributes.ERROR_CODE, body.code)
        }
        return body
    }
}
