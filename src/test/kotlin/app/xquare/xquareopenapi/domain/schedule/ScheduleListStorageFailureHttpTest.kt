package app.xquare.xquareopenapi.domain.schedule

import app.xquare.xquareopenapi.domain.apikey.ApiKeyField
import app.xquare.xquareopenapi.domain.apikey.ApiProject
import app.xquare.xquareopenapi.domain.apikey.ApiProjectFieldRepository
import app.xquare.xquareopenapi.domain.apikey.ApiProjectRepository
import app.xquare.xquareopenapi.domain.apikey.ApplicationStatus
import app.xquare.xquareopenapi.global.error.ErrorCode
import org.junit.jupiter.api.Assertions.assertAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.doReturn
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.doThrow
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.dao.DataAccessResourceFailureException
import org.springframework.test.util.ReflectionTestUtils
import org.springframework.data.domain.Limit
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionStatus
import org.springframework.transaction.TransactionSystemException
import tools.jackson.databind.json.JsonMapper
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import javax.sql.DataSource
import kotlin.test.assertEquals

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = [
        "server.address=127.0.0.1", "spring.datasource.url=jdbc:h2:mem:schedule_list_failure;MODE=MySQL",
        "spring.datasource.hikari.maximum-pool-size=1", "spring.datasource.hikari.connection-timeout=500",
    ],
)
class ScheduleListStorageFailureHttpTest @Autowired constructor(
    private val dataSource: DataSource,
    private val mapper: JsonMapper,
    @LocalServerPort private val port: Int,
) {
    @MockitoSpyBean
    private lateinit var transactions: PlatformTransactionManager

    @MockitoSpyBean
    private lateinit var schedules: ScheduleRepository

    // 일정 조회는 API 키 인증이라 키 조회도 DB를 쓴다. 연결을 붙잡은 상태에서도 인증은 통과하도록 키 조회만 가짜로 돌려준다.
    @MockitoSpyBean
    private lateinit var projects: ApiProjectRepository

    @MockitoSpyBean
    private lateinit var fields: ApiProjectFieldRepository

    @BeforeEach
    fun stubApiKey() {
        val project = ApiProject(1L, "일정 장애 테스트", LocalDateTime.now())
        project.issueKey(LocalDateTime.now())
        ReflectionTestUtils.setField(project, "id", 1L)
        doReturn(project).`when`(projects).findByKeyHash(anyString())
        doReturn(listOf(ApiKeyField.SCHEDULE)).`when`(fields)
            .findFieldsByProjectIdAndStatus(anyLong(), any(ApplicationStatus::class.java) ?: ApplicationStatus.APPROVED)
    }

    private val start = LocalDate.of(2026, 9, 1)
    private val http = HttpClient.newHttpClient()

    @Test
    fun `연결이 없어도 잘못된 기간은 400이고 유효한 기간만 저장소 오류다`() {
        dataSource.connection.use {
            assertAll(
                { assertError(list(start.minusDays(1)), 400, ErrorCode.INVALID_DATE_RANGE) },
                { assertError(list(start.plusDays(92)), 400, ErrorCode.INVALID_DATE_RANGE) },
                { assertError(list(start), 500, ErrorCode.INTERNAL_SERVER_ERROR) },
            )
        }
        assertEquals(200, list(start).statusCode())
    }

    @Test
    fun `유효한 기간의 조회 도중 저장소 접근 실패는 표준 500이다`() {
        doThrow(DataAccessResourceFailureException("synthetic-query-failure")).`when`(schedules)
            .findAllByDateBetweenOrderByDateAscIdAsc(
                any(LocalDate::class.java) ?: start, any(LocalDate::class.java) ?: start, any(Limit::class.java) ?: Limit.unlimited(),
            )
        assertError(list(start), 500, ErrorCode.INTERNAL_SERVER_ERROR)
    }

    @Test
    fun `유효한 기간의 트랜잭션 종료 실패는 표준 500이다`() {
        doAnswer { call ->
            call.callRealMethod()
            throw TransactionSystemException("synthetic-completion-failure")
        }.`when`(transactions).commit(any(TransactionStatus::class.java))
        assertError(list(start), 500, ErrorCode.INTERNAL_SERVER_ERROR)
    }

    private fun list(end: LocalDate): HttpResponse<String> {
        val request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:$port/schedules?startDate=$start&finishDate=$end"))
            .header("X-API-Key", "xq_schedule-failure-key")
            .timeout(Duration.ofSeconds(10)).GET().build()
        return http.send(request, HttpResponse.BodyHandlers.ofString())
    }

    private fun assertError(response: HttpResponse<String>, status: Int, code: ErrorCode) {
        assertEquals(status, response.statusCode(), response.body())
        val expected = mapper.createObjectNode().put("code", code.name).put("message", code.message)
        assertEquals(expected, mapper.readTree(response.body()))
    }
}
