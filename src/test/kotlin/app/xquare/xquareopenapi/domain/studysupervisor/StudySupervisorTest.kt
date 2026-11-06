package app.xquare.xquareopenapi.domain.studysupervisor

import app.xquare.xquareopenapi.domain.teacher.Teacher
import app.xquare.xquareopenapi.domain.teacher.TeacherPosition
import app.xquare.xquareopenapi.domain.teacher.TeacherRepository
import app.xquare.xquareopenapi.domain.teacher.TeacherSubject
import app.xquare.xquareopenapi.domain.user.Role
import app.xquare.xquareopenapi.global.jwt.JwtProvider
import jakarta.persistence.EntityManagerFactory
import jakarta.servlet.ServletException
import org.hamcrest.Matchers.contains
import org.hamcrest.Matchers.containsString
import org.hibernate.SessionFactory
import org.hibernate.exception.ConstraintViolationException
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.doThrow
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.MediaType
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean
import org.springframework.test.web.servlet.MockHttpServletRequestDsl
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActionsDsl
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.patch
import org.springframework.test.web.servlet.post
import java.sql.SQLException
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

// 조회의 쿼리 수를 세려고 Hibernate 통계를 켠다. 스파이 빈 때문에 어차피 이 테스트만의 컨텍스트라 다른 테스트에 영향이 없다.
@SpringBootTest(properties = ["spring.jpa.properties.hibernate.generate_statistics=true"])
@AutoConfigureMockMvc
class StudySupervisorTest @Autowired constructor(
    private val mockMvc: MockMvc,
    private val entityManagerFactory: EntityManagerFactory,
    private val studySupervisorRepository: StudySupervisorRepository,
    private val assignmentRepository: StudySupervisorAssignmentRepository,
    private val teacherRepository: TeacherRepository,
    jwtProvider: JwtProvider,
) {
    // 동시 등록·수정을 흉내 내려고 중복 검사만 통과시킬 때 쓴다.
    @MockitoSpyBean
    private lateinit var spiedStudySupervisorRepository: StudySupervisorRepository

    private val adminToken = jwtProvider.generateAccessToken(1L, Role.ADMIN)
    private val userToken = jwtProvider.generateAccessToken(2L, Role.USER)

    @AfterEach
    fun tearDown() {
        assignmentRepository.deleteAll()
        studySupervisorRepository.deleteAll()
        teacherRepository.deleteAll()
    }

    // 등록

    @Test
    fun `등록하면 저장하고 Location 헤더로 수정, 삭제할 URI를 준다`() {
        val first = teacher("이진경")
        val second = teacher("박성준")
        val third = teacher("김준서")

        val location = mockMvc.post("/study-supervisors") {
            bearer(adminToken)
            json(body(assignments = listOf(2 to first.id!!, 3 to second.id!!, 4 to third.id!!)))
        }.andExpect {
            status { isCreated() }
            jsonPath("$.message") { value("생성되었습니다.") }
        }.andReturn().response.getHeader("Location")

        val saved = studySupervisorRepository.findAll().single()
        assertEquals("/study-supervisors/${saved.id}", location)
        assertEquals(LocalDate.of(2026, 9, 14), saved.date)
        mockMvc.get("/study-supervisors/day?date=2026-09-14") {
            bearer(userToken)
        }.andExpect {
            status { isOk() }
            jsonPath("$.id") { value(saved.id!!) }
            jsonPath("$.date") { value("2026-09-14") }
            jsonPath("$.supervisors[*].floor") { value(contains(2, 3, 4)) }
            jsonPath("$.supervisors[*].teacher.teacherId") { value(contains(first.id!!.toInt(), second.id!!.toInt(), third.id!!.toInt())) }
            jsonPath("$.supervisors[*].teacher.name") { value(contains("이진경", "박성준", "김준서")) }
        }
    }

    @Test
    fun `일부 층만 등록할 수 있고 한 교직원이 여러 층을 맡을 수 있다`() {
        val teacher = teacher("이진경")

        mockMvc.post("/study-supervisors") {
            bearer(adminToken)
            json(body(assignments = listOf(4 to teacher.id!!, 2 to teacher.id!!)))
        }.andExpect { status { isCreated() } }

        assertEquals(2, assignmentRepository.count())
        mockMvc.get("/study-supervisors/day?date=2026-09-14") {
            bearer(userToken)
        }.andExpect {
            jsonPath("$.supervisors[*].floor") { value(contains(2, 3, 4)) }
            jsonPath("$.supervisors[0].teacher.name") { value("이진경") }
            jsonPath("$.supervisors[1].teacher") { value(null as Any?) }
            jsonPath("$.supervisors[2].teacher.name") { value("이진경") }
        }
    }

    @Test
    fun `같은 날짜에 정보가 있으면 저장을 시도하지 않고 SUPERVISION_ALREADY_EXISTS`() {
        val teacher = teacher("이진경")
        val existing = supervision(LocalDate.of(2026, 9, 14), 2 to teacher)

        mockMvc.post("/study-supervisors") {
            bearer(adminToken)
            json(body(assignments = listOf(3 to teacher.id!!)))
        }.andExpectConflict()
        assertEquals(listOf(existing.id), studySupervisorRepository.findAll().map { it.id })
        assertEquals(listOf(2), assignmentRepository.findAll().map { it.floor })
        // 중복 검사를 빼도 unique 제약으로 409가 나므로, 검사에서 막혔는지는 저장 호출로 확인한다.
        verify(spiedStudySupervisorRepository, never()).saveAndFlush(any())
    }

    @Test
    fun `중복 검사를 통과해도 같은 날짜가 먼저 저장됐으면 SUPERVISION_ALREADY_EXISTS`() {
        val teacher = teacher("이진경")
        supervision(LocalDate.of(2026, 9, 14), 2 to teacher)
        doReturn(false).`when`(spiedStudySupervisorRepository).existsByDate(anyDate())

        mockMvc.post("/study-supervisors") {
            bearer(adminToken)
            json(body(assignments = listOf(3 to teacher.id!!)))
        }.andExpectConflict()
        assertEquals(1, studySupervisorRepository.count())
        assertEquals(listOf(2), assignmentRepository.findAll().map { it.floor })
    }

    @Test
    fun `날짜 제약이 아닌 무결성 오류는 그대로 던진다`() {
        val teacher = teacher("이진경")
        val violation = otherViolation()
        doThrow(violation).`when`(spiedStudySupervisorRepository).saveAndFlush(any())

        val thrown = assertFailsWith<ServletException> {
            mockMvc.post("/study-supervisors") {
                bearer(adminToken)
                json(body(assignments = listOf(2 to teacher.id!!)))
            }
        }
        assertSame(violation, thrown.cause)
    }

    @Test
    fun `없는 교직원이 있으면 TEACHER_NOT_FOUND이고 저장하지 않는다`() {
        val teacher = teacher("이진경")

        mockMvc.post("/study-supervisors") {
            bearer(adminToken)
            json(body(assignments = listOf(2 to teacher.id!!, 3 to teacher.id!! + 100)))
        }.andExpectTeacherNotFound()
        assertEquals(0, studySupervisorRepository.count())
        assertEquals(0, assignmentRepository.count())
    }

    @Test
    fun `없는 교직원과 겹치는 날짜가 함께 있으면 TEACHER_NOT_FOUND`() {
        val teacher = teacher("이진경")
        supervision(LocalDate.of(2026, 9, 14), 2 to teacher)

        mockMvc.post("/study-supervisors") {
            bearer(adminToken)
            json(body(assignments = listOf(2 to teacher.id!! + 100)))
        }.andExpectTeacherNotFound()
    }

    @Test
    fun `형식에 맞지 않는 입력은 INVALID_INPUT`() {
        val id = teacher("이진경").id!!

        invalidBodies(id).forEach { (invalid, expectedMessage) ->
            mockMvc.post("/study-supervisors") {
                bearer(adminToken)
                json(invalid)
            }.andExpectInvalidInput(expectedMessage)
        }
        assertEquals(0, studySupervisorRepository.count())
    }

    @Test
    fun `층은 2층부터 4층까지 받는다`() {
        val id = teacher("이진경").id!!

        listOf(2, 4).forEachIndexed { index, floor ->
            mockMvc.post("/study-supervisors") {
                bearer(adminToken)
                json(body(date = "2026-09-1$index", assignments = listOf(floor to id)))
            }.andExpect { status { isCreated() } }
        }
    }

    // 기간 조회

    @Test
    fun `기간 안의 모든 날짜를 날짜 순으로 주고 정보가 없는 날짜는 빈 배열이다`() {
        val first = teacher("이진경")
        val second = teacher("박성준")
        val monday = supervision(LocalDate.of(2026, 9, 14), 2 to first, 3 to second)
        val wednesday = supervision(LocalDate.of(2026, 9, 16), 4 to second)
        supervision(LocalDate.of(2026, 9, 13), 2 to first)
        supervision(LocalDate.of(2026, 9, 17), 2 to first)

        mockMvc.get("/study-supervisors?startDate=2026-09-14&finishDate=2026-09-16") {
            bearer(userToken)
        }.andExpect {
            status { isOk() }
            jsonPath("$.startDate") { value("2026-09-14") }
            jsonPath("$.finishDate") { value("2026-09-16") }
            jsonPath("$.supervisions[*].date") { value(contains("2026-09-14", "2026-09-15", "2026-09-16")) }
            jsonPath("$.supervisions[0].id") { value(monday.id!!) }
            jsonPath("$.supervisions[0].supervisors[*].floor") { value(contains(2, 3, 4)) }
            jsonPath("$.supervisions[0].supervisors[0].teacher.name") { value("이진경") }
            jsonPath("$.supervisions[0].supervisors[1].teacher.teacherId") { value(second.id!!) }
            jsonPath("$.supervisions[0].supervisors[1].teacher.name") { value("박성준") }
            jsonPath("$.supervisions[0].supervisors[2].teacher") { value(null as Any?) }
            jsonPath("$.supervisions[1].id") { value(null as Any?) }
            jsonPath("$.supervisions[1].supervisors") { isEmpty() }
            jsonPath("$.supervisions[2].id") { value(wednesday.id!!) }
            jsonPath("$.supervisions[2].supervisors[0].teacher") { value(null as Any?) }
            jsonPath("$.supervisions[2].supervisors[2].teacher.name") { value("박성준") }
        }
    }

    @Test
    fun `정보가 하나도 없는 기간이면 모든 날짜가 빈 배열이다`() {
        mockMvc.get("/study-supervisors?startDate=2026-09-14&finishDate=2026-09-15") {
            bearer(userToken)
        }.andExpect {
            status { isOk() }
            jsonPath("$.supervisions.length()") { value(2) }
            jsonPath("$.supervisions[0].supervisors") { isEmpty() }
            jsonPath("$.supervisions[1].supervisors") { isEmpty() }
        }
    }

    @Test
    fun `하루만 조회할 수 있고 기간은 92일까지 받는다`() {
        mockMvc.get("/study-supervisors?startDate=2026-09-14&finishDate=2026-09-14") {
            bearer(userToken)
        }.andExpect {
            status { isOk() }
            jsonPath("$.supervisions[*].date") { value(contains("2026-09-14")) }
        }
        mockMvc.get("/study-supervisors?startDate=2026-09-01&finishDate=2026-12-01") {
            bearer(userToken)
        }.andExpect {
            status { isOk() }
            jsonPath("$.supervisions.length()") { value(92) }
            jsonPath("$.supervisions[91].date") { value("2026-12-01") }
        }
    }

    @Test
    fun `종료일이 시작일보다 이르거나 기간이 92일을 넘으면 INVALID_DATE_RANGE`() {
        listOf(
            "startDate=2026-09-02&finishDate=2026-09-01",
            "startDate=2026-09-01&finishDate=2026-12-02",
        ).forEach { query ->
            mockMvc.get("/study-supervisors?$query") {
                bearer(userToken)
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.code") { value("INVALID_DATE_RANGE") }
                jsonPath("$.message") { value("조회 기간이 올바르지 않습니다.") }
            }
        }
    }

    @Test
    fun `기간이 빠지거나 날짜 형식이 틀리면 INVALID_INPUT`() {
        listOf(
            "finishDate=2026-09-16" to "startDate: ",
            "startDate=2026-09-14" to "finishDate: ",
            "" to "Date: ",
            "startDate=2026-9-14&finishDate=2026-09-16" to "startDate: 형식이 올바르지 않습니다.",
            "startDate=2026-09-14&finishDate=abc" to "finishDate: 형식이 올바르지 않습니다.",
        ).forEach { (query, expectedMessage) ->
            mockMvc.get("/study-supervisors?$query") {
                bearer(userToken)
            }.andExpectInvalidInput(expectedMessage)
        }
    }

    @Test
    fun `기간 조회는 날짜와 정보 수와 상관없이 같은 수의 쿼리로 조회한다`() {
        val teacher = teacher("이진경")
        supervision(LocalDate.of(2026, 9, 14), 2 to teacher)
        val one = queryCount("/study-supervisors?startDate=2026-09-14&finishDate=2026-09-14")
        val other = teacher("박성준")
        (15..20).forEach { supervision(LocalDate.of(2026, 9, it), 2 to teacher, 3 to other, 4 to teacher(("선생$it"))) }

        assertEquals(one, queryCount("/study-supervisors?startDate=2026-09-01&finishDate=2026-09-30"))
    }

    // 일별 조회

    @Test
    fun `하루의 층별 배정을 준다`() {
        val teacher = teacher("이진경")
        val supervision = supervision(LocalDate.of(2026, 9, 14), 3 to teacher)
        supervision(LocalDate.of(2026, 9, 15), 2 to teacher)

        mockMvc.get("/study-supervisors/day?date=2026-09-14") {
            bearer(userToken)
        }.andExpect {
            status { isOk() }
            jsonPath("$.id") { value(supervision.id!!) }
            jsonPath("$.date") { value("2026-09-14") }
            jsonPath("$.supervisors[*].floor") { value(contains(2, 3, 4)) }
            jsonPath("$.supervisors[0].teacher") { value(null as Any?) }
            jsonPath("$.supervisors[1].teacher.teacherId") { value(teacher.id!!) }
            jsonPath("$.supervisors[1].teacher.name") { value("이진경") }
            jsonPath("$.supervisors[2].teacher") { value(null as Any?) }
        }
    }

    @Test
    fun `정보가 없는 날짜면 id는 null이고 supervisors는 빈 배열이다`() {
        supervision(LocalDate.of(2026, 9, 15), 2 to teacher("이진경"))

        mockMvc.get("/study-supervisors/day?date=2026-09-14") {
            bearer(userToken)
        }.andExpect {
            status { isOk() }
            jsonPath("$.id") { value(null as Any?) }
            jsonPath("$.date") { value("2026-09-14") }
            jsonPath("$.supervisors") { isEmpty() }
        }
    }

    @Test
    fun `date가 없거나 형식이 틀리면 INVALID_INPUT`() {
        listOf(
            "" to "date: ",
            "?date=2026-9-14" to "date: 형식이 올바르지 않습니다.",
            "?date=abc" to "date: 형식이 올바르지 않습니다.",
        ).forEach { (query, expectedMessage) ->
            mockMvc.get("/study-supervisors/day$query") {
                bearer(userToken)
            }.andExpectInvalidInput(expectedMessage)
        }
    }

    @Test
    fun `단건 조회는 명세에 없어서 405`() {
        val supervision = supervision(LocalDate.of(2026, 9, 14), 2 to teacher("이진경"))

        mockMvc.get("/study-supervisors/${supervision.id}") {
            bearer(userToken)
        }.andExpect { status { isMethodNotAllowed() } }
    }

    // 수정

    @Test
    fun `수정하면 날짜를 바꾸고 배정은 전달한 목록으로 교체한다`() {
        val first = teacher("이진경")
        val second = teacher("박성준")
        val supervision = supervision(LocalDate.of(2026, 9, 14), 2 to first, 3 to first)
        val previousAssignmentIds = assignmentRepository.findAll().map { it.id }

        mockMvc.patch("/study-supervisors/${supervision.id}") {
            bearer(adminToken)
            json(body(date = "2026-09-15", assignments = listOf(2 to second.id!!, 4 to first.id!!)))
        }.andExpect {
            status { isOk() }
            jsonPath("$.message") { value("수정되었습니다.") }
        }

        assertEquals(LocalDate.of(2026, 9, 15), studySupervisorRepository.findAll().single().date)
        // 기존 배정을 지우고 새로 넣는다. 남은 행은 모두 새 id다.
        val assignmentIds = assignmentRepository.findAll().map { it.id }
        assertEquals(2, assignmentIds.toSet().size)
        assertEquals(emptyList(), assignmentIds.filter { it in previousAssignmentIds })
        mockMvc.get("/study-supervisors?startDate=2026-09-14&finishDate=2026-09-15") {
            bearer(userToken)
        }.andExpect {
            jsonPath("$.supervisions[0].supervisors") { isEmpty() }
            jsonPath("$.supervisions[1].id") { value(supervision.id!!) }
            jsonPath("$.supervisions[1].supervisors[0].teacher.name") { value("박성준") }
            jsonPath("$.supervisions[1].supervisors[1].teacher") { value(null as Any?) }
            jsonPath("$.supervisions[1].supervisors[2].teacher.name") { value("이진경") }
        }
    }

    @Test
    fun `날짜를 그대로 두고 같은 층의 교직원만 바꿀 수 있다`() {
        val first = teacher("이진경")
        val second = teacher("박성준")
        val supervision = supervision(LocalDate.of(2026, 9, 14), 2 to first)

        mockMvc.patch("/study-supervisors/${supervision.id}") {
            bearer(adminToken)
            json(body(assignments = listOf(2 to second.id!!)))
        }.andExpect { status { isOk() } }

        assertEquals(LocalDate.of(2026, 9, 14), studySupervisorRepository.findAll().single().date)
        expectDayTeachers("2026-09-14", "박성준", null, null)
    }

    @Test
    fun `다른 정보가 있는 날짜로 바꾸면 SUPERVISION_ALREADY_EXISTS이고 바꾸지 않는다`() {
        val first = teacher("이진경")
        val second = teacher("박성준")
        val supervision = supervision(LocalDate.of(2026, 9, 14), 2 to first)
        supervision(LocalDate.of(2026, 9, 15), 3 to second)

        mockMvc.patch("/study-supervisors/${supervision.id}") {
            bearer(adminToken)
            json(body(date = "2026-09-15", assignments = listOf(4 to second.id!!)))
        }.andExpectConflict()

        expectDayTeachers("2026-09-14", "이진경", null, null)
        expectDayTeachers("2026-09-15", null, "박성준", null)
        // 중복 검사를 빼도 unique 제약으로 409가 나므로, 검사에서 막혔는지는 반영 호출로 확인한다.
        verify(spiedStudySupervisorRepository, never()).flush()
    }

    @Test
    fun `중복 검사를 통과해도 수정한 날짜가 겹치면 SUPERVISION_ALREADY_EXISTS이고 배정을 바꾸지 않는다`() {
        val teacher = teacher("이진경")
        val supervision = supervision(LocalDate.of(2026, 9, 14), 2 to teacher)
        supervision(LocalDate.of(2026, 9, 15), 3 to teacher)
        doReturn(false).`when`(spiedStudySupervisorRepository).existsByDateAndIdNot(anyDate(), anyLong())

        mockMvc.patch("/study-supervisors/${supervision.id}") {
            bearer(adminToken)
            json(body(date = "2026-09-15", assignments = listOf(4 to teacher.id!!)))
        }.andExpectConflict()

        expectDayTeachers("2026-09-14", "이진경", null, null)
        expectDayTeachers("2026-09-15", null, "이진경", null)
    }

    @Test
    fun `수정 중 날짜 제약이 아닌 무결성 오류는 그대로 던진다`() {
        val teacher = teacher("이진경")
        val supervision = supervision(LocalDate.of(2026, 9, 14), 2 to teacher)
        val violation = otherViolation()
        doThrow(violation).`when`(spiedStudySupervisorRepository).flush()

        val thrown = assertFailsWith<ServletException> {
            mockMvc.patch("/study-supervisors/${supervision.id}") {
                bearer(adminToken)
                json(body(date = "2026-09-15", assignments = listOf(3 to teacher.id!!)))
            }
        }
        assertSame(violation, thrown.cause)
    }

    @Test
    fun `없는 정보면 SUPERVISION_NOT_FOUND, 없는 교직원이면 TEACHER_NOT_FOUND이고 바꾸지 않는다`() {
        val teacher = teacher("이진경")
        val supervision = supervision(LocalDate.of(2026, 9, 14), 2 to teacher)

        mockMvc.patch("/study-supervisors/${supervision.id!! + 100}") {
            bearer(adminToken)
            json(body(assignments = listOf(3 to teacher.id!!)))
        }.andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value("SUPERVISION_NOT_FOUND") }
            jsonPath("$.message") { value("존재하지 않는 자습 감독 정보입니다.") }
        }
        mockMvc.patch("/study-supervisors/${supervision.id}") {
            bearer(adminToken)
            json(body(date = "2026-09-15", assignments = listOf(3 to teacher.id!!, 4 to teacher.id!! + 100)))
        }.andExpectTeacherNotFound()

        assertEquals(LocalDate.of(2026, 9, 14), studySupervisorRepository.findAll().single().date)
        expectDayTeachers("2026-09-14", "이진경", null, null)
    }

    @Test
    fun `수정할 값이 형식에 맞지 않거나 id가 숫자가 아니면 INVALID_INPUT`() {
        val teacher = teacher("이진경")
        val supervision = supervision(LocalDate.of(2026, 9, 14), 2 to teacher)

        invalidBodies(teacher.id!!).forEach { (invalid, expectedMessage) ->
            mockMvc.patch("/study-supervisors/${supervision.id}") {
                bearer(adminToken)
                json(invalid)
            }.andExpectInvalidInput(expectedMessage)
        }
        mockMvc.patch("/study-supervisors/abc") {
            bearer(adminToken)
            json(body(assignments = listOf(2 to teacher.id!!)))
        }.andExpectInvalidInput("요청 값이 올바르지 않습니다.")
        expectDayTeachers("2026-09-14", "이진경", null, null)
    }

    // 삭제

    @Test
    fun `삭제하면 배정도 지워져 그 날짜는 빈 배열이고 교직원은 남는다`() {
        val teacher = teacher("이진경")
        val supervision = supervision(LocalDate.of(2026, 9, 14), 2 to teacher, 3 to teacher)
        val other = supervision(LocalDate.of(2026, 9, 15), 2 to teacher)

        mockMvc.delete("/study-supervisors/${supervision.id}") {
            bearer(adminToken)
        }.andExpect {
            status { isOk() }
            jsonPath("$.message") { value("삭제되었습니다.") }
        }

        assertEquals(listOf(other.id), studySupervisorRepository.findAll().map { it.id })
        assertEquals(1, assignmentRepository.count())
        assertEquals(1, teacherRepository.count())
        mockMvc.get("/study-supervisors/day?date=2026-09-14") {
            bearer(userToken)
        }.andExpect { jsonPath("$.supervisors") { isEmpty() } }
    }

    @Test
    fun `없거나 이미 삭제한 정보면 SUPERVISION_NOT_FOUND`() {
        val supervision = supervision(LocalDate.of(2026, 9, 14), 2 to teacher("이진경"))
        mockMvc.delete("/study-supervisors/${supervision.id}") { bearer(adminToken) }.andExpect { status { isOk() } }

        mockMvc.delete("/study-supervisors/${supervision.id}") {
            bearer(adminToken)
        }.andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value("SUPERVISION_NOT_FOUND") }
            jsonPath("$.message") { value("존재하지 않는 자습 감독 정보입니다.") }
        }
        mockMvc.delete("/study-supervisors/abc") { bearer(adminToken) }.andExpectInvalidInput("요청 값이 올바르지 않습니다.")
    }

    @Test
    fun `삭제한 날짜에 다시 등록할 수 있다`() {
        val teacher = teacher("이진경")
        val supervision = supervision(LocalDate.of(2026, 9, 14), 2 to teacher)
        mockMvc.delete("/study-supervisors/${supervision.id}") { bearer(adminToken) }.andExpect { status { isOk() } }

        mockMvc.post("/study-supervisors") {
            bearer(adminToken)
            json(body(assignments = listOf(2 to teacher.id!!)))
        }.andExpect { status { isCreated() } }
        expectDayTeachers("2026-09-14", "이진경", null, null)
    }

    // 교직원 삭제

    @Test
    fun `감독 교직원을 삭제하면 정보는 남고 그 층만 teacher가 null이 된다`() {
        val first = teacher("이진경")
        val second = teacher("박성준")
        val supervision = supervision(LocalDate.of(2026, 9, 14), 2 to first, 3 to second, 4 to first)

        mockMvc.delete("/teachers/${first.id}") {
            bearer(adminToken)
        }.andExpect { status { isOk() } }

        assertEquals(listOf(supervision.id), studySupervisorRepository.findAll().map { it.id })
        expectDayTeachers("2026-09-14", null, "박성준", null)
    }

    @Test
    fun `감독 교직원이 모두 삭제돼도 정보는 남아 수정, 삭제할 수 있다`() {
        val first = teacher("이진경")
        val supervision = supervision(LocalDate.of(2026, 9, 14), 2 to first)
        mockMvc.delete("/teachers/${first.id}") { bearer(adminToken) }.andExpect { status { isOk() } }
        expectDayTeachers("2026-09-14", null, null, null)

        val second = teacher("박성준")
        mockMvc.patch("/study-supervisors/${supervision.id}") {
            bearer(adminToken)
            json(body(assignments = listOf(3 to second.id!!)))
        }.andExpect { status { isOk() } }
        expectDayTeachers("2026-09-14", null, "박성준", null)

        mockMvc.delete("/study-supervisors/${supervision.id}") { bearer(adminToken) }.andExpect { status { isOk() } }
        assertEquals(0, studySupervisorRepository.count())
    }

    // 권한

    @Test
    fun `토큰이 없으면 INVALID_TOKEN`() {
        val teacher = teacher("이진경")
        val supervision = supervision(LocalDate.of(2026, 9, 14), 2 to teacher)

        listOf(
            mockMvc.get("/study-supervisors?startDate=2026-09-14&finishDate=2026-09-14"),
            mockMvc.get("/study-supervisors/day?date=2026-09-14"),
            mockMvc.post("/study-supervisors") { json(body(date = "2026-09-15", assignments = listOf(2 to teacher.id!!))) },
            mockMvc.patch("/study-supervisors/${supervision.id}") { json(body(assignments = listOf(3 to teacher.id!!))) },
            mockMvc.delete("/study-supervisors/${supervision.id}"),
        ).forEach {
            it.andExpect {
                status { isUnauthorized() }
                jsonPath("$.code") { value("INVALID_TOKEN") }
            }
        }
        assertEquals(1, studySupervisorRepository.count())
        expectDayTeachers("2026-09-14", "이진경", null, null)
    }

    @Test
    fun `USER는 등록, 수정, 삭제를 할 수 없다`() {
        val teacher = teacher("이진경")
        val supervision = supervision(LocalDate.of(2026, 9, 14), 2 to teacher)

        listOf(
            mockMvc.post("/study-supervisors") { bearer(userToken); json(body(date = "2026-09-15", assignments = listOf(2 to teacher.id!!))) },
            mockMvc.patch("/study-supervisors/${supervision.id}") { bearer(userToken); json(body(assignments = listOf(3 to teacher.id!!))) },
            mockMvc.delete("/study-supervisors/${supervision.id}") { bearer(userToken) },
        ).forEach {
            it.andExpect {
                status { isForbidden() }
                jsonPath("$.code") { value("FORBIDDEN") }
            }
        }
        assertEquals(1, studySupervisorRepository.count())
        expectDayTeachers("2026-09-14", "이진경", null, null)
    }

    @Test
    fun `ADMIN도 조회할 수 있다`() {
        mockMvc.get("/study-supervisors?startDate=2026-09-14&finishDate=2026-09-14") { bearer(adminToken) }.andExpect { status { isOk() } }
        mockMvc.get("/study-supervisors/day?date=2026-09-14") { bearer(adminToken) }.andExpect { status { isOk() } }
    }

    private fun teacher(name: String) =
        teacherRepository.save(Teacher(name, null, null, TeacherSubject.MATH, "3층 교무실", false, TeacherPosition.MEISTER))

    private fun supervision(date: LocalDate, vararg assignments: Pair<Int, Teacher>): StudySupervisor {
        val supervision = studySupervisorRepository.save(StudySupervisor(date))
        assignmentRepository.saveAll(assignments.map { (floor, teacher) -> StudySupervisorAssignment(supervision, floor, teacher) })
        return supervision
    }

    // 2, 3, 4층 순서로 감독 교직원 이름을 확인한다. null은 배정이 없는 층이다.
    private fun expectDayTeachers(date: String, vararg names: String?) {
        mockMvc.get("/study-supervisors/day?date=$date") {
            bearer(userToken)
        }.andExpect {
            status { isOk() }
            jsonPath("$.supervisors[*].floor") { value(contains(2, 3, 4)) }
            names.forEachIndexed { index, name ->
                if (name == null) {
                    jsonPath("$.supervisors[$index].teacher") { value(null as Any?) }
                } else {
                    jsonPath("$.supervisors[$index].teacher.name") { value(name) }
                }
            }
        }
    }

    private fun queryCount(uri: String): Long {
        val statistics = entityManagerFactory.unwrap(SessionFactory::class.java).statistics
        statistics.clear()
        mockMvc.get(uri) { bearer(userToken) }.andExpect { status { isOk() } }
        return statistics.prepareStatementCount
    }

    private fun invalidBodies(teacherId: Long) = listOf(
        body(date = "2026-13-01", assignments = listOf(2 to teacherId)) to "요청 값이 올바르지 않습니다.",
        body(date = "20260914", assignments = listOf(2 to teacherId)) to "요청 값이 올바르지 않습니다.",
        """{"supervisors":[{"floor":2,"teacherId":$teacherId}]}""" to "요청 값이 올바르지 않습니다.",
        """{"date":"2026-09-14"}""" to "요청 값이 올바르지 않습니다.",
        """{"date":"2026-09-14","supervisors":null}""" to "요청 값이 올바르지 않습니다.",
        """{"date":"2026-09-14","supervisors":[null]}""" to "요청 값이 올바르지 않습니다.",
        """{"date":"2026-09-14","supervisors":[{"teacherId":$teacherId}]}""" to "요청 값이 올바르지 않습니다.",
        """{"date":"2026-09-14","supervisors":[{"floor":2}]}""" to "요청 값이 올바르지 않습니다.",
        """{"date":"2026-09-14","supervisors":[{"floor":"이층","teacherId":$teacherId}]}""" to "요청 값이 올바르지 않습니다.",
        "not json" to "요청 값이 올바르지 않습니다.",
        body(assignments = emptyList()) to "supervisors: ",
        body(assignments = listOf(1 to teacherId)) to "supervisors[0].floor: ",
        body(assignments = listOf(5 to teacherId)) to "supervisors[0].floor: ",
        body(assignments = listOf(2 to teacherId, 3 to teacherId, 2 to teacherId)) to "floorsDistinct: 같은 층을 두 번 보낼 수 없습니다.",
    )

    // existsByDate는 Kotlin에서 LocalDate(non-null)로 선언돼 any()의 null을 받지 못한다. 매처를 등록한 뒤 null 대신 아무 날짜나 넘긴다.
    private fun anyDate(): LocalDate = any(LocalDate::class.java) ?: LocalDate.MIN

    private fun otherViolation() =
        DataIntegrityViolationException("other", ConstraintViolationException("other", SQLException(), "other_constraint"))

    private fun ResultActionsDsl.andExpectConflict() = andExpect {
        status { isConflict() }
        jsonPath("$.code") { value("SUPERVISION_ALREADY_EXISTS") }
        jsonPath("$.message") { value("해당 날짜의 자습 감독 정보가 이미 존재합니다.") }
    }

    private fun ResultActionsDsl.andExpectTeacherNotFound() = andExpect {
        status { isNotFound() }
        jsonPath("$.code") { value("TEACHER_NOT_FOUND") }
    }

    private fun ResultActionsDsl.andExpectInvalidInput(expectedMessage: String) = andExpect {
        status { isBadRequest() }
        jsonPath("$.code") { value("INVALID_INPUT") }
        jsonPath("$.message") { value(containsString(expectedMessage)) }
    }

    private fun MockHttpServletRequestDsl.bearer(token: String) = header("Authorization", "Bearer $token")

    private fun MockHttpServletRequestDsl.json(body: String) {
        contentType = MediaType.APPLICATION_JSON
        content = body
    }

    private fun body(date: String = "2026-09-14", assignments: List<Pair<Int, Long>>) = """
        {
          "date": "$date",
          "supervisors": ${assignments.joinToString(",", "[", "]") { (floor, teacherId) -> """{"floor":$floor,"teacherId":$teacherId}""" }}
        }
    """.trimIndent()
}
