package app.xquare.xquareopenapi.domain.apikey

import app.xquare.xquareopenapi.config.SecurityConfig
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.server.PathContainer
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping
import org.springframework.web.util.pattern.PathPatternParser
import kotlin.test.assertEquals

// 조회 API를 SecurityConfig.API_KEY_READ_PATHS에 빠뜨리면 키 체인이 아니라 JWT 체인으로 조용히 열린다.
// GET으로 받는 매핑을 전부 꺼내서, 키 체인 대상이 아니면서 아래 예외 목록에도 없는 경로가 생기면 실패한다.
@SpringBootTest
class ApiKeyReadEndpointCoverageTest @Autowired constructor(
    @Qualifier("requestMappingHandlerMapping")
    private val handlerMapping: RequestMappingHandlerMapping,
) {

    @Test
    fun `GET 매핑은 모두 API 키 체인 대상이거나 예외 목록에 있다`() {
        val apiKeyPatterns = SecurityConfig.API_KEY_READ_PATHS.keys.map { PathPatternParser.defaultInstance.parse(it) }

        val uncovered = handlerMapping.handlerMethods.keys
            // 메서드 조건이 없는 매핑도 GET을 받는다.
            .filter { it.methodsCondition.methods.let { methods -> methods.isEmpty() || RequestMethod.GET in methods } }
            .flatMap { it.patternValues }
            .filterNot { path -> apiKeyPatterns.any { it.matches(PathContainer.parsePath(path)) } }
            .filterNot { it in NOT_API_KEY_READ_PATHS }
            .sorted()

        assertEquals(emptyList(), uncovered, "API_KEY_READ_PATHS나 NOT_API_KEY_READ_PATHS 중 한 곳에 넣어야 합니다")
    }

    companion object {
        // API 키가 아닌 방식으로 여는 GET. 새 조회 API를 여기에 넣을 때는 키 없이 열어도 되는지 먼저 확인한다.
        private val NOT_API_KEY_READ_PATHS = setOf(
            // 처리되지 않은 오류의 포워드 대상
            "/error",
            // Swagger UI와 API 문서(springdoc). 누구나 볼 수 있다.
            "/swagger-ui.html",
            "/v3/api-docs",
            "/v3/api-docs.yaml",
            "/v3/api-docs/swagger-config",
            // 급식 조회는 키 없이 누구나 연다(공개 단계).
            "/meals",
            "/meals/{mealId}",
            // 로그인한 본인 정보라 JWT로 연다.
            "/users/me",
            // 공지는 분야가 없는 로그인용 조회라 JWT로 연다.
            "/notices",
            "/notices/{noticeId}",
            // 시간표는 공개(PUBLIC) 단계라 키·토큰 없이 연다(명세 #57).
            "/timetables",
            // 관리자 API 사용 기록 조회(#55). ADMIN 액세스 토큰으로 연다.
            "/admin/api-usage-logs",
        )
    }
}
