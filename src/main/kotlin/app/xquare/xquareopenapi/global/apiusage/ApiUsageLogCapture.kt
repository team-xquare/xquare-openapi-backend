package app.xquare.xquareopenapi.global.apiusage

import app.xquare.xquareopenapi.domain.apiusage.ApiUsageLog
import app.xquare.xquareopenapi.domain.apiusage.UsageHttpMethod
import jakarta.servlet.FilterChain
import jakarta.servlet.ServletRequestEvent
import jakarta.servlet.ServletRequestListener
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.server.PathContainer
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.cors.CorsUtils
import org.springframework.web.filter.OncePerRequestFilter
import org.springframework.web.method.HandlerMethod
import org.springframework.web.servlet.HandlerMapping
import org.springframework.web.util.ServletRequestPathUtils
import java.time.Clock
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.concurrent.TimeUnit

class ApiUsageLogCapture(private val clock: Clock, private val writer: ApiUsageLogWriter) :
    OncePerRequestFilter(), ServletRequestListener {

    override fun shouldNotFilter(request: HttpServletRequest): Boolean {
        if (CorsUtils.isPreFlightRequest(request)) return true
        val path = try {
            // MVC와 같은 파서로 범위만 고른다. 요청 속성에 캐시하거나 세그먼트 값을 저장하지 않는다.
            ServletRequestPathUtils.parse(request).pathWithinApplication()
        } catch (_: IllegalArgumentException) {
            return true
        }
        val root = path.elements().filterIsInstance<PathContainer.PathSegment>().firstOrNull()?.valueToMatch()
        return root !in BUSINESS_ROOTS
    }

    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, chain: FilterChain) {
        val state = Capture(UsageHttpMethod.normalize(request.method), System.nanoTime(), response)
        request.setAttribute(CAPTURE_ATTRIBUTE, state)
        try {
            chain.doFilter(request, response)
        } finally {
            val handler = request.getAttribute(HandlerMapping.BEST_MATCHING_HANDLER_ATTRIBUTE)
            val pattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE)
            // 원본 URL이 아니라 업무 컨트롤러가 선언한 MVC 템플릿만 사용한다.
            if (handler is HandlerMethod && handler.beanType.isAnnotationPresent(RestController::class.java) &&
                pattern is String && pattern.length <= MAX_ROUTE_LENGTH && pattern != "/error"
            ) {
                state.route = pattern
            }
        }
    }

    override fun requestDestroyed(event: ServletRequestEvent) {
        val request = event.servletRequest
        val state = request.getAttribute(CAPTURE_ATTRIBUTE) as? Capture ?: return
        request.removeAttribute(CAPTURE_ATTRIBUTE)
        // Tomcat은 컨테이너 오류 응답 처리를 마친 뒤 이 콜백을 부른다. ERROR 재디스패치에서는 새 행을 만들지 않는다.
        val status = state.response.status
        val duration = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - state.startedAt).coerceAtLeast(0)
        writer.submit(ApiUsageLog(LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC), state.method.name, state.route, status, duration))
    }

    private class Capture(val method: UsageHttpMethod, val startedAt: Long, val response: HttpServletResponse) {
        var route: String = "UNMAPPED"
    }

    companion object {
        private const val CAPTURE_ATTRIBUTE = "xquare.apiUsage.capture"
        private const val MAX_ROUTE_LENGTH = 160
        private val BUSINESS_ROOTS = setOf("auth", "users", "students", "teachers", "clubs", "meals", "notices", "study-supervisors", "timetables")
    }
}
