package app.xquare.xquareopenapi.domain.schedule

import app.xquare.xquareopenapi.domain.user.Role
import app.xquare.xquareopenapi.global.jwt.JwtProvider
import org.hamcrest.Matchers.containsString
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockHttpServletRequestDsl
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActionsDsl
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.patch
import org.springframework.test.web.servlet.post
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@SpringBootTest
@AutoConfigureMockMvc
class ScheduleTest @Autowired constructor(
    private val mockMvc: MockMvc,
    private val scheduleRepository: ScheduleRepository,
    jwtProvider: JwtProvider,
) {
    private val adminToken = jwtProvider.generateAccessToken(1L, Role.ADMIN)
    private val userToken = jwtProvider.generateAccessToken(2L, Role.USER)

    @AfterEach
    fun tearDown() {
        scheduleRepository.deleteAll()
    }

    // 등록

    @Test
    fun `등록하면 학교 일정으로 저장하고 Location 헤더로 상세조회 URI를 준다`() {
        mockMvc.post("/schedules") {
            bearer(adminToken)
            json("""{"date":"2026-09-05","title":"체육대회","content":"우천 시 9월 12일로 순연"}""")
        }.andExpect {
            status { isCreated() }
            jsonPath("$.message") { value("생성되었습니다.") }
        }.andReturn().response.also { response ->
            val schedule = scheduleRepository.findAll().single()
            assertEquals("/schedules/${schedule.id}", response.getHeader("Location"))
            assertEquals(LocalDate.of(2026, 9, 5), schedule.date)
            assertEquals("체육대회", schedule.title)
            assertEquals("우천 시 9월 12일로 순연", schedule.content)
            assertEquals(ScheduleSource.SCHOOL, schedule.source)
        }
    }

    @Test
    fun `content는 생략하거나 null로 보낼 수 있다`() {
        listOf("""{"date":"2026-09-05","title":"체육대회"}""", """{"date":"2026-09-05","title":"체육대회","content":null}""").forEach { body ->
            mockMvc.post("/schedules") {
                bearer(adminToken)
                json(body)
            }.andExpect { status { isCreated() } }
        }

        scheduleRepository.findAll().forEach { assertNull(it.content) }
    }

    @Test
    fun `제목은 50자, 내용은 1000자까지 받는다`() {
        mockMvc.post("/schedules") {
            bearer(adminToken)
            json(body(title = "가".repeat(50), content = "나".repeat(1000)))
        }.andExpect { status { isCreated() } }
    }

    @Test
    fun `형식에 맞지 않는 입력은 INVALID_INPUT`() {
        invalidBodies().forEach { (invalid, expectedMessage) ->
            mockMvc.post("/schedules") {
                bearer(adminToken)
                json(invalid)
            }.andExpectInvalidInput(expectedMessage)
        }
        assertEquals(0, scheduleRepository.count())
    }

    @Test
    fun `등록은 ADMIN만 할 수 있다`() {
        mockMvc.post("/schedules") {
            json(body())
        }.andExpect {
            status { isUnauthorized() }
            jsonPath("$.code") { value("INVALID_TOKEN") }
        }
        mockMvc.post("/schedules") {
            bearer(userToken)
            json(body())
        }.andExpect {
            status { isForbidden() }
            jsonPath("$.code") { value("FORBIDDEN") }
        }
        assertEquals(0, scheduleRepository.count())
    }

    // 단건 조회

    @Test
    fun `일정을 조회한다`() {
        val schedule = save(content = "우천 시 순연")

        mockMvc.get("/schedules/${schedule.id}") {
            bearer(userToken)
        }.andExpect {
            status { isOk() }
            jsonPath("$.id") { value(schedule.id!!) }
            jsonPath("$.date") { value("2026-09-05") }
            jsonPath("$.title") { value("체육대회") }
            jsonPath("$.content") { value("우천 시 순연") }
            jsonPath("$.source") { value("SCHOOL") }
        }
    }

    @Test
    fun `내용이 없으면 null, 나이스 일정은 source가 NEIS다`() {
        val schedule = save(content = null, source = ScheduleSource.NEIS)

        mockMvc.get("/schedules/${schedule.id}") {
            bearer(adminToken)
        }.andExpect {
            status { isOk() }
            // 명세가 "없으면 null"이라 필드를 빼지 않고 null로 준다.
            content { string(containsString("\"content\":null")) }
            jsonPath("$.source") { value("NEIS") }
        }
    }

    @Test
    fun `없는 일정은 SCHEDULE_NOT_FOUND`() {
        mockMvc.get("/schedules/999999") {
            bearer(userToken)
        }.andExpectScheduleNotFound()
    }

    @Test
    fun `일정 id가 숫자가 아니면 INVALID_INPUT`() {
        mockMvc.get("/schedules/abc") {
            bearer(userToken)
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("INVALID_INPUT") }
        }
    }

    @Test
    fun `조회는 토큰이 필요하다`() {
        val schedule = save()

        mockMvc.get("/schedules/${schedule.id}").andExpect {
            status { isUnauthorized() }
            jsonPath("$.code") { value("INVALID_TOKEN") }
        }
    }

    // 수정

    @Test
    fun `학교 일정을 통째로 바꾸고, content를 생략하면 지운다`() {
        val schedule = save(content = "우천 시 순연")

        mockMvc.patch("/schedules/${schedule.id}") {
            bearer(adminToken)
            json("""{"date":"2026-09-12","title":"체육대회(순연)"}""")
        }.andExpect {
            status { isOk() }
            jsonPath("$.message") { value("수정되었습니다.") }
        }

        val updated = scheduleRepository.findById(schedule.id!!).get()
        assertEquals(LocalDate.of(2026, 9, 12), updated.date)
        assertEquals("체육대회(순연)", updated.title)
        assertNull(updated.content)
        assertEquals(ScheduleSource.SCHOOL, updated.source)
    }

    @Test
    fun `수정에서 content를 보내면 바꾼다`() {
        val schedule = save(content = null)

        mockMvc.patch("/schedules/${schedule.id}") {
            bearer(adminToken)
            json(body(content = "새 내용"))
        }.andExpect { status { isOk() } }

        assertEquals("새 내용", scheduleRepository.findById(schedule.id!!).get().content)
    }

    @Test
    fun `나이스 일정은 수정할 수 없다`() {
        val schedule = save(content = "나이스 내용", source = ScheduleSource.NEIS)

        mockMvc.patch("/schedules/${schedule.id}") {
            bearer(adminToken)
            json(body(date = "2026-10-01", title = "바꾼 제목", content = "바꾼 내용"))
        }.andExpectNeisScheduleNotEditable()

        val kept = scheduleRepository.findById(schedule.id!!).get()
        assertEquals(LocalDate.of(2026, 9, 5), kept.date)
        assertEquals("체육대회", kept.title)
        assertEquals("나이스 내용", kept.content)
    }

    @Test
    fun `없는 일정을 수정하면 SCHEDULE_NOT_FOUND`() {
        mockMvc.patch("/schedules/999999") {
            bearer(adminToken)
            json(body())
        }.andExpectScheduleNotFound()
    }

    @Test
    fun `수정 입력도 형식을 검사한다`() {
        val schedule = save()

        invalidBodies().forEach { (invalid, expectedMessage) ->
            mockMvc.patch("/schedules/${schedule.id}") {
                bearer(adminToken)
                json(invalid)
            }.andExpectInvalidInput(expectedMessage)
        }
        assertEquals("체육대회", scheduleRepository.findById(schedule.id!!).get().title)
    }

    @Test
    fun `수정은 ADMIN만 할 수 있다`() {
        val schedule = save()

        mockMvc.patch("/schedules/${schedule.id}") {
            bearer(userToken)
            json(body(title = "바꾼 제목"))
        }.andExpect {
            status { isForbidden() }
            jsonPath("$.code") { value("FORBIDDEN") }
        }
        assertEquals("체육대회", scheduleRepository.findById(schedule.id!!).get().title)
    }

    // 삭제

    @Test
    fun `학교 일정을 삭제한다`() {
        val schedule = save()
        val other = save()

        mockMvc.delete("/schedules/${schedule.id}") {
            bearer(adminToken)
        }.andExpect {
            status { isOk() }
            jsonPath("$.message") { value("삭제되었습니다.") }
        }

        assertFalse(scheduleRepository.existsById(schedule.id!!))
        assertTrue(scheduleRepository.existsById(other.id!!))
    }

    @Test
    fun `나이스 일정은 삭제할 수 없다`() {
        val schedule = save(source = ScheduleSource.NEIS)

        mockMvc.delete("/schedules/${schedule.id}") {
            bearer(adminToken)
        }.andExpectNeisScheduleNotEditable()

        assertTrue(scheduleRepository.existsById(schedule.id!!))
    }

    @Test
    fun `없거나 이미 삭제한 일정은 SCHEDULE_NOT_FOUND`() {
        val schedule = save()
        mockMvc.delete("/schedules/${schedule.id}") { bearer(adminToken) }.andExpect { status { isOk() } }

        listOf(schedule.id!!, 999999L).forEach { id ->
            mockMvc.delete("/schedules/$id") {
                bearer(adminToken)
            }.andExpectScheduleNotFound()
        }
    }

    @Test
    fun `삭제는 ADMIN만 할 수 있다`() {
        val schedule = save()

        mockMvc.delete("/schedules/${schedule.id}") {
            bearer(userToken)
        }.andExpect {
            status { isForbidden() }
            jsonPath("$.code") { value("FORBIDDEN") }
        }
        mockMvc.delete("/schedules/${schedule.id}").andExpect { status { isUnauthorized() } }
        assertTrue(scheduleRepository.existsById(schedule.id!!))
    }

    private fun invalidBodies() = listOf(
        body(title = "") to "title",
        body(title = " ") to "title",
        body(title = "가".repeat(51)) to "title",
        body(content = "나".repeat(1001)) to "content",
        body(date = "2026-13-01") to "요청 값이 올바르지 않습니다.",
        """{"title":"체육대회"}""" to "요청 값이 올바르지 않습니다.",
        """{"date":"2026-09-05"}""" to "요청 값이 올바르지 않습니다.",
        """{"date":"2026-09-05","title":null}""" to "요청 값이 올바르지 않습니다.",
    )

    private fun save(content: String? = null, source: ScheduleSource = ScheduleSource.SCHOOL) =
        scheduleRepository.save(Schedule(LocalDate.of(2026, 9, 5), "체육대회", content, source))

    private fun ResultActionsDsl.andExpectInvalidInput(expectedMessage: String) = andExpect {
        status { isBadRequest() }
        jsonPath("$.code") { value("INVALID_INPUT") }
        jsonPath("$.message") { value(containsString(expectedMessage)) }
    }

    private fun ResultActionsDsl.andExpectScheduleNotFound() = andExpect {
        status { isNotFound() }
        jsonPath("$.code") { value("SCHEDULE_NOT_FOUND") }
        jsonPath("$.message") { value("존재하지 않는 일정입니다.") }
    }

    private fun ResultActionsDsl.andExpectNeisScheduleNotEditable() = andExpect {
        status { isConflict() }
        jsonPath("$.code") { value("NEIS_SCHEDULE_NOT_EDITABLE") }
        jsonPath("$.message") { value("나이스에서 동기화한 일정은 수정하거나 삭제할 수 없습니다.") }
    }

    private fun MockHttpServletRequestDsl.bearer(token: String) = header("Authorization", "Bearer $token")

    private fun MockHttpServletRequestDsl.json(body: String) {
        contentType = MediaType.APPLICATION_JSON
        content = body
    }

    private fun body(date: String = "2026-09-05", title: String = "체육대회", content: String? = null) =
        if (content == null) {
            """{"date":"$date","title":"$title"}"""
        } else {
            """{"date":"$date","title":"$title","content":"$content"}"""
        }
}
