package app.xquare.xquareopenapi.domain.teacher

import app.xquare.xquareopenapi.domain.user.Role
import app.xquare.xquareopenapi.global.jwt.JwtProvider
import org.hamcrest.Matchers.contains
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
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@SpringBootTest
@AutoConfigureMockMvc
class TeacherTest @Autowired constructor(
    private val mockMvc: MockMvc,
    private val teacherRepository: TeacherRepository,
    jwtProvider: JwtProvider,
) {
    private val adminToken = jwtProvider.generateAccessToken(1L, Role.ADMIN)
    private val userToken = jwtProvider.generateAccessToken(2L, Role.USER)

    @AfterEach
    fun tearDown() {
        teacherRepository.deleteAll()
    }

    // 등록

    @Test
    fun `등록하면 저장하고 Location 헤더로 상세조회 URI를 준다`() {
        mockMvc.post("/teachers") {
            bearer(adminToken)
            json(body())
        }.andExpect {
            status { isCreated() }
            jsonPath("$.message") { value("생성되었습니다.") }
        }.andReturn().response.also { response ->
            val teacher = teacherRepository.findAll().single()
            assertEquals("/teachers/${teacher.id}", response.getHeader("Location"))
            assertEquals("홍길동", teacher.name)
            assertEquals(1, teacher.grade)
            assertEquals(2, teacher.classNum)
            assertEquals(TeacherSubject.MATH, teacher.subject)
            assertEquals("3층 교무실", teacher.office)
            assertTrue(teacher.isManager)
            assertEquals(TeacherPosition.ACADEMIC_AFFAIRS, teacher.position)
        }
    }

    @Test
    fun `담임이 아니면 학년과 반을 비워서 등록한다`() {
        listOf(body(grade = null, classNum = null), bodyWithoutHomeroom()).forEach { request ->
            mockMvc.post("/teachers") {
                bearer(adminToken)
                json(request)
            }.andExpect { status { isCreated() } }
        }

        teacherRepository.findAll().forEach {
            assertNull(it.grade)
            assertNull(it.classNum)
        }
    }

    @Test
    fun `학년과 반은 1학년 1반부터 3학년 9반까지 받는다`() {
        listOf(1 to 1, 3 to 9).forEach { (grade, classNum) ->
            mockMvc.post("/teachers") {
                bearer(adminToken)
                json(body(grade = grade, classNum = classNum))
            }.andExpect { status { isCreated() } }
        }
    }

    @Test
    fun `이름은 10자, 교무실은 30자까지 받는다`() {
        mockMvc.post("/teachers") {
            bearer(adminToken)
            json(body(name = "가".repeat(10), office = "가".repeat(30)))
        }.andExpect { status { isCreated() } }
    }

    @Test
    fun `형식에 맞지 않는 입력은 INVALID_INPUT`() {
        listOf(
            body(name = " ") to "name",
            body(name = "가".repeat(11)) to "name",
            body(office = " ") to "office",
            body(office = "가".repeat(31)) to "office",
            body(grade = 0) to "grade",
            body(grade = 4) to "grade",
            body(classNum = 0) to "classNum",
            body(classNum = 10) to "classNum",
            body(grade = 1, classNum = null) to "grade와 classNum은 함께 보내거나 함께 비워야 합니다.",
            body(grade = null, classNum = 2) to "grade와 classNum은 함께 보내거나 함께 비워야 합니다.",
            body(subject = "PHYSICS") to "요청 값이 올바르지 않습니다.",
            body(position = "PRINCIPAL") to "요청 값이 올바르지 않습니다.",
            """{"name":"홍길동","subject":"MATH","office":"3층 교무실","position":"MEISTER"}""" to "요청 값이 올바르지 않습니다.",
        ).forEach { (invalid, expectedMessage) ->
            mockMvc.post("/teachers") {
                bearer(adminToken)
                json(invalid)
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.code") { value("INVALID_INPUT") }
                jsonPath("$.message") { value(containsString(expectedMessage)) }
            }
        }
        assertEquals(0, teacherRepository.count())
    }

    // 단건 조회

    @Test
    fun `교직원을 조회한다`() {
        val teacher = save()

        mockMvc.get("/teachers/${teacher.id}") {
            bearer(userToken)
        }.andExpect {
            status { isOk() }
            jsonPath("$.id") { value(teacher.id!!) }
            jsonPath("$.name") { value("홍길동") }
            jsonPath("$.grade") { value(1) }
            jsonPath("$.classNum") { value(2) }
            jsonPath("$.subject") { value("MATH") }
            jsonPath("$.office") { value("3층 교무실") }
            jsonPath("$.isManager") { value(true) }
            jsonPath("$.position") { value("ACADEMIC_AFFAIRS") }
            // 키가 없으면 PathNotFound로 실패하므로, null 값으로 들어 있는지까지 확인된다.
            jsonPath("$.club") { value(null as Any?) }
        }
    }

    @Test
    fun `담임이 아니면 학년과 반을 null로 준다`() {
        val teacher = save(grade = null, classNum = null, isManager = false)

        mockMvc.get("/teachers/${teacher.id}") {
            bearer(userToken)
        }.andExpect {
            jsonPath("$.grade") { value(null as Any?) }
            jsonPath("$.classNum") { value(null as Any?) }
            jsonPath("$.isManager") { value(false) }
        }
    }

    @Test
    fun `없는 교직원이면 TEACHER_NOT_FOUND`() {
        mockMvc.get("/teachers/999") {
            bearer(userToken)
        }.andExpectTeacherNotFound()
    }

    @Test
    fun `교직원 id가 숫자가 아니면 INVALID_INPUT`() {
        mockMvc.get("/teachers/abc") {
            bearer(userToken)
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("INVALID_INPUT") }
        }
    }

    // 목록 조회

    @Test
    fun `조건 없이 조회하면 등록 순으로 첫 페이지 20명을 준다`() {
        val teachers = (1..25).map { save(name = "교사$it") }

        mockMvc.get("/teachers") {
            bearer(userToken)
        }.andExpect {
            status { isOk() }
            jsonPath("$.content.length()") { value(20) }
            jsonPath("$.content[0].id") { value(teachers.first().id!!) }
            jsonPath("$.content[0].name") { value("교사1") }
            jsonPath("$.content[19].name") { value("교사20") }
            jsonPath("$.page") { value(0) }
            jsonPath("$.size") { value(20) }
            jsonPath("$.totalCount") { value(25) }
            jsonPath("$.totalPage") { value(2) }
        }
    }

    @Test
    fun `page와 size로 원하는 페이지를 준다`() {
        (1..5).forEach { save(name = "교사$it") }

        mockMvc.get("/teachers?page=1&size=2") {
            bearer(userToken)
        }.andExpect {
            jsonPath("$.content[*].name") { value(contains("교사3", "교사4")) }
            jsonPath("$.page") { value(1) }
            jsonPath("$.size") { value(2) }
            jsonPath("$.totalCount") { value(5) }
            jsonPath("$.totalPage") { value(3) }
        }
    }

    @Test
    fun `이름은 정확히 일치하는 교직원만 찾는다`() {
        save(name = "홍길동")
        save(name = "홍길동동")
        save(name = "김길동")

        expectNames("name=홍길동", "홍길동")
        expectNames("name=길동")
    }

    @Test
    fun `담당 학급, 과목으로 찾는다`() {
        save(name = "가", grade = 1, classNum = 2, subject = TeacherSubject.MATH)
        save(name = "나", grade = 1, classNum = 3, subject = TeacherSubject.JAVA)
        save(name = "다", grade = 2, classNum = 2, subject = TeacherSubject.MATH)
        save(name = "라", grade = null, classNum = null, subject = TeacherSubject.MUSIC)

        expectNames("grade=1", "가", "나")
        expectNames("classNum=2", "가", "다")
        expectNames("grade=1&classNum=2", "가")
        expectNames("subject=MATH", "가", "다")
        expectNames("subject=MUSIC", "라")
    }

    @Test
    fun `교무실은 일부만 같아도 찾는다`() {
        save(name = "가", office = "3층 교무실")
        save(name = "나", office = "2층 교무실")
        save(name = "다", office = "본관 3층 교무실")

        expectNames("office=3층", "가", "다")
        expectNames("office=2층 교무실", "나")
    }

    @Test
    fun `조건을 여러 개 주면 모두 만족하는 교직원만 준다`() {
        save(name = "홍길동", grade = 1, classNum = 2, office = "3층 교무실")
        save(name = "홍길동", grade = 2, classNum = 2, office = "3층 교무실")
        save(name = "김길동", grade = 1, classNum = 2, office = "3층 교무실")

        expectNames("name=홍길동&grade=1&office=교무실", "홍길동")
    }

    @Test
    fun `page나 size가 범위를 벗어나거나 조건 형식이 틀리면 INVALID_INPUT`() {
        listOf(
            "page=-1" to "page: ",
            "size=0" to "size: ",
            "size=101" to "size: ",
            "grade=abc" to "grade: 형식이 올바르지 않습니다.",
            "subject=PHYSICS" to "subject: 형식이 올바르지 않습니다.",
            "page=21474837&size=100" to "page: ",
        ).forEach { (query, expectedMessage) ->
            mockMvc.get("/teachers?$query") {
                bearer(userToken)
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.code") { value("INVALID_INPUT") }
                jsonPath("$.message") { value(containsString(expectedMessage)) }
            }
        }
    }

    @Test
    fun `size는 100까지, page는 offset이 int를 넘지 않는 데까지 받는다`() {
        mockMvc.get("/teachers?page=21474836&size=100") {
            bearer(userToken)
        }.andExpect {
            status { isOk() }
            jsonPath("$.page") { value(21474836) }
            jsonPath("$.size") { value(100) }
            jsonPath("$.content") { isEmpty() }
        }
    }

    // 수정

    @Test
    fun `수정하면 모든 값을 바꾼다`() {
        val teacher = save()

        mockMvc.patch("/teachers/${teacher.id}") {
            bearer(adminToken)
            json(
                body(
                    name = "김선생",
                    grade = 3,
                    classNum = 4,
                    subject = "JAVA",
                    office = "2층 교무실",
                    isManager = false,
                    position = "MEISTER",
                ),
            )
        }.andExpect {
            status { isOk() }
            jsonPath("$.message") { value("수정되었습니다.") }
        }

        val updated = teacherRepository.findById(teacher.id!!).get()
        assertEquals("김선생", updated.name)
        assertEquals(3, updated.grade)
        assertEquals(4, updated.classNum)
        assertEquals(TeacherSubject.JAVA, updated.subject)
        assertEquals("2층 교무실", updated.office)
        assertFalse(updated.isManager)
        assertEquals(TeacherPosition.MEISTER, updated.position)
    }

    @Test
    fun `학년과 반을 null로 수정하면 담임에서 빠진다`() {
        val teacher = save()

        mockMvc.patch("/teachers/${teacher.id}") {
            bearer(adminToken)
            json(body(grade = null, classNum = null))
        }.andExpect { status { isOk() } }

        val updated = teacherRepository.findById(teacher.id!!).get()
        assertNull(updated.grade)
        assertNull(updated.classNum)
        assertTrue(updated.isManager)
    }

    @Test
    fun `없는 교직원을 수정하면 TEACHER_NOT_FOUND`() {
        mockMvc.patch("/teachers/999") {
            bearer(adminToken)
            json(body())
        }.andExpectTeacherNotFound()
    }

    @Test
    fun `수정할 값이 형식에 맞지 않으면 INVALID_INPUT`() {
        val teacher = save()

        mockMvc.patch("/teachers/${teacher.id}") {
            bearer(adminToken)
            json(body(grade = 1, classNum = null))
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("INVALID_INPUT") }
        }
        assertEquals(2, teacherRepository.findById(teacher.id!!).get().classNum)
    }

    // 삭제

    @Test
    fun `삭제하면 다시 조회할 수 없다`() {
        val teacher = save()

        mockMvc.delete("/teachers/${teacher.id}") {
            bearer(adminToken)
        }.andExpect {
            status { isOk() }
            jsonPath("$.message") { value("삭제되었습니다.") }
        }

        assertEquals(0, teacherRepository.count())
        mockMvc.delete("/teachers/${teacher.id}") {
            bearer(adminToken)
        }.andExpectTeacherNotFound()
    }

    // 권한

    @Test
    fun `토큰이 없으면 INVALID_TOKEN`() {
        val teacher = save()

        listOf(
            mockMvc.get("/teachers"),
            mockMvc.get("/teachers/${teacher.id}"),
            mockMvc.post("/teachers") { json(body()) },
            mockMvc.patch("/teachers/${teacher.id}") { json(body()) },
            mockMvc.delete("/teachers/${teacher.id}"),
        ).forEach {
            it.andExpect {
                status { isUnauthorized() }
                jsonPath("$.code") { value("INVALID_TOKEN") }
            }
        }
    }

    @Test
    fun `USER는 등록, 수정, 삭제를 할 수 없다`() {
        val teacher = save()

        listOf(
            mockMvc.post("/teachers") { bearer(userToken); json(body()) },
            mockMvc.patch("/teachers/${teacher.id}") { bearer(userToken); json(body(name = "바뀜")) },
            mockMvc.delete("/teachers/${teacher.id}") { bearer(userToken) },
        ).forEach {
            it.andExpect {
                status { isForbidden() }
                jsonPath("$.code") { value("FORBIDDEN") }
            }
        }
        assertEquals("홍길동", teacherRepository.findAll().single().name)
    }

    @Test
    fun `ADMIN도 조회할 수 있다`() {
        val teacher = save()

        mockMvc.get("/teachers") { bearer(adminToken) }.andExpect { status { isOk() } }
        mockMvc.get("/teachers/${teacher.id}") { bearer(adminToken) }.andExpect { status { isOk() } }
    }

    private fun save(
        name: String = "홍길동",
        grade: Int? = 1,
        classNum: Int? = 2,
        subject: TeacherSubject = TeacherSubject.MATH,
        office: String = "3층 교무실",
        isManager: Boolean = true,
        position: TeacherPosition = TeacherPosition.ACADEMIC_AFFAIRS,
    ) = teacherRepository.save(Teacher(name, grade, classNum, subject, office, isManager, position))

    private fun expectNames(query: String, vararg expected: String) {
        mockMvc.get("/teachers?$query") {
            bearer(userToken)
        }.andExpect {
            status { isOk() }
            if (expected.isEmpty()) {
                jsonPath("$.content") { isEmpty() }
            } else {
                jsonPath("$.content[*].name") { value(contains(*expected)) }
            }
            jsonPath("$.totalCount") { value(expected.size) }
        }
    }

    private fun ResultActionsDsl.andExpectTeacherNotFound() = andExpect {
        status { isNotFound() }
        jsonPath("$.code") { value("TEACHER_NOT_FOUND") }
        jsonPath("$.message") { value("존재하지 않는 교직원입니다.") }
    }

    private fun MockHttpServletRequestDsl.bearer(token: String) = header("Authorization", "Bearer $token")

    private fun MockHttpServletRequestDsl.json(body: String) {
        contentType = MediaType.APPLICATION_JSON
        content = body
    }

    private fun bodyWithoutHomeroom() =
        """{"name":"홍길동","subject":"MATH","office":"3층 교무실","isManager":true,"position":"ACADEMIC_AFFAIRS"}"""

    private fun body(
        name: String = "홍길동",
        grade: Int? = 1,
        classNum: Int? = 2,
        subject: String = "MATH",
        office: String = "3층 교무실",
        isManager: Boolean = true,
        position: String = "ACADEMIC_AFFAIRS",
    ) = """
        {
          "name": "$name",
          "grade": $grade,
          "classNum": $classNum,
          "subject": "$subject",
          "office": "$office",
          "isManager": $isManager,
          "position": "$position"
        }
    """.trimIndent()
}
