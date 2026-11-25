package app.xquare.xquareopenapi.domain.apiusage

import app.xquare.xquareopenapi.domain.user.Role
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
        assertTrue(rows.all { row -> row.properties().map { it.key }.toSet() == METADATA_FIELDS })
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
        jdbc.update("delete from api_usage_logs")
        assertEquals(404, send("GET", "/schedules/987654321", admin).statusCode())
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
        assertEquals(404, send("GET", "/api-keys/me", admin).statusCode())
        assertEquals(404, send("GET", "/api-key-applications/987654321", admin).statusCode())
        val rows = awaitRows(2)
        assertEquals(listOf("/api-keys/me", "/api-key-applications/{applicationId}"), rows.map { it["route"] })
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

    private fun seedRoute(route: String, at: String = "2026-11-15 00:00:00", method: String = "GET", status: Int = 200) {
        jdbc.update(
            "insert into api_usage_logs (occurred_at, http_method, route, http_status, duration_ms) values (?, ?, ?, ?, ?)",
            java.sql.Timestamp.valueOf(at), method, route, status, 1L,
        )
    }

    private fun openApiContract(document: JsonNode): Map<String, Any> {
        val operation = document.path("paths").path(LIST).path("get")
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

    private fun send(method: String, path: String, token: String? = null, body: String? = null, contentType: String = "application/json"): HttpResponse<String> =
        CLIENT.send(request(method, path, token, body, contentType), HttpResponse.BodyHandlers.ofString())

    private fun request(method: String, path: String, token: String? = null, body: String? = null, contentType: String = "application/json"): HttpRequest {
        val request = HttpRequest.newBuilder(uri(path)).timeout(Duration.ofSeconds(10))
        token?.let { request.header("Authorization", "Bearer $it") }
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
        private const val SEED_PERIOD = "from=2026-11-15T00:00:00Z&to=2026-11-16T00:00:00Z"
        private val CLIENT = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()
        private val METADATA_FIELDS = setOf("id", "occurredAt", "method", "route", "status", "durationMs")
        private val STORAGE_FIELDS = setOf("id", "occurred_at", "http_method", "route", "http_status", "duration_ms")
        private val SCHEMA_FIELDS = setOf("type", "format", "minimum", "maximum", "default", "minLength", "maxLength", "nullable")
    }
}
