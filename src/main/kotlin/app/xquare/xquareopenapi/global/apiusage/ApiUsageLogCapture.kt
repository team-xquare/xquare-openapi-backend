package app.xquare.xquareopenapi.global.apiusage

import app.xquare.xquareopenapi.domain.apiusage.ApiUsageLog
import app.xquare.xquareopenapi.domain.apiusage.UsageAccessLevel
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
import org.springframework.web.util.pattern.PathPatternParser
import java.time.Clock
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.concurrent.TimeUnit

// accessLevels: 조회(GET·HEAD) 경로 패턴과 그 단계. 앞에 있는 패턴이 먼저 맞는다. 맞는 패턴이 없거나 조회가 아니면 단계를 남기지 않는다.
class ApiUsageLogCapture(
    private val clock: Clock,
    private val writer: ApiUsageLogWriter,
    accessLevels: List<Pair<String, UsageAccessLevel>>,
) : OncePerRequestFilter(), ServletRequestListener {
    private val accessLevelPatterns = accessLevels.map { (path, level) -> PathPatternParser.defaultInstance.parse(path) to level }

    override fun shouldNotFilter(request: HttpServletRequest): Boolean {
        if (CorsUtils.isPreFlightRequest(request)) return true
        val path = try {
            // MVC와 같은 파서로 범위만 고른다. 요청 속성에 캐시하거나 세그먼트 값을 저장하지 않는다.
            ServletRequestPathUtils.parse(request).pathWithinApplication()
        } catch (_: IllegalArgumentException) {
            return true
        }
        val segments = path.elements().filterIsInstance<PathContainer.PathSegment>().map { it.valueToMatch() }
        val root = segments.firstOrNull()
        // 관리자 경로 중 분야 신청 처리는 업무 호출이라 기록하고, 사용 기록 조회 같은 나머지 관리자 조회는 기록하지 않는다.
        if (root == ADMIN_ROOT) return segments.getOrNull(1) !in BUSINESS_ADMIN_ROOTS
        return root !in BUSINESS_ROOTS
    }

    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, chain: FilterChain) {
        val method = UsageHttpMethod.normalize(request.method)
        val state = Capture(method, System.nanoTime(), response, accessLevelOf(method, request))
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
        writer.submit(
            ApiUsageLog(
                LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC), state.method.name, state.route, status, duration,
                projectId = request.getAttribute(ApiUsageAttributes.PROJECT_ID) as? Long,
                userId = request.getAttribute(ApiUsageAttributes.USER_ID) as? Long,
                keyPrefix = request.getAttribute(ApiUsageAttributes.KEY_PREFIX) as? String,
                errorCode = request.getAttribute(ApiUsageAttributes.ERROR_CODE) as? String,
                accessLevel = state.accessLevel?.name,
            ),
        )
    }

    // 저장된 템플릿이 아니라 요청 경로로 정한다. 인증에서 막혀 템플릿이 UNMAPPED인 호출도 단계로 찾을 수 있다.
    private fun accessLevelOf(method: UsageHttpMethod, request: HttpServletRequest): UsageAccessLevel? {
        if (method != UsageHttpMethod.GET && method != UsageHttpMethod.HEAD) return null
        val path = ServletRequestPathUtils.parse(request).pathWithinApplication()
        return accessLevelPatterns.firstOrNull { (pattern, _) -> pattern.matches(path) }?.second
    }

    private class Capture(
        val method: UsageHttpMethod,
        val startedAt: Long,
        val response: HttpServletResponse,
        val accessLevel: UsageAccessLevel?,
    ) {
        var route: String = "UNMAPPED"
    }

    companion object {
        private const val CAPTURE_ATTRIBUTE = "xquare.apiUsage.capture"
        private const val MAX_ROUTE_LENGTH = 160
        private const val ADMIN_ROOT = "admin"
        private val BUSINESS_ADMIN_ROOTS = setOf("api-applications")
        private val BUSINESS_ROOTS = setOf("auth", "users", "students", "teachers", "clubs", "meals", "notices", "study-supervisors", "timetables", "schedules", "projects")
    }
}
