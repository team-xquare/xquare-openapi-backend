package app.xquare.xquareopenapi.domain.apiusage

import app.xquare.xquareopenapi.domain.apikey.ApiKeySecret
import app.xquare.xquareopenapi.domain.user.Role
import app.xquare.xquareopenapi.global.error.ErrorCode
import app.xquare.xquareopenapi.global.jwt.JwtProvider
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.AppenderBase
import org.junit.jupiter.api.Assertions.assertAll
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.doThrow
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.dao.PessimisticLockingFailureException
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.domain.Specification
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionStatus
import org.springframework.transaction.TransactionSystemException
import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.SafeConstructor
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import javax.sql.DataSource
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = [
        "server.address=127.0.0.1", "spring.datasource.url=jdbc:h2:mem:usage-log-http;MODE=MySQL",
        "spring.datasource.hikari.maximum-pool-size=2", "spring.datasource.hikari.connection-timeout=1000",
        "api-usage-log.queue-capacity=8",
    ],
)
class ApiUsageLogHttpTest @Autowired constructor(
    private val jwtProvider: JwtProvider,
    private val mapper: JsonMapper,
    private val jdbc: JdbcTemplate,
    private val dataSource: DataSource,
) {
    @MockitoSpyBean
    private lateinit var repository: ApiUsageLogRepository

    @MockitoSpyBean
    private lateinit var transactionManager: PlatformTransactionManager

    @LocalServerPort
    private var port = 0

    @Test
    fun `정상 경로와 동등한 percent 인코딩 경로는 같은 템플릿으로 각각 한 번 기록한다`() {
        jdbc.update("delete from api_usage_logs")
        for (path in listOf("/meals", "/%6Deals")) {
            assertEquals(200, send("GET", path).statusCode())
        }
        val rows = awaitRows(2)
        assertEquals(listOf("/meals", "/meals"), rows.map { it["route"] })
        assertTrue(rows.all { it["http_status"] == 200 })
    }

    @Test
    fun `기록 INSERT가 대기해도 마지막 업무 연결에서 급식을 읽고 HTTP 응답을 완료한다`() {
        jdbc.update("delete from api_usage_logs")
        jdbc.update(
            "insert into meals (meal_date, breakfast, lunch, dinner, edited_by_admin) values (?, ?, ?, ?, ?)",
            LocalDate.parse("2026-11-20"), "[]", "[\"격리 테스트 메뉴\"]", "[]", true,
        )
        try {
            withBlockedInsert { entered, release ->
                // 업무 풀은 2개입니다. 1개를 잡고 DB를 쓰지 않는 요청으로 기록 INSERT만 먼저 시작합니다.
                dataSource.connection.use {
                    assertCode(send("GET", "/users/me"), 401, "INVALID_TOKEN")
                    assertTrue(entered.await(5, TimeUnit.SECONDS), "기록 워커가 DB의 INSERT에 도달해야 합니다")
                    val response = send("GET", "/meals?startDate=2026-11-20&finishDate=2026-11-20")
                    assertEquals(200, response.statusCode(), "기록을 해제하기 전에 업무 DB 조회가 성공해야 합니다")
                    assertEquals("격리 테스트 메뉴", json(response).path("meals").get(0).path("lunch").get(0).asString())
                    assertEquals(0, jdbc.queryForObject("select count(*) from api_usage_logs", Int::class.java))
                }
                release.countDown()
                assertEquals(listOf(401, 200), awaitRows(2).map { it["http_status"] })
            }
        } finally {
            jdbc.update("delete from meals where meal_date = ?", LocalDate.parse("2026-11-20"))
        }
    }

    @Test
    fun `기록이 대기하는 동안 HTTP 응답과 큐 포화 신호를 받는다`() {
        jdbc.update("delete from api_usage_logs")
        withBlockedInsert { entered, release ->
            observeSignals { events ->
                val response = CLIENT.sendAsync(request("GET", "/users/me"), HttpResponse.BodyHandlers.ofString())
                assertTrue(entered.await(5, TimeUnit.SECONDS), "별도 기록 시도가 시작되어야 한다")
                assertCode(response.get(3, TimeUnit.SECONDS), 401, "INVALID_TOKEN")
                repeat(9) {
                    assertCode(CLIENT.sendAsync(request("GET", "/users/me"), HttpResponse.BodyHandlers.ofString())
                        .get(3, TimeUnit.SECONDS), 401, "INVALID_TOKEN")
                }
                assertEquals(0, jdbc.queryForObject("select count(*) from api_usage_logs", Int::class.java))
                assertSignal(events, "API_USAGE_LOG_QUEUE_FULL dropped=1")
                release.countDown()
                val rows = awaitRows(9)
                assertTrue(rows.all { it["http_status"] == 401 && it["route"] == "UNMAPPED" })
                assertEquals(9, rows.size)
            }
        }
    }

    @Test
    fun `조회 연결을 얻지 못해도 오류 형식을 지키고 잘못된 기간은 먼저 거절한다`() {
        val admin = jwtProvider.generateAccessToken(1L, Role.ADMIN)
        dataSource.connection.use {
            dataSource.connection.use {
                assertAll(
                    { assertCode(send("GET", "$LIST?from=2026-11-15T00:00:00Z&to=2026-11-14T00:00:00Z", admin), 400, "INVALID_INPUT") },
                    {
                        val unavailable = send("GET", LIST, admin)
                        assertCode(unavailable, 500, "INTERNAL_SERVER_ERROR")
                        assertEquals(setOf("code", "message"), json(unavailable).properties().map { it.key }.toSet())
                    },
                )
            }
        }
        assertEquals(200, send("GET", LIST, admin).statusCode())
    }

    @Test
    fun `조회 커밋 경계의 트랜잭션 실패는 기존 500 오류 형식을 사용한다`() {
        val admin = jwtProvider.generateAccessToken(1L, Role.ADMIN)
        doAnswer { call ->
            call.callRealMethod()
            throw TransactionSystemException("synthetic-commit-failure")
        }.`when`(transactionManager).commit(any(TransactionStatus::class.java))

        val response = send("GET", LIST, admin)
        assertCode(response, 500, "INTERNAL_SERVER_ERROR")
        assertEquals(setOf("code", "message"), json(response).properties().map { it.key }.toSet())
        assertFalse("synthetic-commit-failure" in response.body())
    }

    @Test
    fun `조회 잠금 충돌은 기존 409 오류 형식을 유지한다`() {
        val admin = jwtProvider.generateAccessToken(1L, Role.ADMIN)
        doThrow(PessimisticLockingFailureException("synthetic-lock-failure")).`when`(repository).findAll(
            any<Specification<ApiUsageLog>>() ?: Specification.unrestricted(),
            any(Pageable::class.java) ?: Pageable.unpaged(),
        )
        val response = send("GET", LIST, admin)
        assertCode(response, 409, "CONCURRENT_UPDATE")
        assertEquals(setOf("code", "message"), json(response).properties().map { it.key }.toSet())
    }

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
        assertEquals(404, send("GET", "/notices/unmapped/private-path-marker", admin).statusCode())
        assertEquals(404, send("GET", "/notices/876543210", admin).statusCode())

        awaitRows(6)
        val result = json(send("GET", LIST, admin))
        assertEquals(6, result.path("totalCount").asInt())
        val rows = result.path("content").toList()
        assertEquals(listOf(404, 404, 415, 403, 401, 200), rows.map { it.path("status").asInt() })
        assertEquals("/meals", rows.last().path("route").asString())
        assertEquals("/notices/{noticeId}", rows.first().path("route").asString())
        assertEquals("UNMAPPED", rows[1].path("route").asString())
        assertEquals("UNMAPPED", rows[4].path("route").asString())
        assertTrue(rows.all { it.path("durationMs").asLong() >= 0 && it.path("occurredAt").asString().endsWith("Z") })
        assertTrue(rows.all { row -> row.properties().map { it.key }.toSet() == LIST_FIELDS })
        val storedRows = jdbc.queryForList("select * from api_usage_logs")
        assertEquals(6, storedRows.size)
        assertEquals(STORAGE_FIELDS, storedRows.first().keys.map { it.lowercase() }.toSet())
        for (marker in listOf("123456789", "876543210", "synthetic-query", "synthetic-body", "private-path-marker", admin, user)) {
            assertFalse(marker in result.toString())
            assertFalse(marker in storedRows.toString())
        }

        val byTemplate = json(send("GET", "$LIST?api=%7BnoticeId%7D&method=GET", admin))
        assertEquals(listOf("/notices/{noticeId}"), byTemplate.path("content").values().map { it.path("route").asString() })
        for (marker in listOf("876543210", "synthetic-query", "private-path-marker", "UNMAPPED")) {
            assertEquals(0, json(send("GET", "$LIST?api=$marker", admin)).path("totalCount").asInt(), marker)
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
        assertCode(send("GET", "$LIST?api=meals"), 401, "INVALID_TOKEN")
        assertCode(send("GET", "$LIST?api=meals", user), 403, "FORBIDDEN")
        assertEquals(403, send("HEAD", "$LIST?api=meals", user).statusCode())
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

        val justAfterStart = "2026-11-15T00:00:00.000000100Z"
        val includesStart = "from=2026-11-15T00:00:00Z&to=$justAfterStart&method=GET&status=200"
        val excludesStart = "from=$justAfterStart&to=2026-11-16T00:00:00Z&method=GET&status=200"
        assertEquals(2, json(send("GET", "$LIST?$includesStart", admin)).path("totalCount").asInt())
        assertEquals(0, json(send("GET", "$LIST?$excludesStart", admin)).path("totalCount").asInt())
    }

    @Test
    fun `API 검색을 기존 필터 및 페이지에 적용하고 생략하면 기존 결과를 유지한다`() {
        val admin = jwtProvider.generateAccessToken(1L, Role.ADMIN)
        jdbc.update("delete from api_usage_logs")
        seedRoute("/meals")
        seedRoute("/meals/{mealId}", at = "2026-11-15 12:00:00")
        seedRoute("/meals/{mealId}/fixture", at = "2026-11-15 12:00:00")
        seedRoute("/meals", method = "POST")
        seedRoute("/meals", status = 401)
        seedRoute("/meals", at = "2026-11-16 00:00:00")
        seedRoute("/notices")
        val query = "$SEED_PERIOD&method=GET&status=200&size=1"
        val pages = (0..2).map { page -> json(send("GET", "$LIST?$query&api=meals&page=$page", admin)) }
        pages.forEach {
            assertEquals(3, it.path("totalCount").asInt())
            assertEquals(3, it.path("totalPage").asInt())
        }
        assertEquals(
            listOf("/meals/{mealId}/fixture", "/meals/{mealId}", "/meals"),
            pages.map { it.path("content").get(0).path("route").asString() },
        )
        assertEquals(4, json(send("GET", "$LIST?$query", admin)).path("totalCount").asInt())
    }

    @Test
    fun `분야는 저장된 템플릿을 다른 필터와 함께 평가한 뒤 페이지에 적용한다`() {
        val admin = jwtProvider.generateAccessToken(1L, Role.ADMIN)
        jdbc.update("delete from api_usage_logs")
        seedRoute("/students", status = 404)
        seedRoute("/students/{studentId}", status = 404)
        seedRoute("/students/{studentId}", method = "PATCH", status = 404)
        seedRoute("/students/{studentId}")
        seedRoute("/students/{studentId}", at = "2026-11-16 00:00:00", status = 404)
        seedRoute("/students/{studentId}/unknown", status = 404)
        seedRoute("/students/12345", status = 404)
        seedRoute("/students-extra", status = 404)
        seedRoute("/STUDENTS", status = 404)
        seedRoute("/teachers", status = 404)
        seedRoute("UNMAPPED", status = 404)

        val query = "$SEED_PERIOD&field=STUDENT&method=GET&status=404&api=students&size=1"
        val pages = (0..2).map { page -> json(send("GET", "$LIST?$query&page=$page", admin)) }
        pages.forEach {
            assertEquals(2, it.path("totalCount").asInt())
            assertEquals(2, it.path("totalPage").asInt())
        }
        assertEquals(listOf("/students/{studentId}", "/students"), pages.take(2).map { it.path("content").get(0).path("route").asString() })
        assertTrue(pages.last().path("content").isEmpty)
        assertEquals(0, json(send("GET", "$LIST?$SEED_PERIOD&field=STUDENT&api=teachers", admin)).path("totalCount").asInt())
    }

    @Test
    fun `기존 분야만 선택할 수 있고 미분류와 공개 급식은 필터 생략 시 남는다`() {
        val admin = jwtProvider.generateAccessToken(1L, Role.ADMIN)
        val user = jwtProvider.generateAccessToken(2L, Role.USER)
        jdbc.update("delete from api_usage_logs")
        val classified = mapOf(
            "STUDENT" to listOf("/students", "/students/{studentId}"),
            "TEACHER" to listOf("/teachers", "/teachers/{teacherId}"),
            "CLUB" to listOf("/clubs", "/clubs/{clubId}"),
            "SCHEDULE" to listOf("/schedules", "/schedules/{scheduleId}"),
            "STUDY_SUPERVISOR" to listOf("/study-supervisors", "/study-supervisors/day", "/study-supervisors/{studySupervisorId}"),
        )
        val unclassified = listOf("/meals", "/meals/{mealId}", "/notices", "/timetables", "UNMAPPED")
        val allRoutes = classified.values.flatten() + unclassified
        allRoutes.forEach { seedRoute(it) }
        fun routes(filter: String) = json(send("GET", "$LIST?$SEED_PERIOD$filter", admin))
            .path("content").values().map { it.path("route").asString() }
        assertEquals(allRoutes.reversed(), routes(""))
        for ((field, expected) in classified) assertEquals(expected.reversed(), routes("&field=$field"), field)
        assertEquals(listOf("/meals/{mealId}", "/meals"), routes("&api=meals"))
        assertCode(send("GET", "$LIST?field=STUDENT"), 401, "INVALID_TOKEN")
        assertCode(send("GET", "$LIST?field=STUDENT", user), 403, "FORBIDDEN")
        for (invalid in listOf("MEAL", "UNMAPPED", "", "student", "%20", "%20STUDENT")) {
            assertCode(send("GET", "$LIST?field=$invalid", admin), 400, "INVALID_INPUT")
        }
    }

    @Test
    fun `API 검색의 퍼센트 밑줄 느낌표는 글자 그대로 비교한다`() {
        val admin = jwtProvider.generateAccessToken(1L, Role.ADMIN)
        jdbc.update("delete from api_usage_logs")
        listOf("/fixture/a%b", "/fixture/a_b", "/fixture/a!b", "/fixture/axb", "/fixture/ab").forEach { seedRoute(it) }
        for ((encoded, route) in listOf("a%25b" to "a%b", "a_b" to "a_b", "a%21b" to "a!b")) {
            val result = json(send("GET", "$LIST?$SEED_PERIOD&api=$encoded", admin))
            assertEquals(listOf("/fixture/$route"), result.path("content").values().map { it.path("route").asString() })
        }
    }

    @Test
    fun `API 검색은 빈 문자열 공백 및 160자 초과를 조회 연결 없이 거절한다`() {
        val admin = jwtProvider.generateAccessToken(1L, Role.ADMIN)
        dataSource.connection.use {
            dataSource.connection.use {
                for (term in listOf("", "%20%09", "a".repeat(161), "%20".repeat(160) + "a")) {
                    assertCode(send("GET", "$LIST?api=$term", admin), 400, "INVALID_INPUT")
                }
            }
        }
        jdbc.update("delete from api_usage_logs")
        val longest = "/" + "a".repeat(159)
        seedRoute(longest)
        for (term in listOf("a", longest)) {
            val result = json(send("GET", "$LIST?$SEED_PERIOD&api=$term", admin))
            assertEquals(listOf(longest), result.path("content").values().map { it.path("route").asString() })
        }
    }

    @Test
    fun `일정 조회도 업무 호출로 기록한다`() {
        val admin = jwtProvider.generateAccessToken(1L, Role.ADMIN)
        assertEquals(200, send("GET", LIST, admin).statusCode())
        // 일정 조회는 SCHEDULE 분야가 승인된 API 키로 연다.
        jdbc.update("delete from api_project_fields")
        jdbc.update("delete from api_projects")
        val now = LocalDateTime.of(2026, 11, 24, 10, 0)
        jdbc.update(
            "insert into api_projects (user_id, name, purpose, key_hash, issued_at, created_at) values (?, ?, ?, ?, ?, ?)",
            1L, "기록 확인", "목적", ApiKeySecret.hash(API_KEY), now, now,
        )
        val projectId = jdbc.queryForObject("select id from api_projects", Long::class.java)
        jdbc.update(
            "insert into api_project_fields (project_id, field, status, applied_at) values (?, 'SCHEDULE', 'APPROVED', ?)",
            projectId, now,
        )
        jdbc.update("delete from api_usage_logs")
        assertEquals(404, send("GET", "/schedules/987654321", apiKey = API_KEY).statusCode())
        assertEquals(200, send("GET", "/meals").statusCode())
        assertEquals(401, send("GET", "/students/987654321").statusCode())
        val rows = awaitRows(3)
        assertEquals(listOf("/schedules/{scheduleId}", "/meals", "UNMAPPED"), rows.map { it["route"] })
        assertTrue(rows.all { it["http_method"] == "GET" })
        val schedules = json(send("GET", "$LIST?field=SCHEDULE&status=404", admin)).path("content").values()
        assertEquals(listOf("/schedules/{scheduleId}"), schedules.map { it.path("route").asString() })
        assertEquals(0, json(send("GET", "$LIST?field=STUDENT&status=401", admin)).path("totalCount").asInt())
        assertEquals(1, json(send("GET", "$LIST?status=401", admin)).path("totalCount").asInt())
        assertEquals(1, json(send("GET", "$LIST?api=meals", admin)).path("totalCount").asInt())
    }

    @Test
    fun `API 키 관리 호출도 업무 호출로 기록한다`() {
        val admin = jwtProvider.generateAccessToken(1L, Role.ADMIN)
        assertEquals(200, send("GET", LIST, admin).statusCode())
        jdbc.update("delete from api_usage_logs")
        val body = """{"fields":["CLUB"],"emailVerifiedToken":"unknown-token"}"""
        assertEquals(404, send("POST", "/projects/987654321/fields", admin, body).statusCode())
        // 관리자 경로 중 분야 신청 처리는 업무 호출로 기록한다(관리자 사용 기록 조회는 기록하지 않음).
        assertEquals(404, send("GET", "/admin/api-applications/987654321", admin).statusCode())
        val rows = awaitRows(2)
        assertEquals(listOf("/projects/{projectId}/fields", "/admin/api-applications/{applicationId}"), rows.map { it["route"] })
    }

    @Test
    fun `문서 헬스 프리플라이트 및 관리자 조회는 기록하지 않는다`() {
        val admin = jwtProvider.generateAccessToken(1L, Role.ADMIN)
        assertEquals(200, send("GET", LIST, admin).statusCode())
        jdbc.update("delete from api_usage_logs")
        assertEquals(200, send("GET", "/v3/api-docs").statusCode())
        assertEquals(200, send("GET", "/swagger-ui/index.html").statusCode())
        send("GET", "/actuator/health")
        val preflight = HttpRequest.newBuilder(uri("/students"))
            .header("Origin", "http://localhost:3000")
            .header("Access-Control-Request-Method", "GET")
            .method("OPTIONS", HttpRequest.BodyPublishers.noBody()).build()
        CLIENT.send(preflight, HttpResponse.BodyHandlers.ofString())
        assertEquals(200, send("GET", "$LIST?api=synthetic-search-term", admin).statusCode())
        assertEquals(200, send("GET", "/meals").statusCode())
        val rows = awaitRows(1)
        assertEquals(listOf("/meals"), rows.map { it["route"] })
    }

    @Test
    fun `실제 생성 OpenAPI와 정적 명세의 관리자 조회 계약이 같다`() {
        val generated = json(send("GET", "/v3/api-docs"))
        val options = LoaderOptions().apply { maxAliasesForCollections = 512 }
        val static = Files.newBufferedReader(Path.of("docs/openapi.yaml")).use {
            mapper.valueToTree<JsonNode>(Yaml(SafeConstructor(options)).load<Any>(it))
        }
        assertEquals(openApiContract(static), openApiContract(generated))
        assertEquals(openApiContract(static, DETAIL), openApiContract(generated, DETAIL))
        val api = generated.path("paths").path(LIST).path("get").path("parameters").single { it.path("name").asString() == "api" }
        assertEquals("query", api.path("in").asString())
        assertFalse(api.path("required").asBoolean(false))
        assertEquals(1, api.path("schema").path("minLength").asInt())
        assertEquals(160, api.path("schema").path("maxLength").asInt())
        val field = generated.path("paths").path(LIST).path("get").path("parameters").single { it.path("name").asString() == "field" }
        assertEquals("query", field.path("in").asString())
        assertFalse(field.path("required").asBoolean(false))
        assertEquals(setOf("STUDENT", "TEACHER", "CLUB", "SCHEDULE", "STUDY_SUPERVISOR"), field.path("schema").path("enum").values().map { it.asString() }.toSet())
        for (name in listOf("Student", "Teacher", "Club")) {
            val schema = generated.path("components").path("schemas").path("PageResponse${name}Response")
            assertFalse(schema.isMissingNode, "$name 목록의 기존 페이지 스키마가 있어야 합니다")
            assertFalse(schema.has("required"), "$name 목록에 기록 전용 required 선언을 적용하면 안 됩니다")
            assertEquals("int64", schema.path("properties").path("totalCount").path("format").asString())
        }
    }

    @Test
    fun `기록 테이블 접근에 실패해도 업무 응답은 유지된다`() {
        val admin = jwtProvider.generateAccessToken(1L, Role.ADMIN)
        assertEquals(200, send("GET", LIST, admin).statusCode())
        jdbc.execute("alter table api_usage_logs rename to api_usage_logs_test_unavailable")
        try {
            observeSignals { events ->
                assertEquals(200, send("GET", "/meals?unused=synthetic-query").statusCode())
                assertCode(send("GET", LIST, admin), 500, "INTERNAL_SERVER_ERROR")
                assertSignal(events, "API_USAGE_LOG_WRITE_FAILED")
            }
        } finally {
            jdbc.execute("alter table api_usage_logs_test_unavailable rename to api_usage_logs")
        }
        jdbc.update("delete from api_usage_logs")
        assertEquals(200, send("GET", "/meals").statusCode())
        assertEquals(listOf("/meals"), awaitRows(1).map { it["route"] })
    }

    @Test
    fun `API 키 호출은 프로젝트와 신청자 키 접두사 단계를 남기고 키 원문은 남기지 않는다`() {
        val admin = jwtProvider.generateAccessToken(1L, Role.ADMIN)
        assertEquals(200, send("GET", LIST, admin).statusCode())
        jdbc.update("delete from api_project_fields")
        jdbc.update("delete from api_projects")
        val key = ApiKeySecret.generate()
        val expiredKey = ApiKeySecret.generate()
        val projectId = seedProject(ownerId = 31L, name = "기록 상세", rawKey = key, fields = listOf("SCHEDULE", "STUDENT"))
        val expiredId = seedProject(ownerId = 32L, name = "만료 키", rawKey = expiredKey, fields = listOf("SCHEDULE"), expired = true)
        val scheduleOnly = ApiKeySecret.generate()
        val scheduleOnlyId = seedProject(ownerId = 33L, name = "일정만", rawKey = scheduleOnly, fields = listOf("SCHEDULE"))
        jdbc.update("delete from api_usage_logs")

        assertEquals(200, send("GET", "/schedules?startDate=2026-11-01&finishDate=2026-11-30", apiKey = key).statusCode())
        assertEquals(200, send("GET", "/students", apiKey = key).statusCode())
        assertEquals(200, send("HEAD", "/students", apiKey = key).statusCode())
        assertCode(send("GET", "/students/987654321", apiKey = scheduleOnly), 403, "API_KEY_FIELD_FORBIDDEN")
        assertCode(send("GET", "/students", apiKey = "xq_not-a-registered-key"), 401, "INVALID_API_KEY")
        assertCode(send("GET", "/schedules?startDate=2026-11-01&finishDate=2026-11-30", apiKey = expiredKey), 401, "API_KEY_EXPIRED")
        assertCode(send("GET", "/schedules/987654321", apiKey = key), 404, "SCHEDULE_NOT_FOUND")
        // 토큰만 보내면 키 체인이 401로 막고 JWT 필터는 돌지 않아 사용자를 남기지 않는다.
        assertCode(send("GET", "/students", jwtProvider.generateAccessToken(9L, Role.USER)), 401, "INVALID_API_KEY")

        val rows = awaitRows(8)
        fun row(i: Int) = with(rows[i]) {
            listOf(this["route"], this["http_status"], this["project_id"], this["user_id"], this["key_prefix"], this["error_code"], this["access_level"])
        }
        val prefix = key.take(11)
        assertEquals(listOf("/schedules", 200, projectId, 31L, prefix, null, "SCHOOL"), row(0))
        assertEquals(listOf("/students", 200, projectId, 31L, prefix, null, "RESTRICTED"), row(1))
        assertEquals(listOf("/students", 200, projectId, 31L, prefix, null, "RESTRICTED"), row(2))
        assertEquals(listOf("UNMAPPED", 403, scheduleOnlyId, 33L, scheduleOnly.take(11), "API_KEY_FIELD_FORBIDDEN", "RESTRICTED"), row(3))
        assertEquals(listOf("UNMAPPED", 401, null, null, null, "INVALID_API_KEY", "RESTRICTED"), row(4))
        assertEquals(listOf("UNMAPPED", 401, expiredId, 32L, expiredKey.take(11), "API_KEY_EXPIRED", "SCHOOL"), row(5))
        assertEquals(listOf("/schedules/{scheduleId}", 404, projectId, 31L, prefix, "SCHEDULE_NOT_FOUND", "SCHOOL"), row(6))
        assertEquals(listOf("UNMAPPED", 401, null, null, null, "INVALID_API_KEY", "RESTRICTED"), row(7))
        val stored = rows.toString()
        for (secret in listOf(key, expiredKey, scheduleOnly, ApiKeySecret.hash(key), "xq_not-a-registered-key", "xq_not-a-r")) {
            assertFalse(secret in stored, secret)
        }
        assertTrue(key.length > 11)
    }

    @Test
    fun `JWT 호출은 로그인한 사용자를 남기고 공개 조회는 PUBLIC 단계로 남긴다`() {
        val admin = jwtProvider.generateAccessToken(1L, Role.ADMIN)
        val user = jwtProvider.generateAccessToken(42L, Role.USER)
        assertEquals(200, send("GET", LIST, admin).statusCode())
        jdbc.update("delete from api_usage_logs")

        assertEquals(200, send("GET", "/notices", user).statusCode())
        assertCode(send("GET", "/notices/876543210", user), 404, "NOTICE_NOT_FOUND")
        assertCode(send("POST", "/meals", user, "{}"), 403, "FORBIDDEN")
        assertEquals(200, send("GET", "/meals").statusCode())
        assertEquals(200, send("HEAD", "/meals").statusCode())
        assertEquals(200, send("GET", "/meals", user).statusCode())
        assertCode(send("GET", "/meals/not-a-number"), 400, "INVALID_INPUT")
        assertCode(send("GET", "/notices", "invalid-token"), 401, "INVALID_TOKEN")
        assertCode(send("GET", "/meals?startDate=2026-11-01&finishDate=2026-10-01"), 400, "INVALID_DATE_RANGE")

        val rows = awaitRows(9).map { listOf(it["route"], it["user_id"], it["project_id"], it["key_prefix"], it["error_code"], it["access_level"]) }
        assertEquals(
            listOf(
                listOf("/notices", 42L, null, null, null, null),
                listOf("/notices/{noticeId}", 42L, null, null, "NOTICE_NOT_FOUND", null),
                listOf("UNMAPPED", 42L, null, null, "FORBIDDEN", null),
                listOf("/meals", null, null, null, null, "PUBLIC"),
                listOf("/meals", null, null, null, null, "PUBLIC"),
                listOf("/meals", 42L, null, null, null, "PUBLIC"),
                listOf("/meals/{mealId}", null, null, null, "INVALID_INPUT", "PUBLIC"),
                listOf("UNMAPPED", null, null, null, "INVALID_TOKEN", null),
                listOf("/meals", null, null, null, "INVALID_DATE_RANGE", "PUBLIC"),
            ),
            rows,
        )
    }

    @Test
    fun `단계 프로젝트 사용자 오류 코드 필터를 다른 필터와 함께 적용한다`() {
        val admin = jwtProvider.generateAccessToken(1L, Role.ADMIN)
        jdbc.update("delete from api_usage_logs")
        seedDetail("/students", projectId = 7L, userId = 70L, errorCode = null, level = "RESTRICTED")
        seedDetail("/students/{studentId}", projectId = 7L, userId = 70L, errorCode = "STUDENT_NOT_FOUND", level = "RESTRICTED", status = 404)
        seedDetail("/schedules", projectId = 8L, userId = 70L, errorCode = null, level = "SCHOOL")
        seedDetail("UNMAPPED", projectId = null, userId = null, errorCode = "INVALID_API_KEY", level = "RESTRICTED", status = 401)
        seedDetail("/meals", projectId = null, userId = 71L, errorCode = null, level = "PUBLIC")
        seedDetail("/notices", projectId = null, userId = 71L, errorCode = null, level = null)
        seedDetail("/meals", projectId = null, userId = null, errorCode = null, level = "PUBLIC", at = "2026-11-16 00:00:00")

        fun routes(filter: String) = json(send("GET", "$LIST?$SEED_PERIOD$filter", admin)).path("content").values()
            .map { it.path("route").asString() }
        assertEquals(listOf("UNMAPPED", "/students/{studentId}", "/students"), routes("&level=RESTRICTED"))
        assertEquals(listOf("/schedules"), routes("&level=SCHOOL"))
        assertEquals(listOf("/meals"), routes("&level=PUBLIC"))
        assertEquals(listOf("/students/{studentId}", "/students"), routes("&projectId=7"))
        assertEquals(listOf("/students"), routes("&projectId=7&status=200"))
        assertEquals(listOf("/schedules", "/students/{studentId}", "/students"), routes("&userId=70"))
        assertEquals(listOf("/notices", "/meals"), routes("&userId=71"))
        assertEquals(listOf("/meals"), routes("&userId=71&level=PUBLIC"))
        assertEquals(listOf("UNMAPPED"), routes("&errorCode=INVALID_API_KEY"))
        assertEquals(listOf("/students/{studentId}"), routes("&errorCode=STUDENT_NOT_FOUND&level=RESTRICTED&projectId=7&userId=70"))
        assertEquals(emptyList(), routes("&errorCode=STUDENT_NOT_FOUND&projectId=8"))
        assertEquals(emptyList(), routes("&projectId=999"))
        assertEquals(6, json(send("GET", "$LIST?$SEED_PERIOD", admin)).path("totalCount").asInt())

        val first = json(send("GET", "$LIST?$SEED_PERIOD&errorCode=STUDENT_NOT_FOUND", admin)).path("content").get(0)
        assertEquals(LIST_FIELDS, first.properties().map { it.key }.toSet())
        assertEquals(
            listOf(7L, 70L, "STUDENT_NOT_FOUND", "RESTRICTED", "xq_detail01"),
            listOf(
                first.path("projectId").asLong(), first.path("userId").asLong(), first.path("errorCode").asString(),
                first.path("accessLevel").asString(), first.path("keyPrefix").asString(),
            ),
        )
        val anonymous = json(send("GET", "$LIST?$SEED_PERIOD&api=notices", admin)).path("content").get(0)
        for (name in listOf("projectId", "keyPrefix", "errorCode", "accessLevel")) assertTrue(anonymous.path(name).isNull, name)

        for (invalid in listOf(
            "level=", "level=public", "level=MEAL", "level=%20PUBLIC", "errorCode=", "errorCode=NOT_A_CODE",
            "errorCode=invalid_api_key", "projectId=abc", "userId=1.5",
        )) {
            assertCode(send("GET", "$LIST?$invalid", admin), 400, "INVALID_INPUT")
        }
        assertCode(send("GET", "$LIST?level=PUBLIC"), 401, "INVALID_TOKEN")
        assertCode(send("GET", "$LIST?level=PUBLIC", jwtProvider.generateAccessToken(2L, Role.USER)), 403, "FORBIDDEN")
    }

    @Test
    fun `상세는 기록과 프로젝트 이름 신청자 이름 아이디를 돌려주고 삭제된 대상은 null이다`() {
        val admin = jwtProvider.generateAccessToken(1L, Role.ADMIN)
        jdbc.update("delete from api_usage_logs")
        jdbc.update("delete from api_project_fields")
        jdbc.update("delete from api_projects")
        jdbc.update("delete from users where username in ('logowner', 'logother')")
        val ownerId = seedUser("logowner", "기록주인")
        val otherId = seedUser("logother", "다른사람")
        val projectId = seedProject(ownerId = ownerId, name = "상세 프로젝트", rawKey = ApiKeySecret.generate(), fields = emptyList())
        seedDetail("/students/{studentId}", projectId = projectId, userId = ownerId, errorCode = "STUDENT_NOT_FOUND", level = "RESTRICTED", status = 404)
        seedDetail("/notices", projectId = null, userId = otherId, errorCode = null, level = null)
        seedDetail("UNMAPPED", projectId = 987654L, userId = 876543L, errorCode = "API_KEY_EXPIRED", level = "SCHOOL", status = 401)
        val ids = jdbc.queryForList("select id from api_usage_logs order by id", Long::class.java).map { it!! }

        val withProject = send("GET", "$LIST/${ids[0]}", admin)
        assertEquals(200, withProject.statusCode())
        val body = json(withProject)
        assertEquals(DETAIL_FIELDS, body.properties().map { it.key }.toSet())
        assertEquals(
            listOf(
                ids[0], "2026-11-15T00:00:00Z", "GET", "/students/{studentId}", 404, 1L, projectId, "상세 프로젝트",
                ownerId, "기록주인", "logowner", "xq_detail01", "STUDENT_NOT_FOUND", "RESTRICTED",
            ),
            listOf(
                body.path("id").asLong(), body.path("occurredAt").asString(), body.path("method").asString(), body.path("route").asString(),
                body.path("status").asInt(), body.path("durationMs").asLong(), body.path("projectId").asLong(), body.path("projectName").asString(),
                body.path("userId").asLong(), body.path("applicantName").asString(), body.path("applicantUsername").asString(),
                body.path("keyPrefix").asString(), body.path("errorCode").asString(), body.path("accessLevel").asString(),
            ),
        )

        val jwtCall = json(send("GET", "$LIST/${ids[1]}", admin))
        assertEquals(
            listOf(otherId, "다른사람", "logother"),
            listOf(jwtCall.path("userId").asLong(), jwtCall.path("applicantName").asString(), jwtCall.path("applicantUsername").asString()),
        )
        for (name in listOf("projectId", "projectName", "accessLevel", "errorCode")) assertTrue(jwtCall.path(name).isNull, name)

        val deleted = json(send("GET", "$LIST/${ids[2]}", admin))
        assertEquals(listOf(987654L, 876543L), listOf(deleted.path("projectId").asLong(), deleted.path("userId").asLong()))
        for (name in listOf("projectName", "applicantName", "applicantUsername")) assertTrue(deleted.path(name).isNull, name)

        assertCode(send("GET", "$LIST/${ids.last() + 1000}", admin), 404, "API_USAGE_LOG_NOT_FOUND")
        assertCode(send("GET", "$LIST/abc", admin), 400, "INVALID_INPUT")
        assertCode(send("GET", "$LIST/${ids[0]}"), 401, "INVALID_TOKEN")
        val user = jwtProvider.generateAccessToken(ownerId, Role.USER)
        assertCode(send("GET", "$LIST/${ids[0]}", user), 403, "FORBIDDEN")
        assertEquals(403, send("HEAD", "$LIST/${ids[0]}", user).statusCode())
        assertEquals(200, send("HEAD", "$LIST/${ids[0]}", admin).statusCode())

        // 상세 조회는 관리자 조회라 사용 기록에 남기지 않는다.
        assertEquals(200, send("GET", "/meals").statusCode())
        val rows = awaitRows(4)
        assertEquals(4, rows.size)
        assertEquals("/meals", rows.last()["route"])
        jdbc.update("delete from api_projects")
        jdbc.update("delete from users where username in ('logowner', 'logother')")
    }

    @Test
    fun `오류 코드와 단계 이름은 모두 저장 열 길이 안에 들어간다`() {
        assertEquals(emptyList(), ErrorCode.entries.map { it.name }.filter { it.length > ApiUsageLog.ERROR_CODE_MAX_LENGTH })
        assertEquals(emptyList(), UsageAccessLevel.entries.map { it.name }.filter { it.length > ApiUsageLog.ACCESS_LEVEL_MAX_LENGTH })
    }

    private fun seedProject(ownerId: Long, name: String, rawKey: String, fields: List<String>, expired: Boolean = false): Long {
        val now = LocalDateTime.now()
        val expiresAt = if (expired) now.minusDays(1) else now.plusDays(30)
        jdbc.update(
            "insert into api_projects (user_id, name, purpose, key_hash, issued_at, expires_at, created_at) values (?, ?, ?, ?, ?, ?, ?)",
            ownerId, name, "목적", ApiKeySecret.hash(rawKey), now, expiresAt, now,
        )
        val projectId = jdbc.queryForObject("select id from api_projects where key_hash = ?", Long::class.java, ApiKeySecret.hash(rawKey))!!
        for (field in fields) {
            jdbc.update(
                "insert into api_project_fields (project_id, field, status, applied_at) values (?, ?, 'APPROVED', ?)",
                projectId, field, now,
            )
        }
        return projectId
    }

    private fun seedUser(username: String, name: String): Long {
        jdbc.update(
            "insert into users (username, password, student_number, name, email, role) values (?, ?, ?, ?, ?, 'USER')",
            username, "{noop}unused", 1101, name, "$username@dsm.hs.kr",
        )
        return jdbc.queryForObject("select id from users where username = ?", Long::class.java, username)!!
    }

    private fun seedDetail(
        route: String,
        projectId: Long?,
        userId: Long?,
        errorCode: String?,
        level: String?,
        status: Int = 200,
        at: String = "2026-11-15 00:00:00",
    ) {
        jdbc.update(
            "insert into api_usage_logs (occurred_at, http_method, route, http_status, duration_ms, " +
                "project_id, user_id, key_prefix, error_code, access_level) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
            java.sql.Timestamp.valueOf(at), "GET", route, status, 1L,
            projectId, userId, if (projectId == null) null else "xq_detail01", errorCode, level,
        )
    }

    private fun seedRoute(route: String, at: String = "2026-11-15 00:00:00", method: String = "GET", status: Int = 200) {
        jdbc.update(
            "insert into api_usage_logs (occurred_at, http_method, route, http_status, duration_ms) values (?, ?, ?, ?, ?)",
            java.sql.Timestamp.valueOf(at), method, route, status, 1L,
        )
    }

    private fun openApiContract(document: JsonNode, path: String = LIST): Map<String, Any> {
        val operation = document.path("paths").path(path).path("get")
        assertFalse(operation.isMissingNode, "$path 문서가 있어야 합니다")
        val parameters = operation.path("parameters").values().associate { parameter ->
            parameter.path("name").asString() to mapOf(
                "in" to parameter.path("in").asString(),
                "required" to parameter.path("required").asBoolean(false),
                "schema" to schemaContract(document, parameter.path("schema")),
            )
        }
        val responses = operation.path("responses").properties().associate { (status, response) ->
            status to response.path("content").properties().associate { (mediaType, content) ->
                mediaType to schemaContract(document, content.path("schema"))
            }
        }
        return mapOf(
            "operationId" to operation.path("operationId"), "security" to operation.path("security"),
            "role" to operation.path("x-required-role"), "parameters" to parameters, "responses" to responses,
        )
    }

    private fun schemaContract(document: JsonNode, schema: JsonNode): Map<String, Any> {
        if (schema.has("\$ref")) return schemaContract(document, document.at(schema.path("\$ref").asString().removePrefix("#")))
        assertFalse(schema.isMissingNode, "스키마가 있어야 한다")
        val fields = schema.properties().filter { it.key in SCHEMA_FIELDS }.associate { it.key to it.value }
        val result = fields.toMutableMap<String, Any>()
        if (schema.has("enum")) result["enum"] = schema.path("enum").values().toSet()
        if (schema.has("required")) result["required"] = schema.path("required").values().toSet()
        if (schema.has("items")) result["items"] = schemaContract(document, schema.path("items"))
        if (schema.has("properties")) {
            result["properties"] = schema.path("properties").properties().associate { (name, value) ->
                name to schemaContract(document, value)
            }
        }
        return result
    }

    private fun awaitRows(count: Int): List<Map<String, Any?>> {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        do {
            val rows = jdbc.queryForList("select * from api_usage_logs order by id")
            if (rows.size >= count) return rows
            Thread.sleep(20)
        } while (System.nanoTime() < deadline)
        error("기록 $count 행을 대기했으나 읽지 못했습니다.")
    }

    private fun withBlockedInsert(action: (CountDownLatch, CountDownLatch) -> Unit) {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        blockLogInsert = {
            entered.countDown()
            check(release.await(15, TimeUnit.SECONDS)) { "테스트 INSERT 해제 시간 초과" }
        }
        jdbc.execute("create alias hold_usage_insert for \"${javaClass.name}.holdUsageInsert\"")
        try {
            jdbc.execute("alter table api_usage_logs add constraint hold_usage_check check (hold_usage_insert()) nocheck")
            action(entered, release)
        } finally {
            release.countDown()
            jdbc.execute("alter table api_usage_logs drop constraint if exists hold_usage_check")
            jdbc.execute("drop alias hold_usage_insert")
            blockLogInsert = {}
        }
    }

    private fun observeSignals(action: (LinkedBlockingQueue<ILoggingEvent>) -> Unit) {
        val logger = LoggerFactory.getLogger("app.xquare.xquareopenapi.global.apiusage") as Logger
        val events = LinkedBlockingQueue<ILoggingEvent>()
        val appender = object : AppenderBase<ILoggingEvent>() {
            override fun append(event: ILoggingEvent) {
                events.add(event)
            }
        }.apply { start() }
        logger.addAppender(appender)
        try {
            action(events)
        } finally {
            logger.detachAppender(appender)
            appender.stop()
        }
    }

    private fun assertSignal(events: LinkedBlockingQueue<ILoggingEvent>, message: String) {
        val event = assertNotNull(events.poll(5, TimeUnit.SECONDS), message)
        assertEquals(message, event.formattedMessage)
        assertEquals(null, event.throwableProxy)
    }

    private fun assertCode(response: HttpResponse<String>, status: Int, code: String) {
        assertEquals(status, response.statusCode())
        assertEquals(code, json(response).path("code").asString())
    }

    private fun json(response: HttpResponse<String>): JsonNode = mapper.readTree(response.body())
    private fun uri(path: String) = URI("http://127.0.0.1:$port$path")

    private fun send(
        method: String,
        path: String,
        token: String? = null,
        body: String? = null,
        contentType: String = "application/json",
        apiKey: String? = null,
    ): HttpResponse<String> =
        CLIENT.send(request(method, path, token, body, contentType, apiKey), HttpResponse.BodyHandlers.ofString())

    private fun request(
        method: String,
        path: String,
        token: String? = null,
        body: String? = null,
        contentType: String = "application/json",
        apiKey: String? = null,
    ): HttpRequest {
        val request = HttpRequest.newBuilder(uri(path)).timeout(Duration.ofSeconds(10))
        token?.let { request.header("Authorization", "Bearer $it") }
        apiKey?.let { request.header("X-API-Key", it) }
        val publisher = if (body == null) HttpRequest.BodyPublishers.noBody() else {
            request.header("Content-Type", contentType)
            HttpRequest.BodyPublishers.ofString(body)
        }
        return request.method(method, publisher).build()
    }

    companion object {
        @Volatile
        private var blockLogInsert: () -> Unit = {}

        @JvmStatic
        fun holdUsageInsert(): Boolean {
            blockLogInsert()
            return true
        }

        private const val LIST = "/admin/api-usage-logs"
        private const val DETAIL = "/admin/api-usage-logs/{logId}"
        private const val API_KEY = "xq_usage-log-key"
        private const val SEED_PERIOD = "from=2026-11-15T00:00:00Z&to=2026-11-16T00:00:00Z"
        private val CLIENT = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()
        private val METADATA_FIELDS = setOf("id", "occurredAt", "method", "route", "status", "durationMs")
        private val LIST_FIELDS = METADATA_FIELDS + setOf("projectId", "userId", "keyPrefix", "errorCode", "accessLevel")
        private val DETAIL_FIELDS = LIST_FIELDS + setOf("projectName", "applicantName", "applicantUsername")
        private val STORAGE_FIELDS = setOf(
            "id", "occurred_at", "http_method", "route", "http_status", "duration_ms",
            "project_id", "user_id", "key_prefix", "error_code", "access_level",
        )
        private val SCHEMA_FIELDS = setOf("type", "format", "minimum", "maximum", "default", "minLength", "maxLength", "nullable")
    }
}
