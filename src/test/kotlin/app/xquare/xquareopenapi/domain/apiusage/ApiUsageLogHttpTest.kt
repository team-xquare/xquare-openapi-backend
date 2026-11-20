package app.xquare.xquareopenapi.domain.apiusage

import app.xquare.xquareopenapi.domain.user.Role
import app.xquare.xquareopenapi.global.apiusage.ApiUsageLogCapture
import app.xquare.xquareopenapi.global.jwt.JwtProvider
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.jdbc.core.JdbcTemplate
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = ["server.address=127.0.0.1", "spring.datasource.url=jdbc:h2:mem:usage-log-http;MODE=MySQL"],
)
class ApiUsageLogHttpTest @Autowired constructor(
    private val jwtProvider: JwtProvider,
    private val mapper: JsonMapper,
    private val jdbc: JdbcTemplate,
) {
    @LocalServerPort
    private var port = 0

    @Test
    fun `업무 호출은 성공과 거절 및 오류를 완료 상태로 한 번씩 기록한다`() {
        val admin = jwtProvider.generateAccessToken(1L, Role.ADMIN)
        val user = jwtProvider.generateAccessToken(2L, Role.USER)
        // 조회 API부터 검증하면 기능이 없는 기준선에서도 실제 응답으로 RED를 확인할 수 있다.
        assertEquals(200, send("GET", LIST, admin).statusCode())
        jdbc.update("delete from api_usage_logs")
        assertEquals(200, send("GET", "/meals?unused=synthetic-query", admin).statusCode())
        assertEquals(401, send("GET", "/students/123456789?unused=synthetic-query").statusCode())
        assertEquals(403, send("POST", "/students", user, "{}").statusCode())
        assertEquals(415, send("POST", "/auth/signup", body = "synthetic-body", contentType = "text/plain").statusCode())
        assertEquals(404, send("GET", "/students/unmapped/private-path-marker", admin).statusCode())
        assertEquals(404, send("GET", "/students/876543210", admin).statusCode())

        val result = json(send("GET", LIST, admin))
        assertEquals(6, result.path("totalCount").asInt())
        val rows = result.path("content").toList()
        assertEquals(listOf(404, 404, 415, 403, 401, 200), rows.map { it.path("status").asInt() })
        assertEquals("/meals", rows.last().path("route").asString())
        assertEquals("/students/{studentId}", rows.first().path("route").asString())
        assertEquals("UNMAPPED", rows[1].path("route").asString())
        assertEquals("UNMAPPED", rows[4].path("route").asString())
        assertTrue(rows.all { it.path("durationMs").asLong() >= 0 && it.path("occurredAt").asString().endsWith("Z") })
        assertTrue(rows.all { row -> row.properties().map { it.key }.toSet() == METADATA_FIELDS })
        val storedRows = jdbc.queryForList("select * from api_usage_logs")
        assertEquals(6, storedRows.size)
        assertEquals(STORAGE_FIELDS, storedRows.first().keys.map { it.lowercase() }.toSet())
        for (marker in listOf("123456789", "876543210", "synthetic-query", "synthetic-body", "private-path-marker", admin, user)) {
            assertFalse(marker in result.toString())
            assertFalse(marker in storedRows.toString())
        }
    }

    @Test
    fun `조회와 HEAD는 관리자 JWT만 허용하고 필터는 제한된다`() {
        val admin = jwtProvider.generateAccessToken(1L, Role.ADMIN)
        val user = jwtProvider.generateAccessToken(2L, Role.USER)
        assertCode(send("GET", LIST), 401, "INVALID_TOKEN")
        assertCode(send("GET", LIST, user), 403, "FORBIDDEN")
        assertEquals(401, send("HEAD", LIST).statusCode())
        assertEquals(403, send("HEAD", LIST, user).statusCode())
        assertEquals(200, send("HEAD", LIST, admin).statusCode())
        for (query in listOf("size=101", "page=1001", "method=unknown", "status=99", "from=2026-11-15T00:00:00Z",
            "from=2026-11-15T00:00:00Z&to=2026-11-14T00:00:00Z",
            "from=2026-01-01T00:00:00Z&to=2026-03-01T00:00:00Z")) {
            assertCode(send("GET", "$LIST?$query", admin), 400, "INVALID_INPUT")
        }
    }

    @Test
    fun `관리자 조회는 반열린 기간과 메서드 및 상태를 적용하고 동시간에는 ID 내림차순이다`() {
        val admin = jwtProvider.generateAccessToken(1L, Role.ADMIN)
        assertEquals(200, send("GET", LIST, admin).statusCode())
        jdbc.update("delete from api_usage_logs")
        fun insert(at: String, method: String = "GET", status: Int = 200) {
            jdbc.update(
                "insert into api_usage_logs (occurred_at, http_method, route, http_status, duration_ms) values (?, ?, ?, ?, ?)",
                java.sql.Timestamp.valueOf(at), method, "/meals", status, 1L,
            )
        }
        insert("2026-11-15 00:00:00")
        insert("2026-11-15 00:00:00")
        insert("2026-11-15 00:00:00", "POST")
        insert("2026-11-15 00:00:00", status = 401)
        insert("2026-11-16 00:00:00")
        val query = "from=2026-11-15T00:00:00Z&to=2026-11-16T00:00:00Z&method=GET&status=200&size=1"
        val pages = (0..1).map { page -> json(send("GET", "$LIST?$query&page=$page", admin)) }
        pages.forEach { assertEquals(2, it.path("totalCount").asInt()) }
        assertTrue(pages[0].path("content").get(0).path("id").asLong() > pages[1].path("content").get(0).path("id").asLong())
    }

    @Test
    fun `문서 헬스 프리플라이트는 기록하지 않으며 Swagger는 관리자 JWT를 표시한다`() {
        val admin = jwtProvider.generateAccessToken(1L, Role.ADMIN)
        assertEquals(200, send("GET", LIST, admin).statusCode())
        jdbc.update("delete from api_usage_logs")
        val openApi = json(send("GET", "/v3/api-docs"))
        val operation = openApi.path("paths").path(LIST).path("get")
        assertTrue(operation.path("security").get(0).path("bearerAuth").isArray)
        assertEquals("ADMIN", operation.path("x-required-role").asString())
        assertEquals(setOf("from", "to", "method", "status", "page", "size"), operation.path("parameters").values().map { it.path("name").asString() }.toSet())
        assertEquals(setOf("200", "400", "401", "403", "409", "500"), operation.path("responses").properties().map { it.key }.toSet())
        assertEquals(200, send("GET", "/swagger-ui/index.html").statusCode())
        send("GET", "/actuator/health")
        val preflight = HttpRequest.newBuilder(uri("/students"))
            .header("Origin", "http://localhost:3000")
            .header("Access-Control-Request-Method", "GET")
            .method("OPTIONS", HttpRequest.BodyPublishers.noBody()).build()
        CLIENT.send(preflight, HttpResponse.BodyHandlers.ofString())
        assertEquals(0, json(send("GET", LIST, admin)).path("totalCount").asInt())
    }

    @Test
    fun `기록 테이블 접근에 실패해도 업무 응답은 유지된다`() {
        val admin = jwtProvider.generateAccessToken(1L, Role.ADMIN)
        assertEquals(200, send("GET", LIST, admin).statusCode())
        val logger = LoggerFactory.getLogger(ApiUsageLogCapture::class.java) as Logger
        val events = ListAppender<ILoggingEvent>().apply { start() }
        jdbc.execute("alter table api_usage_logs rename to api_usage_logs_test_unavailable")
        logger.addAppender(events)
        try {
            assertEquals(200, send("GET", "/meals?unused=synthetic-query").statusCode())
            assertCode(send("GET", LIST, admin), 500, "INTERNAL_SERVER_ERROR")
            assertEquals(listOf("API_USAGE_LOG_WRITE_FAILED"), events.list.map { it.formattedMessage })
            assertTrue(events.list.all { it.throwableProxy == null })
        } finally {
            logger.detachAppender(events)
            events.stop()
            jdbc.execute("alter table api_usage_logs_test_unavailable rename to api_usage_logs")
        }
    }

    private fun assertCode(response: HttpResponse<String>, status: Int, code: String) {
        assertEquals(status, response.statusCode())
        assertEquals(code, json(response).path("code").asString())
    }

    private fun json(response: HttpResponse<String>): JsonNode = mapper.readTree(response.body())
    private fun uri(path: String) = URI("http://127.0.0.1:$port$path")

    private fun send(method: String, path: String, token: String? = null, body: String? = null, contentType: String = "application/json"): HttpResponse<String> {
        val request = HttpRequest.newBuilder(uri(path)).timeout(Duration.ofSeconds(10))
        token?.let { request.header("Authorization", "Bearer $it") }
        val publisher = if (body == null) HttpRequest.BodyPublishers.noBody() else {
            request.header("Content-Type", contentType)
            HttpRequest.BodyPublishers.ofString(body)
        }
        return CLIENT.send(request.method(method, publisher).build(), HttpResponse.BodyHandlers.ofString())
    }

    companion object {
        private const val LIST = "/admin/api-usage-logs"
        private val CLIENT = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()
        private val METADATA_FIELDS = setOf("id", "occurredAt", "method", "route", "status", "durationMs")
        private val STORAGE_FIELDS = setOf("id", "occurred_at", "http_method", "route", "http_status", "duration_ms")
    }
}
