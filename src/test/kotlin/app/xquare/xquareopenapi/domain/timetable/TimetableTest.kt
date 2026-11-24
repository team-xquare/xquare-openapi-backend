package app.xquare.xquareopenapi.domain.timetable

import app.xquare.xquareopenapi.domain.meal.neis.NeisApiException
import app.xquare.xquareopenapi.domain.timetable.neis.NeisTimetable
import app.xquare.xquareopenapi.domain.timetable.neis.NeisTimetableClient
import app.xquare.xquareopenapi.domain.user.Role
import app.xquare.xquareopenapi.global.jwt.JwtProvider
import org.hamcrest.Matchers.containsString
import org.junit.jupiter.api.Test
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import java.time.LocalDate

// 시간표 캐시는 컨텍스트에 남으므로 테스트마다 다른 학급·주를 쓴다.
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = ["neis.api-key=test-key"])
class TimetableTest @Autowired constructor(
    private val mockMvc: MockMvc,
    jwtProvider: JwtProvider,
) {
    @MockitoBean
    private lateinit var neisTimetableClient: NeisTimetableClient

    private val userToken = jwtProvider.generateAccessToken(2L, Role.USER)

    @Test
    fun `학급의 한 주 시간표를 조회한다`() {
        val monday = LocalDate.of(2026, 11, 16)
        `when`(neisTimetableClient.fetch(1, 2, monday, monday.plusDays(6))).thenReturn(
            listOf(NeisTimetable(monday, 1, "국어"), NeisTimetable(monday, 2, "수학"), NeisTimetable(monday.plusDays(1), 1, "영어")),
        )

        mockMvc.get("/timetables?grade=1&classNum=2&date=2026-11-18") {
            header("Authorization", "Bearer $userToken")
        }.andExpect {
            status { isOk() }
            jsonPath("$.grade") { value(1) }
            jsonPath("$.classNum") { value(2) }
            jsonPath("$.startDate") { value("2026-11-16") }
            jsonPath("$.finishDate") { value("2026-11-22") }
            jsonPath("$.days.length()") { value(2) }
            jsonPath("$.days[0].date") { value("2026-11-16") }
            jsonPath("$.days[0].periods[0].period") { value(1) }
            jsonPath("$.days[0].periods[0].subject") { value("국어") }
            jsonPath("$.days[0].periods[1].period") { value(2) }
            jsonPath("$.days[0].periods[1].subject") { value("수학") }
            jsonPath("$.days[1].date") { value("2026-11-17") }
            jsonPath("$.days[1].periods[0].subject") { value("영어") }
        }
    }

    @Test
    fun `수업이 없는 주는 빈 days를 준다`() {
        val monday = LocalDate.of(2027, 1, 4)
        `when`(neisTimetableClient.fetch(3, 9, monday, monday.plusDays(6))).thenReturn(emptyList())

        mockMvc.get("/timetables?grade=3&classNum=9&date=2027-01-04") {
            header("Authorization", "Bearer $userToken")
        }.andExpect {
            status { isOk() }
            jsonPath("$.days.length()") { value(0) }
        }
    }

    @Test
    fun `나이스에서 가져오지 못하면 502 TIMETABLE_UNAVAILABLE`() {
        val monday = LocalDate.of(2027, 2, 1)
        `when`(neisTimetableClient.fetch(1, 1, monday, monday.plusDays(6))).thenThrow(NeisApiException("ERROR-500", "서버 오류"))

        mockMvc.get("/timetables?grade=1&classNum=1&date=2027-02-01") {
            header("Authorization", "Bearer $userToken")
        }.andExpect {
            status { isBadGateway() }
            jsonPath("$.code") { value("TIMETABLE_UNAVAILABLE") }
            jsonPath("$.message") { value("나이스에서 시간표를 가져오지 못했습니다. 잠시 후 다시 시도해 주세요.") }
        }
    }

    @Test
    fun `학년, 반이 없거나 범위를 벗어나거나 형식이 틀리면 INVALID_INPUT`() {
        listOf(
            "classNum=1" to "grade",
            "grade=1" to "classNum",
            "grade=0&classNum=1" to "grade",
            "grade=4&classNum=1" to "grade",
            "grade=1&classNum=0" to "classNum",
            "grade=1&classNum=10" to "classNum",
            "grade=a&classNum=1" to "grade: 형식이 올바르지 않습니다.",
            "grade=1&classNum=1&date=2026-13-01" to "date: 형식이 올바르지 않습니다.",
        ).forEach { (query, expectedMessage) ->
            mockMvc.get("/timetables?$query") {
                header("Authorization", "Bearer $userToken")
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.code") { value("INVALID_INPUT") }
                jsonPath("$.message") { value(containsString(expectedMessage)) }
            }
        }
        verifyNoInteractions(neisTimetableClient)
    }

    @Test
    fun `공개 API라 키나 토큰이 없어도, 토큰이 유효하지 않아도 조회된다`() {
        mockMvc.get("/timetables?grade=2&classNum=5&date=2026-12-07").andExpect {
            status { isOk() }
            jsonPath("$.days") { isEmpty() }
        }
        mockMvc.get("/timetables?grade=2&classNum=6&date=2026-12-07") {
            header("Authorization", "Bearer invalid-token")
        }.andExpect { status { isOk() } }
    }
}
