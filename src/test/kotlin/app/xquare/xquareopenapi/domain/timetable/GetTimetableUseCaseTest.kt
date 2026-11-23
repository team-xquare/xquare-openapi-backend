package app.xquare.xquareopenapi.domain.timetable

import app.xquare.xquareopenapi.domain.meal.neis.NeisApiException
import app.xquare.xquareopenapi.domain.meal.neis.NeisProperties
import app.xquare.xquareopenapi.domain.timetable.neis.NeisTimetable
import app.xquare.xquareopenapi.domain.timetable.neis.NeisTimetableClient
import app.xquare.xquareopenapi.domain.timetable.presentation.dto.TimetableDayResponse
import app.xquare.xquareopenapi.domain.timetable.presentation.dto.TimetablePeriodResponse
import app.xquare.xquareopenapi.domain.timetable.presentation.dto.TimetableResponse
import app.xquare.xquareopenapi.domain.timetable.presentation.dto.TimetableSearchRequest
import app.xquare.xquareopenapi.domain.timetable.usecase.GetTimetableUseCase
import app.xquare.xquareopenapi.global.error.ErrorCode
import app.xquare.xquareopenapi.global.error.XquareException
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.`when`
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class GetTimetableUseCaseTest {
    private val client = mock(NeisTimetableClient::class.java)
    private val clock = MutableClock(Instant.parse("2026-11-18T03:00:00Z"))
    private val useCase = GetTimetableUseCase(client, NeisProperties("test-key", "G10", "7430310"), clock)

    private val monday = LocalDate.of(2026, 11, 16)
    private val sunday = LocalDate.of(2026, 11, 22)

    @Test
    fun `date가 속한 주(월~일)의 시간표를 날짜별로 묶어 준다`() {
        `when`(client.fetch(1, 2, monday, sunday)).thenReturn(
            listOf(
                NeisTimetable(monday, 1, "국어"),
                NeisTimetable(monday, 2, "수학"),
                NeisTimetable(LocalDate.of(2026, 11, 18), 1, "영어"),
            ),
        )

        // 수요일을 주면 그 주 월요일부터 일요일까지다.
        val response = useCase.execute(TimetableSearchRequest(1, 2, LocalDate.of(2026, 11, 18)))

        assertEquals(
            TimetableResponse(
                grade = 1,
                classNum = 2,
                startDate = monday,
                finishDate = sunday,
                days = listOf(
                    TimetableDayResponse(monday, listOf(TimetablePeriodResponse(1, "국어"), TimetablePeriodResponse(2, "수학"))),
                    TimetableDayResponse(LocalDate.of(2026, 11, 18), listOf(TimetablePeriodResponse(1, "영어"))),
                ),
            ),
            response,
        )
    }

    @Test
    fun `월요일과 일요일을 주면 같은 주다`() {
        `when`(client.fetch(1, 1, monday, sunday)).thenReturn(emptyList())

        listOf(monday, sunday).forEach { assertEquals(monday, useCase.execute(TimetableSearchRequest(1, 1, it)).startDate) }

        // 같은 주라 두 번째는 캐시에서 준다.
        verify(client, times(1)).fetch(1, 1, monday, sunday)
    }

    @Test
    fun `date를 생략하면 서울 시간으로 오늘이 속한 주다`() {
        // UTC로는 일요일(11-22) 15:30이지만 서울은 월요일(11-23) 00:30이다.
        clock.now = Instant.parse("2026-11-22T15:30:00Z")
        val nextMonday = LocalDate.of(2026, 11, 23)
        `when`(client.fetch(1, 1, nextMonday, nextMonday.plusDays(6))).thenReturn(emptyList())

        val response = useCase.execute(TimetableSearchRequest(1, 1, null))

        assertEquals(nextMonday, response.startDate)
        assertEquals(nextMonday.plusDays(6), response.finishDate)
        assertEquals(emptyList(), response.days)
    }

    @Test
    fun `10분 동안은 같은 학급, 같은 주를 다시 부르지 않고 10분이 지나면 다시 가져온다`() {
        `when`(client.fetch(1, 1, monday, sunday)).thenReturn(listOf(NeisTimetable(monday, 1, "국어")))
        val request = TimetableSearchRequest(1, 1, monday)

        useCase.execute(request)
        clock.now = clock.now.plus(Duration.ofMinutes(10)).minusMillis(1)
        assertEquals("국어", useCase.execute(request).days.single().periods.single().subject)
        verify(client, times(1)).fetch(1, 1, monday, sunday)

        clock.now = clock.now.plusMillis(1)
        `when`(client.fetch(1, 1, monday, sunday)).thenReturn(listOf(NeisTimetable(monday, 1, "바뀐 과목")))
        assertEquals("바뀐 과목", useCase.execute(request).days.single().periods.single().subject)
        verify(client, times(2)).fetch(1, 1, monday, sunday)
    }

    @Test
    fun `학년, 반, 주가 다르면 따로 가져온다`() {
        listOf(Triple(1, 1, monday), Triple(2, 1, monday), Triple(1, 2, monday), Triple(1, 1, monday.plusWeeks(1))).forEach { (grade, classNum, start) ->
            `when`(client.fetch(grade, classNum, start, start.plusDays(6))).thenReturn(listOf(NeisTimetable(start, 1, "$grade-$classNum")))
        }

        assertEquals("1-1", subject(useCase.execute(TimetableSearchRequest(1, 1, monday))))
        assertEquals("2-1", subject(useCase.execute(TimetableSearchRequest(2, 1, monday))))
        assertEquals("1-2", subject(useCase.execute(TimetableSearchRequest(1, 2, monday))))
        val nextWeek = useCase.execute(TimetableSearchRequest(1, 1, monday.plusWeeks(1)))
        assertEquals(monday.plusWeeks(1), nextWeek.startDate)
        assertEquals(1 to 1, nextWeek.grade to nextWeek.classNum)
        assertEquals("1-1", subject(nextWeek))
    }

    @Test
    fun `나이스 호출이 실패하면 TIMETABLE_UNAVAILABLE이고 실패는 캐시하지 않는다`() {
        `when`(client.fetch(1, 1, monday, sunday))
            .thenThrow(NeisApiException("ERROR-500", "서버 오류"))
            .thenReturn(listOf(NeisTimetable(monday, 1, "국어")))
        val request = TimetableSearchRequest(1, 1, monday)

        val thrown = assertFailsWith<XquareException> { useCase.execute(request) }
        assertEquals(ErrorCode.TIMETABLE_UNAVAILABLE, thrown.errorCode)

        assertEquals("국어", subject(useCase.execute(request)))
    }

    @Test
    fun `API 키가 없으면 나이스를 부르지 않고 TIMETABLE_UNAVAILABLE`() {
        listOf("", " ").forEach { apiKey ->
            val noKey = GetTimetableUseCase(client, NeisProperties(apiKey, "G10", "7430310"), clock)

            val thrown = assertFailsWith<XquareException> { noKey.execute(TimetableSearchRequest(1, 1, monday)) }
            assertEquals(ErrorCode.TIMETABLE_UNAVAILABLE, thrown.errorCode)
        }
        verifyNoInteractions(client)
    }

    @Test
    fun `캐시가 1000개 쌓이면 비우고 새로 담는다`() {
        val weeks = (0L until 1000L).map { monday.plusWeeks(it) }
        weeks.forEach { `when`(client.fetch(1, 1, it, it.plusDays(6))).thenReturn(emptyList()) }
        `when`(client.fetch(2, 1, monday, sunday)).thenReturn(emptyList())

        // 999개까지는 그대로 남는다.
        weeks.take(999).forEach { useCase.execute(TimetableSearchRequest(1, 1, it)) }
        useCase.execute(TimetableSearchRequest(1, 1, weeks[0]))
        verify(client, times(1)).fetch(1, 1, weeks[0], weeks[0].plusDays(6))

        // 1000번째를 담으면 1000개가 되고, 그다음 새 키를 담기 전에 비운다.
        useCase.execute(TimetableSearchRequest(1, 1, weeks[999]))
        useCase.execute(TimetableSearchRequest(1, 1, weeks[998]))
        verify(client, times(1)).fetch(1, 1, weeks[998], weeks[998].plusDays(6))
        useCase.execute(TimetableSearchRequest(2, 1, monday))
        useCase.execute(TimetableSearchRequest(1, 1, weeks[0]))
        verify(client, times(2)).fetch(1, 1, weeks[0], weeks[0].plusDays(6))
    }

    private fun subject(response: TimetableResponse) = response.days.single().periods.single().subject

    private class MutableClock(var now: Instant) : Clock() {
        override fun getZone(): ZoneId = ZoneId.of("Asia/Seoul")
        override fun withZone(zone: ZoneId): Clock = throw UnsupportedOperationException()
        override fun instant(): Instant = now
    }
}
