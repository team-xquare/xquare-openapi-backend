package app.xquare.xquareopenapi.domain.schedule.neis

import app.xquare.xquareopenapi.domain.meal.neis.NeisProperties
import app.xquare.xquareopenapi.domain.schedule.Schedule
import app.xquare.xquareopenapi.domain.schedule.ScheduleRepository
import app.xquare.xquareopenapi.domain.schedule.ScheduleSource
import app.xquare.xquareopenapi.domain.schedule.ScheduleSource.NEIS
import app.xquare.xquareopenapi.domain.schedule.ScheduleSource.SCHOOL
import app.xquare.xquareopenapi.domain.schedule.neis.NeisScheduleClientTest.Companion.NO_DATA
import app.xquare.xquareopenapi.domain.schedule.neis.NeisScheduleClientTest.Companion.row
import app.xquare.xquareopenapi.domain.schedule.neis.NeisScheduleClientTest.Companion.rows
import app.xquare.xquareopenapi.domain.schedule.usecase.ScheduleSyncResult
import app.xquare.xquareopenapi.domain.schedule.usecase.SyncNeisSchedulesUseCase
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam
import org.springframework.test.web.client.response.MockRestResponseCreators.withServerError
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import kotlin.test.assertEquals
import kotlin.test.assertNull

