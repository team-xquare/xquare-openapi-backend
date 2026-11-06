package app.xquare.xquareopenapi.domain.club

import app.xquare.xquareopenapi.domain.student.Student
import app.xquare.xquareopenapi.domain.student.StudentRepository
import app.xquare.xquareopenapi.domain.teacher.Teacher
import app.xquare.xquareopenapi.domain.teacher.TeacherPosition
import app.xquare.xquareopenapi.domain.teacher.TeacherRepository
import app.xquare.xquareopenapi.domain.teacher.TeacherSubject
import app.xquare.xquareopenapi.domain.user.Role
import app.xquare.xquareopenapi.global.jwt.JwtProvider
import jakarta.persistence.EntityManagerFactory
import jakarta.servlet.ServletException
import org.hibernate.SessionFactory
import org.hamcrest.Matchers.contains
import org.hamcrest.Matchers.containsString
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
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

// 목록 조회의 쿼리 수를 세려고 Hibernate 통계를 켠다. 스파이 빈 때문에 어차피 이 테스트만의 컨텍스트라 다른 테스트에 영향이 없다.
@SpringBootTest(properties = ["spring.jpa.properties.hibernate.generate_statistics=true"])
@AutoConfigureMockMvc
class ClubTest @Autowired constructor(
    private val mockMvc: MockMvc,
    private val entityManagerFactory: EntityManagerFactory,
    private val clubRepository: ClubRepository,
    private val studentRepository: StudentRepository,
    private val teacherRepository: TeacherRepository,
    jwtProvider: JwtProvider,
) {
    // 동시 등록·수정을 흉내 내려고 중복 검사만 통과시킬 때 쓴다.
    @MockitoSpyBean
    private lateinit var spiedClubRepository: ClubRepository

    private val adminToken = jwtProvider.generateAccessToken(1L, Role.ADMIN)
    private val userToken = jwtProvider.generateAccessToken(2L, Role.USER)

    @AfterEach
    fun tearDown() {
        clubRepository.deleteAll()
        studentRepository.deleteAll()
        teacherRepository.deleteAll()
    }

    // 등록

    @Test
    fun `등록하면 저장하고 Location 헤더로 상세조회 URI를 준다`() {
        val manager = student(2315, "김준서")
        val member = student(2101, "박성준")
        val teacher = teacher("이진경")

        val location = mockMvc.post("/clubs") {
            bearer(adminToken)
            json(body(manager = manager.id!!, member = listOf(manager.id!!, member.id!!), teacher = teacher.id))
        }.andExpect {
            status { isCreated() }
            jsonPath("$.message") { value("생성되었습니다.") }
        }.andReturn().response.getHeader("Location")

        val club = clubRepository.findAll().single()
        assertEquals("/clubs/${club.id}", location)
        assertEquals("DMS", club.name)
        assertEquals("logo.png", club.logo)
        assertEquals(ClubRoom.ROOM_2_1, club.room)
        mockMvc.get("/clubs/${club.id}") {
            bearer(userToken)
        }.andExpect {
            jsonPath("$.manager.id") { value(manager.id!!) }
            jsonPath("$.member[*].id") { value(contains(member.id!!.toInt(), manager.id!!.toInt())) }
            jsonPath("$.teacher.id") { value(teacher.id!!) }
        }
    }

    @Test
    fun `담당 선생님은 빼거나 null로 보내도 등록된다`() {
        val first = student(2315)
        val second = student(2316)

        mockMvc.post("/clubs") {
            bearer(adminToken)
            json(body(name = "가", room = "ROOM_2_1", manager = first.id!!, member = listOf(first.id!!), teacher = null))
        }.andExpect { status { isCreated() } }
        mockMvc.post("/clubs") {
            bearer(adminToken)
            json(
                """{"name":"나","logo":"logo.png","room":"ROOM_2_2","manager":${second.id},"member":[${second.id}]}""",
            )
        }.andExpect { status { isCreated() } }

        clubRepository.findAll().forEach { mockMvc.get("/clubs/${it.id}") { bearer(userToken) }.andExpect { jsonPath("$.teacher") { value(null as Any?) } } }
    }

    @Test
    fun `member에 같은 학생을 여러 번 넣으면 한 번으로 친다`() {
        val manager = student(2315)
        val member = student(2316)

        mockMvc.post("/clubs") {
            bearer(adminToken)
            json(body(manager = manager.id!!, member = listOf(manager.id!!, member.id!!, member.id!!, manager.id!!)))
        }.andExpect { status { isCreated() } }

        val club = clubRepository.findAll().single()
        mockMvc.get("/clubs/${club.id}") {
            bearer(userToken)
        }.andExpect { jsonPath("$.member.length()") { value(2) } }
    }

    @Test
    fun `이름은 20자, 로고는 255자, 구성원은 50명까지 받는다`() {
        val students = (1..50).map { student(2100 + it) }

        mockMvc.post("/clubs") {
            bearer(adminToken)
            json(body(name = "가".repeat(20), logo = "a".repeat(255), manager = students.first().id!!, member = students.map { it.id!! }))
        }.andExpect { status { isCreated() } }
    }

    @Test
    fun `팀장이 구성원에 없으면 MANAGER_NOT_IN_MEMBER`() {
        val manager = student(2315)
        val member = student(2316)

        listOf(listOf(member.id!!), emptyList()).forEach { members ->
            mockMvc.post("/clubs") {
                bearer(adminToken)
                json(body(manager = manager.id!!, member = members))
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.code") { value("MANAGER_NOT_IN_MEMBER") }
                jsonPath("$.message") { value("팀장은 구성원에 포함돼야 합니다.") }
            }
        }
        assertEquals(0, clubRepository.count())
    }

    @Test
    fun `없는 학생이나 교직원을 넣으면 STUDENT_NOT_FOUND, TEACHER_NOT_FOUND`() {
        val manager = student(2315)

        mockMvc.post("/clubs") {
            bearer(adminToken)
            json(body(manager = manager.id!!, member = listOf(manager.id!!, 999_999)))
        }.andExpectStudentNotFound()
        mockMvc.post("/clubs") {
            bearer(adminToken)
            json(body(manager = manager.id!!, member = listOf(manager.id!!), teacher = 999_999))
        }.andExpectTeacherNotFound()
        assertEquals(0, clubRepository.count())
    }

    @Test
    fun `다른 동아리가 쓰는 교실이면 ROOM_ALREADY_IN_USE`() {
        club(room = ClubRoom.ROOM_2_1)
        val manager = student(3101)

        mockMvc.post("/clubs") {
            bearer(adminToken)
            json(body(name = "새 동아리", room = "ROOM_2_1", manager = manager.id!!, member = listOf(manager.id!!)))
        }.andExpectRoomInUse()
        assertEquals(1, clubRepository.count())
        // 검사에서 막아야 운영 DB의 제약 이름이 달라도 400이 난다. 제약에 맡기면 이름을 구분하지 못해 500이 된다.
        verify(spiedClubRepository, never()).saveAndFlush(any())
    }

    @Test
    fun `다른 동아리에 속한 학생이나 담당 교직원을 넣으면 STUDENT_ALREADY_IN_CLUB, TEACHER_ALREADY_IN_CLUB`() {
        val other = club(room = ClubRoom.ROOM_2_1, withTeacher = true)
        val otherMember = studentRepository.findAllByClubIdIn(listOf(other.id!!)).first()
        val otherTeacherId = teacherRepository.findAll().single().id!!
        val manager = student(3101)

        mockMvc.post("/clubs") {
            bearer(adminToken)
            json(body(room = "ROOM_3_1", manager = manager.id!!, member = listOf(manager.id!!, otherMember.id!!)))
        }.andExpectStudentInClub()
        mockMvc.post("/clubs") {
            bearer(adminToken)
            json(body(room = "ROOM_3_1", manager = manager.id!!, member = listOf(manager.id!!), teacher = otherTeacherId))
        }.andExpectTeacherInClub()
        assertEquals(1, clubRepository.count())
        expectMemberNames(other.id!!, "박성준", "김준서")
        verify(spiedClubRepository, never()).saveAndFlush(any())
    }

    @Test
    fun `동시 등록으로 중복 검사를 통과해도 교실이나 교직원이 겹치면 같은 에러를 준다`() {
        club(room = ClubRoom.ROOM_2_1, withTeacher = true)
        val teacherId = teacherRepository.findAll().single().id!!
        val manager = student(3101)
        doReturn(false).`when`(spiedClubRepository).existsByRoomExcept(any(ClubRoom::class.java) ?: ClubRoom.ROOM_1_1, any())
        doReturn(false).`when`(spiedClubRepository).existsByTeacherExcept(anyLong(), any())

        mockMvc.post("/clubs") {
            bearer(adminToken)
            json(body(room = "ROOM_2_1", manager = manager.id!!, member = listOf(manager.id!!)))
        }.andExpectRoomInUse()
        mockMvc.post("/clubs") {
            bearer(adminToken)
            json(body(room = "ROOM_3_1", manager = manager.id!!, member = listOf(manager.id!!), teacher = teacherId))
        }.andExpectTeacherInClub()
        assertEquals(1, clubRepository.count())
    }

    @Test
    fun `교실, 교직원 제약이 아닌 무결성 위반은 원래 예외를 그대로 던진다`() {
        val manager = student(2315)
        val violation = DataIntegrityViolationException("다른 제약 위반")
        doThrow(violation).`when`(spiedClubRepository).saveAndFlush(any())

        val thrown = assertFailsWith<ServletException> {
            mockMvc.post("/clubs") {
                bearer(adminToken)
                json(body(manager = manager.id!!, member = listOf(manager.id!!)))
            }
        }
        assertSame(violation, thrown.cause)
    }

    @Test
    fun `형식에 맞지 않는 입력은 INVALID_INPUT`() {
        val manager = student(2315)
        val id = manager.id!!
        listOf(
            body(name = " ", manager = id, member = listOf(id)) to "name",
            body(name = "가".repeat(21), manager = id, member = listOf(id)) to "name",
            body(logo = " ", manager = id, member = listOf(id)) to "logo",
            body(logo = "a".repeat(256), manager = id, member = listOf(id)) to "logo",
            body(manager = id, member = (1L..51L).toList()) to "member",
            body(room = "ROOM_9_9", manager = id, member = listOf(id)) to "요청 값이 올바르지 않습니다.",
            """{"name":"DMS","logo":"logo.png","room":"ROOM_2_1","manager":$id,"member":[$id,null]}""" to "요청 값이 올바르지 않습니다.",
            """{"name":"DMS","logo":"logo.png","room":"ROOM_2_1","member":[$id]}""" to "요청 값이 올바르지 않습니다.",
            """{"name":"DMS","logo":"logo.png","room":"ROOM_2_1","manager":$id}""" to "요청 값이 올바르지 않습니다.",
            """{"name":"DMS","logo":"logo.png","manager":$id,"member":[$id]}""" to "요청 값이 올바르지 않습니다.",
        ).forEach { (invalid, expectedMessage) ->
            mockMvc.post("/clubs") {
                bearer(adminToken)
                json(invalid)
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.code") { value("INVALID_INPUT") }
                jsonPath("$.message") { value(containsString(expectedMessage)) }
            }
        }
        assertEquals(0, clubRepository.count())
    }

    // 단건 조회

    @Test
    fun `동아리를 조회하면 팀장, 학번 순 구성원, 담당 선생님을 준다`() {
        val club = club(withTeacher = true)
        val teacher = teacherRepository.findAll().single()

        mockMvc.get("/clubs/${club.id}") {
            bearer(userToken)
        }.andExpect {
            status { isOk() }
            jsonPath("$.id") { value(club.id!!) }
            jsonPath("$.name") { value("DMS") }
            jsonPath("$.logo") { value("logo.png") }
            jsonPath("$.room") { value("ROOM_2_1") }
            jsonPath("$.manager.id") { value(club.manager!!.id!!) }
            jsonPath("$.manager.studentNumber") { value(2315) }
            jsonPath("$.manager.name") { value("김준서") }
            jsonPath("$.member[*].studentNumber") { value(contains(2101, 2315)) }
            jsonPath("$.member[*].name") { value(contains("박성준", "김준서")) }
            jsonPath("$.member[1].id") { value(club.manager!!.id!!) }
            jsonPath("$.teacher.id") { value(teacher.id!!) }
            jsonPath("$.teacher.name") { value("이진경") }
        }
    }

    @Test
    fun `담당 선생님이 없으면 teacher는 null`() {
        val club = club()

        mockMvc.get("/clubs/${club.id}") {
            bearer(userToken)
        }.andExpect {
            status { isOk() }
            jsonPath("$.teacher") { value(null as Any?) }
        }
    }

    @Test
    fun `없는 동아리면 CLUB_NOT_FOUND`() {
        mockMvc.get("/clubs/999") {
            bearer(userToken)
        }.andExpectClubNotFound()
    }

    @Test
    fun `동아리 id가 숫자가 아니면 INVALID_INPUT`() {
        mockMvc.get("/clubs/abc") {
            bearer(userToken)
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("INVALID_INPUT") }
        }
    }

    // 목록 조회

    @Test
    fun `조건 없이 조회하면 등록 순으로 첫 페이지 20개와 각 동아리의 구성원을 준다`() {
        val clubs = (1..21).map { club(name = "동아리$it", room = ClubRoom.entries[it], studentNumbers = listOf(1100 + it, 2100 + it)) }

        mockMvc.get("/clubs") {
            bearer(userToken)
        }.andExpect {
            status { isOk() }
            jsonPath("$.content.length()") { value(20) }
            jsonPath("$.content[0].id") { value(clubs.first().id!!) }
            jsonPath("$.content[0].name") { value("동아리1") }
            jsonPath("$.content[0].room") { value("ROOM_1_2") }
            jsonPath("$.content[0].logo") { value("logo.png") }
            jsonPath("$.content[0].manager.studentNumber") { value(1101) }
            jsonPath("$.content[0].member[*].studentNumber") { value(contains(1101, 2101)) }
            jsonPath("$.content[0].teacher") { value(null as Any?) }
            jsonPath("$.content[19].name") { value("동아리20") }
            jsonPath("$.content[19].member[*].studentNumber") { value(contains(1120, 2120)) }
            jsonPath("$.page") { value(0) }
            jsonPath("$.size") { value(20) }
            jsonPath("$.totalCount") { value(21) }
            jsonPath("$.totalPage") { value(2) }
        }
    }

    @Test
    fun `page와 size로 원하는 페이지를 준다`() {
        (1..5).forEach { club(name = "동아리$it", room = ClubRoom.entries[it], studentNumbers = listOf(1100 + it)) }

        mockMvc.get("/clubs?page=1&size=2") {
            bearer(userToken)
        }.andExpect {
            jsonPath("$.content[*].name") { value(contains("동아리3", "동아리4")) }
            jsonPath("$.page") { value(1) }
            jsonPath("$.size") { value(2) }
            jsonPath("$.totalCount") { value(5) }
            jsonPath("$.totalPage") { value(3) }
        }
    }

    @Test
    fun `동아리명은 일부만 같아도 찾고 %, _, !는 글자 그대로 찾는다`() {
        club(name = "DMS", room = ClubRoom.ROOM_2_1, studentNumbers = listOf(1101))
        club(name = "XQUARE DMS", room = ClubRoom.ROOM_2_2, studentNumbers = listOf(1102))
        club(name = "100%", room = ClubRoom.ROOM_2_3, studentNumbers = listOf(1103))
        club(name = "a_b", room = ClubRoom.ROOM_2_4, studentNumbers = listOf(1104))
        club(name = "!", room = ClubRoom.ROOM_2_5, studentNumbers = listOf(1105))

        expectClubNames("name=DMS", "DMS", "XQUARE DMS")
        listOf("%" to "100%", "_" to "a_b", "!" to "!").forEach { (name, expected) ->
            mockMvc.get("/clubs") {
                bearer(userToken)
                param("name", name)
            }.andExpect { jsonPath("$.content[*].name") { value(contains(expected)) } }
        }
    }

    @Test
    fun `구성원 이름은 일부만 같아도 찾고 여러 명을 주면 모두 있는 동아리만 준다`() {
        club(name = "가", room = ClubRoom.ROOM_2_1, studentNumbers = listOf(1101, 1102), names = listOf("박성준", "김민수"))
        club(name = "나", room = ClubRoom.ROOM_2_2, studentNumbers = listOf(1103, 1104), names = listOf("이성준", "최지훈"))
        club(name = "다", room = ClubRoom.ROOM_2_3, studentNumbers = listOf(1105), names = listOf("정민수"))

        expectClubNames("member=성준", "가", "나")
        expectClubNames("member=민수", "가", "다")
        expectClubNames("member=성준&member=민수", "가")
        expectClubNames("member=박성준&member=최지훈")
        expectClubNames("member=없는이름")
    }

    @Test
    fun `구성원 이름의 %, _, !는 글자 그대로 찾는다`() {
        club(name = "가", room = ClubRoom.ROOM_2_1, studentNumbers = listOf(1101), names = listOf("100%"))
        club(name = "나", room = ClubRoom.ROOM_2_2, studentNumbers = listOf(1102), names = listOf("a_b"))
        club(name = "다", room = ClubRoom.ROOM_2_3, studentNumbers = listOf(1103), names = listOf("!"))

        listOf("%" to "가", "_" to "나", "!" to "다").forEach { (member, expected) ->
            mockMvc.get("/clubs") {
                bearer(userToken)
                param("member", member)
            }.andExpect { jsonPath("$.content[*].name") { value(contains(expected)) } }
        }
    }

    @Test
    fun `교실과 담당 선생님 이름으로 찾는다`() {
        club(name = "가", room = ClubRoom.ROOM_2_1, studentNumbers = listOf(1101), teacherName = "이진경")
        club(name = "나", room = ClubRoom.ROOM_2_2, studentNumbers = listOf(1102), teacherName = "김진경")
        club(name = "다", room = ClubRoom.ROOM_2_3, studentNumbers = listOf(1103))

        expectClubNames("room=ROOM_2_2", "나")
        expectClubNames("room=ROOM_5_9")
        // 교직원 검색처럼 선생님 이름은 정확히 일치하는 것만 찾는다.
        expectClubNames("teacher=이진경", "가")
        expectClubNames("teacher=진경")
    }

    @Test
    fun `조건을 여러 개 주면 모두 만족하는 동아리만 준다`() {
        club(name = "DMS", room = ClubRoom.ROOM_2_1, studentNumbers = listOf(1101), names = listOf("박성준"), teacherName = "이진경")
        club(name = "DMS2", room = ClubRoom.ROOM_2_2, studentNumbers = listOf(1102), names = listOf("박성준"), teacherName = "김진경")
        club(name = "XQUARE", room = ClubRoom.ROOM_2_3, studentNumbers = listOf(1103), names = listOf("박성준"))

        expectClubNames("name=DMS&member=성준&room=ROOM_2_1&teacher=이진경", "DMS")
        expectClubNames("name=DMS&room=ROOM_2_3")
    }

    @Test
    fun `page나 size가 범위를 벗어나거나 조건 형식이 틀리면 INVALID_INPUT`() {
        listOf(
            "page=-1" to "page: ",
            "size=0" to "size: ",
            "size=101" to "size: ",
            "page=21474837&size=100" to "page: ",
            "room=ROOM_9_9" to "room: 형식이 올바르지 않습니다.",
            (1..11).joinToString("&") { "member=a$it" } to "member: ",
        ).forEach { (query, expectedMessage) ->
            mockMvc.get("/clubs?$query") {
                bearer(userToken)
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.code") { value("INVALID_INPUT") }
                jsonPath("$.message") { value(containsString(expectedMessage)) }
            }
        }
    }

    @Test
    fun `구성원 이름은 10개까지, size는 100까지, page는 offset이 int를 넘지 않는 데까지 받는다`() {
        mockMvc.get("/clubs?page=21474836&size=100&" + (1..10).joinToString("&") { "member=a$it" }) {
            bearer(userToken)
        }.andExpect {
            status { isOk() }
            jsonPath("$.page") { value(21474836) }
            jsonPath("$.size") { value(100) }
            jsonPath("$.content") { isEmpty() }
            jsonPath("$.totalCount") { value(0) }
        }
    }

    // 수정

    @Test
    fun `수정하면 모든 값을 바꾸고 member는 전달한 목록으로 교체한다`() {
        val club = club(room = ClubRoom.ROOM_2_1)
        val (kept, removed) = studentRepository.findAllByClubIdIn(listOf(club.id!!)).sortedByDescending { it.studentNumber }
        val added = student(3101, "최지훈")
        val teacher = teacher("이진경")

        mockMvc.patch("/clubs/${club.id}") {
            bearer(adminToken)
            json(
                body(
                    name = "XQUARE",
                    logo = "new.png",
                    room = "ROOM_3_1",
                    manager = added.id!!,
                    member = listOf(kept.id!!, added.id!!),
                    teacher = teacher.id,
                ),
            )
        }.andExpect {
            status { isOk() }
            jsonPath("$.message") { value("수정되었습니다.") }
        }

        mockMvc.get("/clubs/${club.id}") {
            bearer(userToken)
        }.andExpect {
            jsonPath("$.name") { value("XQUARE") }
            jsonPath("$.logo") { value("new.png") }
            jsonPath("$.room") { value("ROOM_3_1") }
            jsonPath("$.manager.id") { value(added.id!!) }
            jsonPath("$.member[*].id") { value(contains(kept.id!!.toInt(), added.id!!.toInt())) }
            jsonPath("$.teacher.id") { value(teacher.id!!) }
        }
        expectStudentClub(removed.id!!, null)
        expectStudentClub(kept.id!!, club.id!!)
        expectStudentClub(added.id!!, club.id!!)
    }

    @Test
    fun `teacher를 null로 보내면 담당 선생님이 없어진다`() {
        val club = club(withTeacher = true)
        val members = memberIds(club)

        mockMvc.patch("/clubs/${club.id}") {
            bearer(adminToken)
            json(body(manager = club.manager!!.id!!, member = members, teacher = null))
        }.andExpect { status { isOk() } }

        mockMvc.get("/clubs/${club.id}") {
            bearer(userToken)
        }.andExpect { jsonPath("$.teacher") { value(null as Any?) } }
        mockMvc.get("/teachers/${teacherRepository.findAll().single().id}") {
            bearer(userToken)
        }.andExpect { jsonPath("$.club") { value(null as Any?) } }
    }

    @Test
    fun `자기 동아리의 교실, 구성원, 담당 선생님은 그대로 두고 수정할 수 있다`() {
        val club = club(room = ClubRoom.ROOM_2_1, withTeacher = true)
        val teacherId = teacherRepository.findAll().single().id!!

        mockMvc.patch("/clubs/${club.id}") {
            bearer(adminToken)
            json(body(name = "바뀐 이름", room = "ROOM_2_1", manager = club.manager!!.id!!, member = memberIds(club), teacher = teacherId))
        }.andExpect { status { isOk() } }

        mockMvc.get("/clubs/${club.id}") {
            bearer(userToken)
        }.andExpect {
            jsonPath("$.name") { value("바뀐 이름") }
            jsonPath("$.room") { value("ROOM_2_1") }
            jsonPath("$.member.length()") { value(2) }
            jsonPath("$.teacher.id") { value(teacherId) }
        }
    }

    @Test
    fun `다른 동아리의 교실, 구성원, 담당 선생님으로 바꾸면 에러를 주고 바꾸지 않는다`() {
        val club = club(name = "가", room = ClubRoom.ROOM_2_1, studentNumbers = listOf(1101))
        val other = club(name = "나", room = ClubRoom.ROOM_2_2, studentNumbers = listOf(1102), teacherName = "이진경")
        val managerId = club.manager!!.id!!
        val otherTeacherId = teacherRepository.findAll().single().id!!

        mockMvc.patch("/clubs/${club.id}") {
            bearer(adminToken)
            json(body(room = "ROOM_2_2", manager = managerId, member = listOf(managerId)))
        }.andExpectRoomInUse()
        mockMvc.patch("/clubs/${club.id}") {
            bearer(adminToken)
            json(body(room = "ROOM_2_1", manager = managerId, member = listOf(managerId, other.manager!!.id!!)))
        }.andExpectStudentInClub()
        mockMvc.patch("/clubs/${club.id}") {
            bearer(adminToken)
            json(body(room = "ROOM_2_1", manager = managerId, member = listOf(managerId), teacher = otherTeacherId))
        }.andExpectTeacherInClub()

        mockMvc.get("/clubs/${club.id}") {
            bearer(userToken)
        }.andExpect {
            jsonPath("$.room") { value("ROOM_2_1") }
            jsonPath("$.teacher") { value(null as Any?) }
        }
        expectStudentClub(other.manager!!.id!!, other.id!!)
        // 교실·교직원은 unique 제약에 맡기지 않고 검사에서 막는다.
        verify(spiedClubRepository, never()).flush()
    }

    @Test
    fun `동시 수정으로 중복 검사를 통과해도 교실이나 교직원이 겹치면 같은 에러를 준다`() {
        val club = club(name = "가", room = ClubRoom.ROOM_2_1, studentNumbers = listOf(1101))
        club(name = "나", room = ClubRoom.ROOM_2_2, studentNumbers = listOf(1102), teacherName = "이진경")
        val managerId = club.manager!!.id!!
        val otherTeacherId = teacherRepository.findAll().single().id!!
        doReturn(false).`when`(spiedClubRepository).existsByRoomExcept(any(ClubRoom::class.java) ?: ClubRoom.ROOM_1_1, any())
        doReturn(false).`when`(spiedClubRepository).existsByTeacherExcept(anyLong(), any())

        mockMvc.patch("/clubs/${club.id}") {
            bearer(adminToken)
            json(body(room = "ROOM_2_2", manager = managerId, member = listOf(managerId)))
        }.andExpectRoomInUse()
        mockMvc.patch("/clubs/${club.id}") {
            bearer(adminToken)
            json(body(room = "ROOM_2_1", manager = managerId, member = listOf(managerId), teacher = otherTeacherId))
        }.andExpectTeacherInClub()
    }

    @Test
    fun `수정할 때 교실, 교직원 제약이 아닌 무결성 위반은 원래 예외를 그대로 던진다`() {
        val club = club()
        val violation = DataIntegrityViolationException("다른 제약 위반")
        doThrow(violation).`when`(spiedClubRepository).flush()

        val thrown = assertFailsWith<ServletException> {
            mockMvc.patch("/clubs/${club.id}") {
                bearer(adminToken)
                json(body(manager = club.manager!!.id!!, member = memberIds(club)))
            }
        }
        assertSame(violation, thrown.cause)
    }

    @Test
    fun `없는 동아리, 학생, 교직원이면 404이고 팀장이 구성원에 없으면 MANAGER_NOT_IN_MEMBER`() {
        val club = club()
        val managerId = club.manager!!.id!!
        val members = memberIds(club)

        mockMvc.patch("/clubs/999999") {
            bearer(adminToken)
            json(body(manager = managerId, member = members))
        }.andExpectClubNotFound()
        mockMvc.patch("/clubs/${club.id}") {
            bearer(adminToken)
            json(body(manager = managerId, member = members + 999_999))
        }.andExpectStudentNotFound()
        mockMvc.patch("/clubs/${club.id}") {
            bearer(adminToken)
            json(body(manager = managerId, member = members, teacher = 999_999))
        }.andExpectTeacherNotFound()
        mockMvc.patch("/clubs/${club.id}") {
            bearer(adminToken)
            json(body(manager = managerId, member = members - managerId))
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("MANAGER_NOT_IN_MEMBER") }
        }
        expectMemberNames(club.id!!, "박성준", "김준서")
    }

    @Test
    fun `수정할 값이 형식에 맞지 않으면 INVALID_INPUT`() {
        val club = club()

        mockMvc.patch("/clubs/${club.id}") {
            bearer(adminToken)
            json(body(name = " ", manager = club.manager!!.id!!, member = memberIds(club)))
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("INVALID_INPUT") }
        }
        assertEquals("DMS", clubRepository.findById(club.id!!).get().name)
    }

    // 삭제

    @Test
    fun `삭제하면 다시 조회할 수 없고 학생과 교직원은 남아 소속만 없어진다`() {
        val club = club(withTeacher = true)
        val members = memberIds(club)

        mockMvc.delete("/clubs/${club.id}") {
            bearer(adminToken)
        }.andExpect {
            status { isOk() }
            jsonPath("$.message") { value("삭제되었습니다.") }
        }

        assertEquals(0, clubRepository.count())
        assertEquals(2, studentRepository.count())
        members.forEach { expectStudentClub(it, null) }
        mockMvc.get("/teachers/${teacherRepository.findAll().single().id}") {
            bearer(userToken)
        }.andExpect { jsonPath("$.club") { value(null as Any?) } }
        mockMvc.delete("/clubs/${club.id}") {
            bearer(adminToken)
        }.andExpectClubNotFound()
    }

    @Test
    fun `삭제한 동아리의 교실, 학생, 선생님으로 새 동아리를 만들 수 있다`() {
        val club = club(room = ClubRoom.ROOM_2_1, withTeacher = true)
        val members = memberIds(club)
        val teacherId = teacherRepository.findAll().single().id!!
        mockMvc.delete("/clubs/${club.id}") { bearer(adminToken) }.andExpect { status { isOk() } }

        mockMvc.post("/clubs") {
            bearer(adminToken)
            json(body(room = "ROOM_2_1", manager = members.first(), member = members, teacher = teacherId))
        }.andExpect { status { isCreated() } }
    }

    // 학생·교직원 응답과 삭제

    @Test
    fun `학생과 교직원 조회에 소속, 담당 동아리를 준다`() {
        val club = club(name = "DMS", withTeacher = true)
        val noClub = student(3101)
        val teacher = teacherRepository.findAll().single()
        val noClubTeacher = teacher("김선생")

        mockMvc.get("/students/${club.manager!!.id}") {
            bearer(userToken)
        }.andExpect {
            jsonPath("$.club.id") { value(club.id!!) }
            jsonPath("$.club.name") { value("DMS") }
            jsonPath("$.club.logo") { value("logo.png") }
        }
        mockMvc.get("/students") {
            bearer(userToken)
        }.andExpect {
            jsonPath("$.content[*].studentNumber") { value(contains(2101, 2315, 3101)) }
            jsonPath("$.content[0].club.name") { value("DMS") }
            jsonPath("$.content[1].club.id") { value(club.id!!) }
            jsonPath("$.content[2].club") { value(null as Any?) }
        }
        mockMvc.get("/students/${noClub.id}") {
            bearer(userToken)
        }.andExpect { jsonPath("$.club") { value(null as Any?) } }
        mockMvc.get("/teachers/${teacher.id}") {
            bearer(userToken)
        }.andExpect {
            jsonPath("$.club.id") { value(club.id!!) }
            jsonPath("$.club.name") { value("DMS") }
            jsonPath("$.club.logo") { value("logo.png") }
        }
        mockMvc.get("/teachers") {
            bearer(userToken)
        }.andExpect {
            jsonPath("$.content[*].id") { value(contains(teacher.id!!.toInt(), noClubTeacher.id!!.toInt())) }
            jsonPath("$.content[0].club.id") { value(club.id!!) }
            jsonPath("$.content[1].club") { value(null as Any?) }
        }
    }

    @Test
    fun `학생을 수정해도 소속 동아리는 그대로다`() {
        val club = club()
        val managerId = club.manager!!.id!!

        mockMvc.patch("/students/$managerId") {
            bearer(adminToken)
            json("""{"studentNumber":2399,"name":"바뀐이름","birthDate":"2009-07-28","email":"a@dsm.hs.kr"}""")
        }.andExpect { status { isOk() } }

        expectStudentClub(managerId, club.id!!)
    }

    @Test
    fun `구성원 학생을 삭제하면 구성원에서 빠진다`() {
        val club = club()
        val member = studentRepository.findAllByClubIdIn(listOf(club.id!!)).single { it.id != club.manager!!.id }

        mockMvc.delete("/students/${member.id}") {
            bearer(adminToken)
        }.andExpect { status { isOk() } }

        expectMemberNames(club.id!!, "김준서")
    }

    @Test
    fun `팀장 학생을 삭제하면 동아리는 남고 팀장은 null이 된다`() {
        val club = club()

        mockMvc.delete("/students/${club.manager!!.id}") {
            bearer(adminToken)
        }.andExpect { status { isOk() } }

        mockMvc.get("/clubs/${club.id}") {
            bearer(userToken)
        }.andExpect {
            status { isOk() }
            jsonPath("$.manager") { value(null as Any?) }
            jsonPath("$.member[*].name") { value(contains("박성준")) }
        }
    }

    @Test
    fun `구성원이 모두 삭제된 동아리도 목록에 빈 구성원으로 나온다`() {
        val club = club(studentNumbers = listOf(1101))

        mockMvc.delete("/students/${club.manager!!.id}") { bearer(adminToken) }.andExpect { status { isOk() } }

        mockMvc.get("/clubs") {
            bearer(userToken)
        }.andExpect {
            jsonPath("$.content[0].id") { value(club.id!!) }
            jsonPath("$.content[0].manager") { value(null as Any?) }
            jsonPath("$.content[0].member") { isEmpty() }
        }
    }

    @Test
    fun `담당 교직원을 삭제하면 동아리는 남고 teacher는 null이 된다`() {
        val club = club(withTeacher = true)

        mockMvc.delete("/teachers/${teacherRepository.findAll().single().id}") {
            bearer(adminToken)
        }.andExpect { status { isOk() } }

        mockMvc.get("/clubs/${club.id}") {
            bearer(userToken)
        }.andExpect {
            status { isOk() }
            jsonPath("$.teacher") { value(null as Any?) }
        }
    }

    @Test
    fun `동아리, 학생, 교직원 목록은 동아리 수와 상관없이 같은 수의 쿼리로 조회한다`() {
        club(name = "가", room = ClubRoom.ROOM_2_1, studentNumbers = listOf(1101, 1102), teacherName = "선생1")
        val oneClub = listOf("/clubs", "/students", "/teachers").map { queryCount(it) }
        (2..4).forEach { club(name = "가$it", room = ClubRoom.entries[it], studentNumbers = listOf(1200 + it, 1300 + it), teacherName = "선생$it") }

        assertEquals(oneClub, listOf("/clubs", "/students", "/teachers").map { queryCount(it) })
    }

    // 권한

    @Test
    fun `토큰이 없으면 INVALID_TOKEN`() {
        val club = club()

        listOf(
            mockMvc.get("/clubs"),
            mockMvc.get("/clubs/${club.id}"),
            mockMvc.post("/clubs") { json(body(manager = 1, member = listOf(1))) },
            mockMvc.patch("/clubs/${club.id}") { json(body(manager = 1, member = listOf(1))) },
            mockMvc.delete("/clubs/${club.id}"),
        ).forEach {
            it.andExpect {
                status { isUnauthorized() }
                jsonPath("$.code") { value("INVALID_TOKEN") }
            }
        }
    }

    @Test
    fun `USER는 등록, 수정, 삭제를 할 수 없다`() {
        val club = club()
        val managerId = club.manager!!.id!!

        listOf(
            mockMvc.post("/clubs") { bearer(userToken); json(body(room = "ROOM_3_1", manager = managerId, member = listOf(managerId))) },
            mockMvc.patch("/clubs/${club.id}") { bearer(userToken); json(body(name = "바뀜", manager = managerId, member = listOf(managerId))) },
            mockMvc.delete("/clubs/${club.id}") { bearer(userToken) },
        ).forEach {
            it.andExpect {
                status { isForbidden() }
                jsonPath("$.code") { value("FORBIDDEN") }
            }
        }
        assertEquals("DMS", clubRepository.findAll().single().name)
    }

    @Test
    fun `ADMIN도 조회할 수 있다`() {
        val club = club()

        mockMvc.get("/clubs") { bearer(adminToken) }.andExpect { status { isOk() } }
        mockMvc.get("/clubs/${club.id}") { bearer(adminToken) }.andExpect { status { isOk() } }
    }

    // 첫 번째 학번이 팀장이다. 기본값은 팀장 2315 김준서, 구성원 2101 박성준.
    private fun club(
        name: String = "DMS",
        room: ClubRoom = ClubRoom.ROOM_2_1,
        studentNumbers: List<Int> = listOf(2315, 2101),
        names: List<String> = listOf("김준서", "박성준"),
        withTeacher: Boolean = false,
        teacherName: String? = if (withTeacher) "이진경" else null,
    ): Club {
        val students = studentNumbers.mapIndexed { i, number -> student(number, names.getOrElse(i) { "학생$number" }) }
        val teacher = teacherName?.let { teacher(it) }
        val club = clubRepository.save(Club(name, "logo.png", room, students.first(), teacher))
        students.forEach { it.joinClub(club) }
        studentRepository.saveAll(students)
        return club
    }

    private fun student(studentNumber: Int, name: String = "학생$studentNumber") =
        studentRepository.save(Student(studentNumber, name, LocalDate.of(2009, 7, 28), "s$studentNumber@dsm.hs.kr"))

    private fun teacher(name: String) =
        teacherRepository.save(Teacher(name, null, null, TeacherSubject.MATH, "3층 교무실", false, TeacherPosition.MEISTER))

    private fun queryCount(uri: String): Long {
        val statistics = entityManagerFactory.unwrap(SessionFactory::class.java).statistics
        statistics.clear()
        mockMvc.get(uri) { bearer(userToken) }.andExpect { status { isOk() } }
        return statistics.prepareStatementCount
    }

    private fun memberIds(club: Club) = studentRepository.findAllByClubIdIn(listOf(club.id!!)).map { it.id!! }

    private fun expectClubNames(query: String, vararg expected: String) {
        mockMvc.get("/clubs?$query") {
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

    private fun expectMemberNames(clubId: Long, vararg expected: String) {
        mockMvc.get("/clubs/$clubId") {
            bearer(userToken)
        }.andExpect { jsonPath("$.member[*].name") { value(contains(*expected)) } }
    }

    private fun expectStudentClub(studentId: Long, clubId: Long?) {
        mockMvc.get("/students/$studentId") {
            bearer(userToken)
        }.andExpect {
            if (clubId == null) jsonPath("$.club") { value(null as Any?) } else jsonPath("$.club.id") { value(clubId) }
        }
    }

    private fun ResultActionsDsl.andExpectClubNotFound() = andExpect {
        status { isNotFound() }
        jsonPath("$.code") { value("CLUB_NOT_FOUND") }
        jsonPath("$.message") { value("존재하지 않는 동아리입니다.") }
    }

    private fun ResultActionsDsl.andExpectStudentNotFound() = andExpect {
        status { isNotFound() }
        jsonPath("$.code") { value("STUDENT_NOT_FOUND") }
    }

    private fun ResultActionsDsl.andExpectTeacherNotFound() = andExpect {
        status { isNotFound() }
        jsonPath("$.code") { value("TEACHER_NOT_FOUND") }
    }

    private fun ResultActionsDsl.andExpectRoomInUse() = andExpect {
        status { isBadRequest() }
        jsonPath("$.code") { value("ROOM_ALREADY_IN_USE") }
        jsonPath("$.message") { value("다른 동아리가 이미 사용 중인 교실입니다.") }
    }

    private fun ResultActionsDsl.andExpectStudentInClub() = andExpect {
        status { isConflict() }
        jsonPath("$.code") { value("STUDENT_ALREADY_IN_CLUB") }
        jsonPath("$.message") { value("이미 다른 동아리에 속한 학생입니다.") }
    }

    private fun ResultActionsDsl.andExpectTeacherInClub() = andExpect {
        status { isConflict() }
        jsonPath("$.code") { value("TEACHER_ALREADY_IN_CLUB") }
        jsonPath("$.message") { value("이미 다른 동아리를 담당하는 교직원입니다.") }
    }

    private fun MockHttpServletRequestDsl.bearer(token: String) = header("Authorization", "Bearer $token")

    private fun MockHttpServletRequestDsl.json(body: String) {
        contentType = MediaType.APPLICATION_JSON
        content = body
    }

    private fun body(
        name: String = "DMS",
        logo: String = "logo.png",
        room: String = "ROOM_2_1",
        manager: Long,
        member: List<Long>,
        teacher: Long? = null,
    ) = """
        {
          "name": "$name",
          "logo": "$logo",
          "room": "$room",
          "manager": $manager,
          "member": ${member.joinToString(",", "[", "]")},
          "teacher": $teacher
        }
    """.trimIndent()
}
