package app.xquare.xquareopenapi.domain.student

import app.xquare.xquareopenapi.domain.user.Role
import app.xquare.xquareopenapi.global.jwt.JwtProvider
import jakarta.servlet.ServletException
import org.hamcrest.Matchers.contains
import org.hamcrest.Matchers.containsString
import app.xquare.xquareopenapi.domain.apikey.ApiKey
import app.xquare.xquareopenapi.domain.apikey.ApiKeyRepository
import app.xquare.xquareopenapi.domain.apikey.ApiKeySecret
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyInt
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
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

@SpringBootTest
@AutoConfigureMockMvc
class StudentTest @Autowired constructor(
    private val mockMvc: MockMvc,
    private val studentRepository: StudentRepository,
    private val apiKeyRepository: ApiKeyRepository,
    jwtProvider: JwtProvider,
) {
    // 동시 등록·수정을 흉내 내려고 중복 검사만 통과시킬 때 쓴다.
    @MockitoSpyBean
    private lateinit var spiedStudentRepository: StudentRepository

    private val adminToken = jwtProvider.generateAccessToken(1L, Role.ADMIN)
    private val userToken = jwtProvider.generateAccessToken(2L, Role.USER)

    // 조회 API는 API 키로만 인증한다.
    @BeforeEach
    fun setUp() {
        apiKeyRepository.save(ApiKey(2L, "테스트", "테스트", ApiKeySecret.hash(RAW_API_KEY)))
    }

    @AfterEach
    fun tearDown() {
        apiKeyRepository.deleteAll()
        studentRepository.deleteAll()
    }

    // 등록

    @Test
    fun `등록하면 저장하고 Location 헤더로 상세조회 URI를 준다`() {
        mockMvc.post("/students") {
            bearer(adminToken)
            json(body())
        }.andExpect {
            status { isCreated() }
            jsonPath("$.message") { value("생성되었습니다.") }
        }.andReturn().response.also { response ->
            val student = studentRepository.findAll().single()
            assertEquals("/students/${student.id}", response.getHeader("Location"))
            assertEquals(2315, student.studentNumber)
            assertEquals("김준서", student.name)
            assertEquals(LocalDate.of(2009, 7, 28), student.birthDate)
            assertEquals("junseo@dsm.hs.kr", student.email)
        }
    }

    @Test
    fun `학번은 1000부터 3999까지 받는다`() {
        listOf(1000, 3999).forEach { studentNumber ->
            mockMvc.post("/students") {
                bearer(adminToken)
                json(body(studentNumber = studentNumber, email = "s$studentNumber@dsm.hs.kr"))
            }.andExpect { status { isCreated() } }
        }
    }

    @Test
    fun `학번이 1000~3999를 벗어나면 INVALID_STUDENT_NUMBER`() {
        listOf(999, 4000, -2315).forEach { studentNumber ->
            mockMvc.post("/students") {
                bearer(adminToken)
                json(body(studentNumber = studentNumber))
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.code") { value("INVALID_STUDENT_NUMBER") }
                jsonPath("$.message") { value("학번은 1000~3999 범위여야 합니다.") }
            }
        }
        assertEquals(0, studentRepository.count())
    }

    @Test
    fun `이미 쓰는 학번이면 STUDENT_NUMBER_ALREADY_EXISTS`() {
        save(2315)

        mockMvc.post("/students") {
            bearer(adminToken)
            json(body(studentNumber = 2315))
        }.andExpectStudentNumberConflict()
        assertEquals(1, studentRepository.count())
        // 중복 검사에서 막아야 운영 DB의 제약 이름이 달라도 409가 난다. 제약에 맡기면 이름을 구분하지 못해 500이 된다.
        verify(spiedStudentRepository, never()).saveAndFlush(any())
    }

    @Test
    fun `동시 등록으로 중복 검사를 통과해도 같은 학번은 STUDENT_NUMBER_ALREADY_EXISTS`() {
        save(2315)
        doReturn(false).`when`(spiedStudentRepository).existsByStudentNumber(anyInt())

        mockMvc.post("/students") {
            bearer(adminToken)
            json(body(studentNumber = 2315))
        }.andExpectStudentNumberConflict()
    }

    @Test
    fun `학번 제약이 아닌 무결성 위반은 원래 예외를 그대로 던진다`() {
        val violation = DataIntegrityViolationException("다른 제약 위반")
        doThrow(violation).`when`(spiedStudentRepository).saveAndFlush(any())

        val thrown = assertFailsWith<ServletException> {
            mockMvc.post("/students") {
                bearer(adminToken)
                json(body())
            }
        }
        assertSame(violation, thrown.cause)
    }

    @Test
    fun `형식에 맞지 않는 입력은 INVALID_INPUT`() {
        listOf(
            body(name = " ") to "name",
            body(name = "가".repeat(11)) to "name",
            body(email = "junseo@gmail.com") to "email",
            body(email = ".junseo@dsm.hs.kr") to "email",
            body(birthDate = "2009-13-01") to "요청 값이 올바르지 않습니다.",
            """{"name":"김준서","birthDate":"2009-07-28","email":"junseo@dsm.hs.kr"}""" to "요청 값이 올바르지 않습니다.",
        ).forEach { (invalid, expectedMessage) ->
            mockMvc.post("/students") {
                bearer(adminToken)
                json(invalid)
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.code") { value("INVALID_INPUT") }
                jsonPath("$.message") { value(containsString(expectedMessage)) }
            }
        }
        assertEquals(0, studentRepository.count())
    }

    @Test
    fun `이름은 10자까지 받는다`() {
        mockMvc.post("/students") {
            bearer(adminToken)
            json(body(name = "가".repeat(10)))
        }.andExpect { status { isCreated() } }
    }

    // 단건 조회

    @Test
    fun `학생을 조회하면 학번에서 학년, 반, 번호를 나눠 준다`() {
        val student = save(2315)

        mockMvc.get("/students/${student.id}") {
            apiKey()
        }.andExpect {
            status { isOk() }
            jsonPath("$.id") { value(student.id!!) }
            jsonPath("$.studentNumber") { value(2315) }
            jsonPath("$.grade") { value(2) }
            jsonPath("$.classNum") { value(3) }
            jsonPath("$.number") { value(15) }
            jsonPath("$.name") { value("김준서") }
            jsonPath("$.birthDate") { value("2009-07-28") }
            jsonPath("$.email") { value("s2315@dsm.hs.kr") }
            // 키가 없으면 PathNotFound로 실패하므로, null 값으로 들어 있는지까지 확인된다.
            jsonPath("$.club") { value(null as Any?) }
        }
    }

    @Test
    fun `반이 0인 학번도 학년, 반, 번호를 나눠 준다`() {
        val student = save(3001)

        mockMvc.get("/students/${student.id}") {
            apiKey()
        }.andExpect {
            jsonPath("$.grade") { value(3) }
            jsonPath("$.classNum") { value(0) }
            jsonPath("$.number") { value(1) }
        }
    }

    @Test
    fun `없는 학생이면 STUDENT_NOT_FOUND`() {
        mockMvc.get("/students/999") {
            apiKey()
        }.andExpectStudentNotFound()
    }

    @Test
    fun `학생 id가 숫자가 아니면 INVALID_INPUT`() {
        mockMvc.get("/students/abc") {
            apiKey()
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("INVALID_INPUT") }
            jsonPath("$.message") { value("요청 값이 올바르지 않습니다.") }
        }
    }

    // 목록 조회

    @Test
    fun `조건 없이 조회하면 학번 순으로 첫 페이지 20명을 준다`() {
        (1101..1125).reversed().forEach { save(it) }

        mockMvc.get("/students") {
            apiKey()
        }.andExpect {
            status { isOk() }
            jsonPath("$.content.length()") { value(20) }
            jsonPath("$.content[0].studentNumber") { value(1101) }
            jsonPath("$.content[19].studentNumber") { value(1120) }
            jsonPath("$.content[0].grade") { value(1) }
            jsonPath("$.page") { value(0) }
            jsonPath("$.size") { value(20) }
            jsonPath("$.totalCount") { value(25) }
            jsonPath("$.totalPage") { value(2) }
        }
    }

    @Test
    fun `page와 size로 원하는 페이지를 준다`() {
        (1101..1105).forEach { save(it) }

        mockMvc.get("/students?page=1&size=2") {
            apiKey()
        }.andExpect {
            jsonPath("$.content[*].studentNumber") { value(contains(1103, 1104)) }
            jsonPath("$.page") { value(1) }
            jsonPath("$.size") { value(2) }
            jsonPath("$.totalCount") { value(5) }
            jsonPath("$.totalPage") { value(3) }
        }
    }

    @Test
    fun `이름 일부로 찾는다`() {
        save(1101, name = "김민준")
        save(1102, name = "이서준")
        save(1103, name = "민서")

        expectStudentNumbers("name=민", 1101, 1103)
        expectStudentNumbers("name=김민준", 1101)
    }

    @Test
    fun `학년과 반으로 찾는다`() {
        listOf(1999, 2000, 2101, 2199, 2200, 2999, 3000, 3101).forEach { save(it) }

        expectStudentNumbers("grade=2", 2000, 2101, 2199, 2200, 2999)
        expectStudentNumbers("classNum=1", 2101, 2199, 3101)
        expectStudentNumbers("grade=2&classNum=1", 2101, 2199)
        expectStudentNumbers("grade=1&classNum=9", 1999)
        expectStudentNumbers("grade=3", 3000, 3101)
        expectStudentNumbers("classNum=0", 2000, 3000)
    }

    @Test
    fun `이름의 %, _, !는 와일드카드가 아닌 글자 그대로 찾는다`() {
        save(1101, name = "김%준")
        save(1102, name = "김_준")
        save(1103, name = "김!준")
        save(1104, name = "김민준")

        // URI 문자열에 %를 넣으면 MockMvc가 다시 인코딩하므로 param으로 넘긴다.
        listOf("%" to 1101, "_" to 1102, "!" to 1103).forEach { (name, expected) ->
            mockMvc.get("/students") {
                apiKey()
                param("name", name)
            }.andExpect {
                jsonPath("$.content[*].studentNumber") { value(contains(expected)) }
            }
        }
        mockMvc.get("/students") {
            apiKey()
            param("name", "!%")
        }.andExpect {
            jsonPath("$.content") { isEmpty() }
        }
        expectStudentNumbers("name=김", 1101, 1102, 1103, 1104)
    }

    @Test
    fun `조건을 여러 개 주면 모두 만족하는 학생만 준다`() {
        save(2101, name = "김민준")
        save(2102, name = "이민준")
        save(3101, name = "김민준2")

        expectStudentNumbers("name=김&grade=2", 2101)
    }

    @Test
    fun `page나 size가 범위를 벗어나거나 조건이 숫자가 아니면 INVALID_INPUT`() {
        listOf(
            "page=-1" to "page: ",
            "size=0" to "size: ",
            "size=101" to "size: ",
            "grade=0" to "grade: ",
            "grade=4" to "grade: ",
            // 범위를 막기 전에는 쿼리의 grade * 1000이 int를 넘어 500이 났다.
            "grade=2147483647" to "grade: ",
            "classNum=-1" to "classNum: ",
            "classNum=10" to "classNum: ",
            "grade=abc" to "grade: 형식이 올바르지 않습니다.",
            "classNum=abc" to "classNum: 형식이 올바르지 않습니다.",
            "page=abc" to "page: 형식이 올바르지 않습니다.",
            // page × size가 int를 넘으면 Spring Data가 offset을 만들지 못해 500이 났다.
            "page=21474837&size=100" to "page: ",
        ).forEach { (query, expectedMessage) ->
            mockMvc.get("/students?$query") {
                apiKey()
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.code") { value("INVALID_INPUT") }
                jsonPath("$.message") { value(containsString(expectedMessage)) }
            }
        }
    }

    @Test
    fun `size는 100까지, page는 offset이 int를 넘지 않는 데까지 받는다`() {
        mockMvc.get("/students?page=21474836&size=100") {
            apiKey()
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
        val student = save(2315)

        mockMvc.patch("/students/${student.id}") {
            bearer(adminToken)
            json(body(studentNumber = 3101, name = "김준서2", birthDate = "2008-01-02", email = "new@dsm.hs.kr"))
        }.andExpect {
            status { isOk() }
            jsonPath("$.message") { value("수정되었습니다.") }
        }

        val updated = studentRepository.findById(student.id!!).get()
        assertEquals(3101, updated.studentNumber)
        assertEquals("김준서2", updated.name)
        assertEquals(LocalDate.of(2008, 1, 2), updated.birthDate)
        assertEquals("new@dsm.hs.kr", updated.email)
    }

    @Test
    fun `학번을 그대로 두고 다른 값만 수정할 수 있다`() {
        val student = save(2315)

        mockMvc.patch("/students/${student.id}") {
            bearer(adminToken)
            json(body(studentNumber = 2315, name = "새이름"))
        }.andExpect { status { isOk() } }

        assertEquals("새이름", studentRepository.findById(student.id!!).get().name)
    }

    @Test
    fun `다른 학생의 학번으로 바꾸면 STUDENT_NUMBER_ALREADY_EXISTS`() {
        save(2316)
        val student = save(2315)

        mockMvc.patch("/students/${student.id}") {
            bearer(adminToken)
            json(body(studentNumber = 2316))
        }.andExpectStudentNumberConflict()
        assertEquals(2315, studentRepository.findById(student.id!!).get().studentNumber)
        verify(spiedStudentRepository, never()).flush()
    }

    @Test
    fun `동시 수정으로 중복 검사를 통과해도 같은 학번은 STUDENT_NUMBER_ALREADY_EXISTS`() {
        save(2316)
        val student = save(2315)
        doReturn(false).`when`(spiedStudentRepository).existsByStudentNumber(anyInt())

        mockMvc.patch("/students/${student.id}") {
            bearer(adminToken)
            json(body(studentNumber = 2316))
        }.andExpectStudentNumberConflict()
        assertEquals(2315, studentRepository.findById(student.id!!).get().studentNumber)
    }

    @Test
    fun `수정 중 학번 제약이 아닌 무결성 위반은 원래 예외를 그대로 던진다`() {
        val student = save(2315)
        val violation = DataIntegrityViolationException("다른 제약 위반")
        doThrow(violation).`when`(spiedStudentRepository).flush()

        val thrown = assertFailsWith<ServletException> {
            mockMvc.patch("/students/${student.id}") {
                bearer(adminToken)
                json(body())
            }
        }
        assertSame(violation, thrown.cause)
    }

    @Test
    fun `수정할 학번이 1000~3999를 벗어나면 INVALID_STUDENT_NUMBER`() {
        val student = save(2315)

        mockMvc.patch("/students/${student.id}") {
            bearer(adminToken)
            json(body(studentNumber = 4000))
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("INVALID_STUDENT_NUMBER") }
        }
        assertEquals(2315, studentRepository.findById(student.id!!).get().studentNumber)
    }

    @Test
    fun `없는 학생을 수정하면 STUDENT_NOT_FOUND`() {
        mockMvc.patch("/students/999") {
            bearer(adminToken)
            json(body())
        }.andExpectStudentNotFound()
    }

    @Test
    fun `수정할 값이 형식에 맞지 않으면 INVALID_INPUT`() {
        val student = save(2315)

        mockMvc.patch("/students/${student.id}") {
            bearer(adminToken)
            json(body(email = "junseo@gmail.com"))
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("INVALID_INPUT") }
        }
    }

    // 삭제

    @Test
    fun `삭제하면 다시 조회할 수 없다`() {
        val student = save(2315)

        mockMvc.delete("/students/${student.id}") {
            bearer(adminToken)
        }.andExpect {
            status { isOk() }
            jsonPath("$.message") { value("삭제되었습니다.") }
        }

        assertEquals(0, studentRepository.count())
        mockMvc.delete("/students/${student.id}") {
            bearer(adminToken)
        }.andExpectStudentNotFound()
    }

    // 권한

    @Test
    fun `토큰이 없으면 등록, 수정, 삭제는 INVALID_TOKEN`() {
        val student = save(2315)

        listOf(
            mockMvc.post("/students") { json(body()) },
            mockMvc.patch("/students/${student.id}") { json(body()) },
            mockMvc.delete("/students/${student.id}"),
        ).forEach {
            it.andExpect {
                status { isUnauthorized() }
                jsonPath("$.code") { value("INVALID_TOKEN") }
            }
        }
    }

    @Test
    fun `API 키가 없거나 틀리면 조회는 INVALID_API_KEY`() {
        val student = save(2315)

        listOf(
            mockMvc.get("/students"),
            mockMvc.get("/students/${student.id}"),
            mockMvc.get("/students") { header("X-API-Key", "xq_wrong") },
        ).forEach {
            it.andExpect {
                status { isUnauthorized() }
                jsonPath("$.code") { value("INVALID_API_KEY") }
                jsonPath("$.message") { value("API 키가 없거나 유효하지 않습니다.") }
            }
        }
    }

    @Test
    fun `USER는 등록, 수정, 삭제를 할 수 없다`() {
        val student = save(2315)

        listOf(
            mockMvc.post("/students") { bearer(userToken); json(body(studentNumber = 2316)) },
            mockMvc.patch("/students/${student.id}") { bearer(userToken); json(body(name = "바뀜")) },
            mockMvc.delete("/students/${student.id}") { bearer(userToken) },
        ).forEach {
            it.andExpect {
                status { isForbidden() }
                jsonPath("$.code") { value("FORBIDDEN") }
                jsonPath("$.message") { value("권한이 없습니다.") }
            }
        }
        assertEquals("김준서", studentRepository.findAll().single().name)
    }

    @Test
    fun `토큰만으로는 USER든 ADMIN이든 조회할 수 없다`() {
        val student = save(2315)

        listOf(userToken, adminToken).forEach { token ->
            mockMvc.get("/students/${student.id}") { bearer(token) }.andExpect {
                status { isUnauthorized() }
                jsonPath("$.code") { value("INVALID_API_KEY") }
            }
        }
    }

    private fun save(studentNumber: Int, name: String = "김준서") =
        studentRepository.save(Student(studentNumber, name, LocalDate.of(2009, 7, 28), "s$studentNumber@dsm.hs.kr"))

    private fun expectStudentNumbers(query: String, vararg expected: Int) {
        mockMvc.get("/students?$query") {
            apiKey()
        }.andExpect {
            status { isOk() }
            jsonPath("$.content[*].studentNumber") { value(contains(*expected.toTypedArray())) }
            jsonPath("$.totalCount") { value(expected.size) }
        }
    }

    private fun ResultActionsDsl.andExpectStudentNumberConflict() = andExpect {
        status { isConflict() }
        jsonPath("$.code") { value("STUDENT_NUMBER_ALREADY_EXISTS") }
        jsonPath("$.message") { value("이미 사용 중인 학번입니다.") }
    }

    private fun ResultActionsDsl.andExpectStudentNotFound() = andExpect {
        status { isNotFound() }
        jsonPath("$.code") { value("STUDENT_NOT_FOUND") }
        jsonPath("$.message") { value("존재하지 않는 학생입니다.") }
    }

    private fun org.springframework.test.web.servlet.MockHttpServletRequestDsl.bearer(token: String) =
        header("Authorization", "Bearer $token")

    private fun org.springframework.test.web.servlet.MockHttpServletRequestDsl.json(body: String) {
        contentType = MediaType.APPLICATION_JSON
        content = body
    }

    private fun MockHttpServletRequestDsl.apiKey() = header("X-API-Key", RAW_API_KEY)

    private fun body(
        studentNumber: Int = 2315,
        name: String = "김준서",
        birthDate: String = "2009-07-28",
        email: String = "junseo@dsm.hs.kr",
    ) = """
        {
          "studentNumber": $studentNumber,
          "name": "$name",
          "birthDate": "$birthDate",
          "email": "$email"
        }
    """.trimIndent()

    companion object {
        private const val RAW_API_KEY = "xq_test-api-key"
    }
}