@SpringBootTest
class NeisScheduleSyncTest @Autowired constructor(
    private val scheduleRepository: ScheduleRepository,
    private val syncNeisSchedulesUseCase: SyncNeisSchedulesUseCase,
) {
    private val builder = RestClient.builder().baseUrl("https://open.neis.go.kr")
    private val server = MockRestServiceServer.bindTo(builder).build()
    private val restClient = builder.build()

    // 서울 시간으로 2026-12-31 23:30. UTC로는 아직 12월 31일 14:30이다.
    private val clock = Clock.fixed(Instant.parse("2026-12-31T14:30:00Z"), ZoneId.of("Asia/Seoul"))

    private val march = YearMonth.of(2026, 3)

    @AfterEach
    fun tearDown() {
        scheduleRepository.deleteAll()
    }

    // 저장

    @Test
    fun `없는 일정은 나이스 일정으로 새로 만든다`() {
        val result = syncNeisSchedulesUseCase.execute(
            march,
            listOf(NeisSchedule(LocalDate.of(2026, 3, 3), "입학식", "1학년"), NeisSchedule(LocalDate.of(2026, 3, 3), "개학식", null)),
        )

        assertEquals(ScheduleSyncResult(created = 2, updated = 0, deleted = 0), result)
        val schedules = scheduleRepository.findAll().sortedBy(Schedule::title)
        assertEquals(listOf("개학식", "입학식"), schedules.map(Schedule::title))
        assertEquals(listOf(LocalDate.of(2026, 3, 3), LocalDate.of(2026, 3, 3)), schedules.map(Schedule::date))
        assertEquals(listOf(null, "1학년"), schedules.map(Schedule::content))
        assertEquals(listOf(NEIS, NEIS), schedules.map(Schedule::source))
    }

    @Test
    fun `날짜와 제목이 같은 나이스 일정은 id를 유지하고 내용만 바꾼다`() {
        val saved = save(LocalDate.of(2026, 3, 3), "입학식", "예전 내용", NEIS)

        val result = syncNeisSchedulesUseCase.execute(march, listOf(NeisSchedule(LocalDate.of(2026, 3, 3), "입학식", "새 내용")))

        assertEquals(ScheduleSyncResult(created = 0, updated = 1, deleted = 0), result)
        assertEquals(1, result.updated)
        val schedule = scheduleRepository.findAll().single()
        assertEquals(saved.id, schedule.id)
        assertEquals("새 내용", schedule.content)
    }

    @Test
    fun `나이스에서 사라졌거나 날짜가 바뀐 나이스 일정은 지운다`() {
        save(LocalDate.of(2026, 3, 5), "체육대회", null, NEIS)
        save(LocalDate.of(2026, 3, 6), "의무귀가", null, NEIS)

        val result = syncNeisSchedulesUseCase.execute(march, listOf(NeisSchedule(LocalDate.of(2026, 3, 12), "체육대회", null)))

        assertEquals(ScheduleSyncResult(created = 1, updated = 0, deleted = 2), result)
        // 로그에 쓰는 값이라 각 필드도 확인한다.
        assertEquals(listOf(1, 0, 2), listOf(result.created, result.updated, result.deleted))
        val schedule = scheduleRepository.findAll().single()
        assertEquals(LocalDate.of(2026, 3, 12), schedule.date)
        assertEquals("체육대회", schedule.title)
    }

    @Test
    fun `학교 일정은 날짜와 제목이 같아도 건드리지 않는다`() {
        val school = save(LocalDate.of(2026, 3, 3), "입학식", "학교 내용", SCHOOL)
        val otherSchool = save(LocalDate.of(2026, 3, 20), "동아리 발표회", null, SCHOOL)

        val result = syncNeisSchedulesUseCase.execute(march, listOf(NeisSchedule(LocalDate.of(2026, 3, 3), "입학식", "나이스 내용")))

        assertEquals(ScheduleSyncResult(created = 1, updated = 0, deleted = 0), result)
        assertEquals("학교 내용", scheduleRepository.findById(school.id!!).get().content)
        assertEquals(SCHOOL, scheduleRepository.findById(school.id!!).get().source)
        assertEquals(true, scheduleRepository.existsById(otherSchool.id!!))
        assertEquals("나이스 내용", scheduleRepository.findAll().single { it.source == NEIS }.content)
    }

    @Test
    fun `그 달이 아닌 나이스 일정은 읽지도 지우지도 않고, 받은 목록의 다른 달 일정은 버린다`() {
        val february = save(LocalDate.of(2026, 2, 28), "졸업식", null, NEIS)
        val april = save(LocalDate.of(2026, 4, 1), "만우절", null, NEIS)

        val result = syncNeisSchedulesUseCase.execute(
            march,
            listOf(NeisSchedule(LocalDate.of(2026, 2, 28), "졸업식", "바뀜"), NeisSchedule(LocalDate.of(2026, 4, 1), "새 행사", null)),
        )

        assertEquals(ScheduleSyncResult(created = 0, updated = 0, deleted = 0), result)
        assertEquals(listOf(february.id, april.id), scheduleRepository.findAll().map(Schedule::id).sortedBy { it })
        assertNull(scheduleRepository.findById(february.id!!).get().content)
    }

    @Test
    fun `그 달의 첫날과 말일 일정도 같은 일정으로 찾는다`() {
        save(LocalDate.of(2026, 3, 1), "3·1절", null, NEIS)
        save(LocalDate.of(2026, 3, 31), "학부모 상담", null, NEIS)

        val result = syncNeisSchedulesUseCase.execute(
            march,
            listOf(NeisSchedule(LocalDate.of(2026, 3, 1), "3·1절", null), NeisSchedule(LocalDate.of(2026, 3, 31), "학부모 상담", null)),
        )

        assertEquals(ScheduleSyncResult(created = 0, updated = 2, deleted = 0), result)
        assertEquals(2, scheduleRepository.count())
    }

    @Test
    fun `같은 날짜와 제목이 여러 개면 하나만 남긴다`() {
        val ids = listOf(save(LocalDate.of(2026, 3, 3), "입학식", null, NEIS).id, save(LocalDate.of(2026, 3, 3), "입학식", null, NEIS).id)

        val result = syncNeisSchedulesUseCase.execute(
            march,
            listOf(NeisSchedule(LocalDate.of(2026, 3, 3), "입학식", "가"), NeisSchedule(LocalDate.of(2026, 3, 3), "입학식", "나")),
        )

        assertEquals(ScheduleSyncResult(created = 0, updated = 1, deleted = 1), result)
        val schedule = scheduleRepository.findAll().single()
        assertEquals(true, schedule.id in ids)
        assertEquals("나", schedule.content)
    }

    @Test
    fun `컬럼보다 긴 제목과 내용은 잘라서 저장하고 다음 동기화에서도 같은 일정으로 찾는다`() {
        val longTitle = "가".repeat(Schedule.TITLE_COLUMN_LENGTH + 1)
        val longContent = "나".repeat(Schedule.CONTENT_COLUMN_LENGTH + 1)
        val neisSchedules = listOf(NeisSchedule(LocalDate.of(2026, 3, 3), longTitle, longContent))

        syncNeisSchedulesUseCase.execute(march, neisSchedules)
        val result = syncNeisSchedulesUseCase.execute(march, neisSchedules)

        assertEquals(ScheduleSyncResult(created = 0, updated = 1, deleted = 0), result)
        val schedule = scheduleRepository.findAll().single()
        assertEquals("가".repeat(Schedule.TITLE_COLUMN_LENGTH), schedule.title)
        assertEquals("나".repeat(Schedule.CONTENT_COLUMN_LENGTH), schedule.content)
    }

    @Test
    fun `같은 목록으로 다시 동기화해도 결과가 같다`() {
        val neisSchedules = listOf(NeisSchedule(LocalDate.of(2026, 3, 3), "입학식", null))

        syncNeisSchedulesUseCase.execute(march, neisSchedules)
        val id = scheduleRepository.findAll().single().id
        syncNeisSchedulesUseCase.execute(march, neisSchedules)

        assertEquals(id, scheduleRepository.findAll().single().id)
    }

    // 스케줄러

    @Test
    fun `서울 시간 기준 이번 달과 다음 달을 가져와 저장한다`() {
        expectMonth("20261201", "20261231", rows(row("20261225", "성탄절", "")))
        expectMonth("20270101", "20270131", rows(row("20270101", "신정", "")))

        scheduler().sync()

        server.verify()
        val schedules = scheduleRepository.findAll().sortedBy(Schedule::date)
        assertEquals(listOf(LocalDate.of(2026, 12, 25), LocalDate.of(2027, 1, 1)), schedules.map(Schedule::date))
        assertEquals(listOf("성탄절", "신정"), schedules.map(Schedule::title))
    }

    @Test
    fun `한 달이 실패하면 그 달은 그대로 두고 다음 달은 저장한다`() {
        val december = save(LocalDate.of(2026, 12, 25), "성탄절", null, NEIS)
        server.expect(queryParam("AA_FROM_YMD", "20261201")).andRespond(withServerError())
        expectMonth("20270101", "20270131", rows(row("20270101", "신정", "")))

        scheduler().sync()

        server.verify()
        assertEquals(true, scheduleRepository.existsById(december.id!!))
        assertEquals(listOf("성탄절", "신정"), scheduleRepository.findAll().map(Schedule::title).sorted())
    }

    @Test
    fun `데이터가 없는 달은 그 달의 나이스 일정을 지운다`() {
        save(LocalDate.of(2026, 12, 25), "성탄절", null, NEIS)
        val school = save(LocalDate.of(2026, 12, 24), "종업식", null, SCHOOL)
        expectMonth("20261201", "20261231", NO_DATA)
        expectMonth("20270101", "20270131", NO_DATA)

        scheduler().sync()

        server.verify()
        assertEquals(listOf(school.id), scheduleRepository.findAll().map(Schedule::id))
    }

    @Test
    fun `API 키가 없으면 나이스를 부르지 않는다`() {
        listOf("", " ").forEach { apiKey ->
            // 기대한 요청이 없으므로 요청을 보내면 AssertionError가 나서 테스트가 실패한다.
            scheduler(apiKey).sync()
        }

        server.verify()
        assertEquals(0, scheduleRepository.count())
    }

    private fun save(date: LocalDate, title: String, content: String?, source: ScheduleSource) =
        scheduleRepository.save(Schedule(date, title, content, source))

    private fun scheduler(apiKey: String = "test-key"): NeisScheduleSyncScheduler {
        val properties = NeisProperties(apiKey, "G10", "7430310")
        return NeisScheduleSyncScheduler(NeisScheduleClient(restClient, properties), syncNeisSchedulesUseCase, properties, clock)
    }

    private fun expectMonth(from: String, to: String, body: String) {
        server.expect(queryParam("AA_FROM_YMD", from))
            .andExpect(queryParam("AA_TO_YMD", to))
            .andRespond(withSuccess(body, MediaType.APPLICATION_JSON))
    }
}
