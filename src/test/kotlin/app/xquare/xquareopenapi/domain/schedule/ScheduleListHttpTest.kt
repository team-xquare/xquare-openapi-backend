package app.xquare.xquareopenapi.domain.schedule

import app.xquare.xquareopenapi.domain.apikey.ApiKeyField
import app.xquare.xquareopenapi.domain.apikey.ApiProjectFieldRepository
import app.xquare.xquareopenapi.domain.apikey.ApiProjectRepository
import app.xquare.xquareopenapi.domain.apikey.saveApiKey
import app.xquare.xquareopenapi.domain.user.Role
import app.xquare.xquareopenapi.global.jwt.JwtProvider
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
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
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = ["server.address=127.0.0.1", "spring.datasource.url=jdbc:h2:mem:schedule_list;MODE=MySQL;DB_CLOSE_DELAY=-1"],
)
class ScheduleListHttpTest @Autowired constructor(
    private val scheduleRepository: ScheduleRepository,
    private val projectRepository: ApiProjectRepository,
    private val fieldRepository: ApiProjectFieldRepository,
    private val jwtProvider: JwtProvider,
    private val jsonMapper: JsonMapper,
    @LocalServerPort private val port: Int,
) {
    @BeforeEach
    fun setUp() {
        saveApiKey(projectRepository, fieldRepository, 1L, SCHEDULE_KEY, ApiKeyField.SCHEDULE)
    }

    @AfterEach
    fun tearDown() {
        scheduleRepository.deleteAll()
        fieldRepository.deleteAll()
        projectRepository.deleteAll()
    }

    @Test
    fun `조회 기간에 일정이 없으면 빈 배열을 반환한다`() {
        val response = list()

        assertEquals(200, response.statusCode(), response.body())
        assertEquals(jsonMapper.readTree("[]"), jsonMapper.readTree(response.body()))
    }

    @Test
    fun `양쪽 날짜를 포함하고 날짜와 id 순으로 두 출처를 반환한다`() {
        save("2026-08-31", "기간 전")
        val end = save("2026-09-30", "마지막 날", ScheduleSource.NEIS)
        val first = save("2026-09-01", "체육대회", content = "학교에서 등록")
        val second = save("2026-09-01", "개학", ScheduleSource.NEIS)
        save("2026-10-01", "기간 후", ScheduleSource.NEIS)

        val response = list()
        assertEquals(200, response.statusCode(), response.body())
        val expected = jsonMapper.readTree(
            """[
              {"id":$first,"date":"2026-09-01","title":"체육대회","content":"학교에서 등록","source":"SCHOOL"},
              {"id":$second,"date":"2026-09-01","title":"개학","content":null,"source":"NEIS"},
              {"id":$end,"date":"2026-09-30","title":"마지막 날","content":null,"source":"NEIS"}
            ]""".trimIndent(),
        )
        assertEquals(expected, jsonMapper.readTree(response.body()))
    }

    @Test
    fun `두 날짜 모두 필요하며 형식과 DATE 범위를 검사한다`() {
        for (query in listOf(
            "", "startDate=2026-09-01", "finishDate=2026-09-30",
            "startDate=&finishDate=2026-09-30", "startDate=2026-09-01&finishDate=",
            "startDate=2026-02-30&finishDate=2026-03-01", "startDate=2026-09-01&finishDate=2026-9-1",
            "startDate=2026-09-01&finishDate=2026-09-01T00:00:00Z",
            "startDate=2026-09-01%2B09:00&finishDate=2026-09-30",
            "startDate=0999-12-31&finishDate=1000-01-01", "startDate=9999-12-31&finishDate=%2B10000-01-01",
        )) {
            assertError(list(query), 400, "INVALID_INPUT", query)
        }
        for (date in listOf("1000-01-01", "9999-12-31", "2028-02-29")) {
            assertEquals(200, list("startDate=$date&finishDate=$date").statusCode(), date)
        }
    }

    @Test
    fun `양 끝을 포함해 하루와 92일까지 허용하고 역순과 93일은 거절한다`() {
        val day = "startDate=2026-09-01&finishDate=2026-09-01"
        save("2026-09-01", "하루 일정")
        val sameDay = list(day)
        assertEquals(200, sameDay.statusCode(), sameDay.body())
        assertEquals(1, jsonMapper.readTree(sameDay.body()).size())
        val start = LocalDate.of(2026, 9, 1)
        assertEquals(200, list("startDate=$start&finishDate=${start.plusDays(91)}").statusCode())
        assertError(list("startDate=$start&finishDate=${start.plusDays(92)}"), 400, "INVALID_DATE_RANGE")
        assertError(list("startDate=$start&finishDate=${start.minusDays(1)}"), 400, "INVALID_DATE_RANGE")
    }

    @Test
    fun `500건까지 반환하고 한 건이라도 더 있으면 잘린 성공 대신 400을 반환한다`() {
        val date = LocalDate.of(2026, 9, 1)
        scheduleRepository.saveAll((1..500).map { Schedule(date, "행사 $it", null, ScheduleSource.SCHOOL) })
        val response = list()
        assertEquals(200, response.statusCode(), response.body())
        assertEquals(500, jsonMapper.readTree(response.body()).size())

        save("2026-09-01", "초과 일정", ScheduleSource.NEIS)
        assertError(list(), 400, "SCHEDULE_LIST_LIMIT_EXCEEDED")
    }

    @Test
    fun `목록 GET과 HEAD는 상세 GET과 같이 SCHEDULE 분야가 승인된 API 키로만 연다`() {
        val id = save("2026-09-01", "일정")
        saveApiKey(projectRepository, fieldRepository, 2L, OTHER_KEY, ApiKeyField.STUDENT)
        val listPath = "/schedules?$MONTH"
        for (path in listOf(listPath, "/schedules/$id")) {
            assertError(get(path), 401, "INVALID_API_KEY", path)
            assertError(get(path, jwtProvider.generateAccessToken(2L, Role.ADMIN)), 401, "INVALID_API_KEY", path)
            assertError(get(path, apiKey = "xq_unknown"), 401, "INVALID_API_KEY", path)
            assertError(get(path, apiKey = OTHER_KEY), 403, "API_KEY_FIELD_FORBIDDEN", path)
            assertEquals(200, get(path, apiKey = SCHEDULE_KEY).statusCode(), path)
        }
        assertEquals(401, get(listPath, method = "HEAD").statusCode())
        assertEquals(200, get(listPath, apiKey = SCHEDULE_KEY, method = "HEAD").statusCode())
    }

    @Test
    fun `생성 명세의 일정 목록 계약과 정적 OpenAPI가 일치한다`() {
        val response = get("/v3/api-docs")
        assertEquals(200, response.statusCode(), response.body())
        val generated = jsonMapper.readTree(response.body())
        val options = LoaderOptions().apply { maxAliasesForCollections = 512 }
        val static = Files.newBufferedReader(Path.of("docs/openapi.yaml")).use {
            jsonMapper.valueToTree<JsonNode>(Yaml(SafeConstructor(options)).load<Any>(it))
        }
        assertEquals(contract(static), contract(generated))
        assertEquals(generated.at("/paths/~1schedules~1{scheduleId}/get/security"), generated.at("/paths/~1schedules/get/security"))
    }

    private fun contract(document: JsonNode): Map<String, Any> {
        val operation = document.at("/paths/~1schedules/get")
        assertFalse(operation.isMissingNode, "일정 목록 operation이 있어야 합니다.")
        val parameters = operation.path("parameters").values().associate { parameter ->
            parameter.path("name").asString() to listOf(
                parameter.path("in"), parameter.path("required"), parameter.path("description"),
                schemaContract(document, parameter.path("schema")),
            )
        }
        val responses = operation.path("responses").properties().associate { (code, response) ->
            code to (response.path("description") to response.path("content").properties().associate { (mediaType, content) ->
                mediaType to schemaContract(document, content.path("schema"))
            })
        }
        return mapOf(
            "operationId" to operation.path("operationId"), "security" to operation.path("security"),
            "role" to operation.path("x-required-role"), "description" to operation.path("description"),
            "parameters" to parameters, "responses" to responses,
        )
    }

    private fun schemaContract(document: JsonNode, schema: JsonNode): Map<String, Any> {
        if (schema.has("\$ref")) return schemaContract(document, document.at(schema.path("\$ref").asString().removePrefix("#")))
        assertFalse(schema.isMissingNode, "스키마가 있어야 합니다.")
        val types = schema.path("type").let { if (it.isArray) it.values().toSet() else setOf(it) }
        val fields = schema.properties().filter { it.key in setOf("format", "maxItems") }.associate { it.key to it.value }
        return fields.toMutableMap<String, Any>().apply {
            put("type", types)
            for (key in listOf("required", "enum")) if (schema.has(key)) put(key, schema.path(key).values().toSet())
            if (schema.has("items")) put("items", schemaContract(document, schema.path("items")))
            if (schema.has("properties")) put("properties", schema.path("properties").properties().associate { (name, field) ->
                name to schemaContract(document, field)
            })
        }
    }

    private fun assertError(response: HttpResponse<String>, status: Int, code: String, context: String = "") {
        assertEquals(status, response.statusCode(), "$context: ${response.body()}")
        val error = jsonMapper.readTree(response.body())
        assertEquals(setOf("code", "message"), error.properties().map { it.key }.toSet())
        assertEquals(code, error.path("code").asString(), context)
        assertTrue(error.path("message").asString().isNotBlank())
    }

    private fun save(date: String, title: String, source: ScheduleSource = ScheduleSource.SCHOOL, content: String? = null): Long =
        scheduleRepository.save(Schedule(LocalDate.parse(date), title, content, source)).id!!

    private fun list(query: String = MONTH) = get("/schedules?$query", apiKey = SCHEDULE_KEY)

    private fun get(path: String, token: String? = null, apiKey: String? = null, method: String = "GET"): HttpResponse<String> {
        val request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:$port$path"))
            .timeout(Duration.ofSeconds(10))
            .method(method, HttpRequest.BodyPublishers.noBody())
        if (token != null) request.header("Authorization", "Bearer $token")
        if (apiKey != null) request.header("X-API-Key", apiKey)
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString())
    }

    companion object {
        private const val MONTH = "startDate=2026-09-01&finishDate=2026-09-30"
        private const val SCHEDULE_KEY = "xq_schedule-list-key"
        private const val OTHER_KEY = "xq_schedule-other-key"
        private val http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()
    }
}
