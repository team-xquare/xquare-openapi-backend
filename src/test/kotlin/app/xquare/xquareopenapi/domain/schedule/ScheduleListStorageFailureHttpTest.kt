package app.xquare.xquareopenapi.domain.schedule

import app.xquare.xquareopenapi.domain.user.Role
import app.xquare.xquareopenapi.global.error.ErrorCode
import app.xquare.xquareopenapi.global.jwt.JwtProvider
import org.junit.jupiter.api.Assertions.assertAll
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.doThrow
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.dao.DataAccessResourceFailureException
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
    private val jwtProvider: JwtProvider,
    private val mapper: JsonMapper,
    @LocalServerPort private val port: Int,
) {
    @MockitoSpyBean
    private lateinit var transactions: PlatformTransactionManager

    @MockitoSpyBean
    private lateinit var schedules: ScheduleRepository

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
            .header("Authorization", "Bearer ${jwtProvider.generateAccessToken(2L, Role.USER)}")
            .timeout(Duration.ofSeconds(10)).GET().build()
        return http.send(request, HttpResponse.BodyHandlers.ofString())
    }

    private fun assertError(response: HttpResponse<String>, status: Int, code: ErrorCode) {
        assertEquals(status, response.statusCode(), response.body())
        val expected = mapper.createObjectNode().put("code", code.name).put("message", code.message)
        assertEquals(expected, mapper.readTree(response.body()))
    }
}
