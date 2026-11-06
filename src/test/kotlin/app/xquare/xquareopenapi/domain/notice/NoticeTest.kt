package app.xquare.xquareopenapi.domain.notice

import app.xquare.xquareopenapi.domain.user.Role
import app.xquare.xquareopenapi.domain.user.User
import app.xquare.xquareopenapi.domain.user.UserRepository
import app.xquare.xquareopenapi.global.jwt.JwtProvider
import jakarta.persistence.EntityManagerFactory
import org.hamcrest.Matchers.contains
import org.hamcrest.Matchers.containsString
import org.hibernate.SessionFactory
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockHttpServletRequestDsl
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActionsDsl
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.patch
import org.springframework.test.web.servlet.post
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

// 작성·수정일시를 확인하려고 Clock 빈을 시간을 바꿀 수 있는 가짜로 바꾼다. 목록 조회의 쿼리 수를 세려고 Hibernate 통계도 켠다.
// 빈을 바꿔서 어차피 이 테스트만의 컨텍스트라 다른 테스트에 영향이 없다.
@SpringBootTest(properties = ["spring.jpa.properties.hibernate.generate_statistics=true"])
@AutoConfigureMockMvc
class NoticeTest @Autowired constructor(
    private val mockMvc: MockMvc,
    private val entityManagerFactory: EntityManagerFactory,
    private val noticeRepository: NoticeRepository,
    private val userRepository: UserRepository,
    private val clock: MutableClock,
    private val jwtProvider: JwtProvider,
) {
    private lateinit var admin: User
    private lateinit var otherAdmin: User
    private lateinit var adminToken: String
    private lateinit var otherAdminToken: String
    private lateinit var userToken: String

    @BeforeEach
    fun setUp() {
        admin = userRepository.save(User("noticeadmin", "encoded", 2315, "김관리", "notice-admin@dsm.hs.kr"))
        otherAdmin = userRepository.save(User("noticeadmin2", "encoded", 2316, "이관리", "notice-admin2@dsm.hs.kr"))
        val user = userRepository.save(User("noticeuser", "encoded", 2317, "박학생", "notice-user@dsm.hs.kr"))
        adminToken = jwtProvider.generateAccessToken(admin.id!!, Role.ADMIN)
        otherAdminToken = jwtProvider.generateAccessToken(otherAdmin.id!!, Role.ADMIN)
        userToken = jwtProvider.generateAccessToken(user.id!!, Role.USER)
        clock.set(LocalDateTime.of(2026, 9, 1, 9, 0))
    }

    @AfterEach
    fun tearDown() {
        noticeRepository.deleteAll()
        userRepository.deleteAll(userRepository.findAll().filter { it.username.startsWith("notice") })
    }

    // 등록

    @Test
    fun `등록하면 토큰의 사용자를 작성자로 저장하고 Location 헤더로 상세조회 URI를 준다`() {
        // 작성일시는 Clock 빈의 서울 시간을 초까지만 남긴다.
        clock.set(LocalDateTime.of(2026, 9, 1, 9, 0, 0, 999_999_999))

        val location = mockMvc.post("/notices") {
            bearer(adminToken)
            json(body(title = "2학기 동아리 모집 안내", content = "9월 15일까지 신청서를 제출하세요."))
        }.andExpect {
            status { isCreated() }
            jsonPath("$.message") { value("생성되었습니다.") }
        }.andReturn().response.getHeader("Location")

        val notice = noticeRepository.findAll().single()
        assertEquals("/notices/${notice.id}", location)
        mockMvc.get(location!!) {
            bearer(userToken)
        }.andExpect {
            status { isOk() }
            jsonPath("$.id") { value(notice.id!!) }
            jsonPath("$.title") { value("2학기 동아리 모집 안내") }
            jsonPath("$.content") { value("9월 15일까지 신청서를 제출하세요.") }
            jsonPath("$.author") { value("김관리") }
            jsonPath("$.createdAt") { value("2026-09-01T09:00:00") }
            jsonPath("$.updatedAt") { value(null as Any?) }
        }
    }

    @Test
    fun `body에 작성자나 작성일을 넣어도 무시하고 토큰의 사용자와 지금 시간을 쓴다`() {
        mockMvc.post("/notices") {
            bearer(otherAdminToken)
            json("""{"title":"제목","content":"본문","author":"김관리","createdAt":"2020-01-01T00:00:00"}""")
        }.andExpect { status { isCreated() } }

        val notice = noticeRepository.findAll().single()
        mockMvc.get("/notices/${notice.id}") {
            bearer(userToken)
        }.andExpect {
            jsonPath("$.author") { value("이관리") }
            jsonPath("$.createdAt") { value("2026-09-01T09:00:00") }
        }
    }

    @Test
    fun `제목은 100자, 본문은 4000자까지 받는다`() {
        mockMvc.post("/notices") {
            bearer(adminToken)
            json(body(title = "가".repeat(100), content = "나".repeat(4000)))
        }.andExpect { status { isCreated() } }

        val notice = noticeRepository.findAll().single()
        assertEquals(100, notice.title.length)
        assertEquals(4000, notice.content.length)
    }

    @Test
    fun `형식에 맞지 않는 입력은 INVALID_INPUT`() {
        listOf(
            """{"content":"본문"}""" to "",
            """{"title":null,"content":"본문"}""" to "",
            """{"title":"제목"}""" to "",
            """{"title":"제목","content":null}""" to "",
            body(title = "", content = "본문") to "title: ",
            body(title = "   ", content = "본문") to "title: ",
            body(title = "가".repeat(101), content = "본문") to "title: ",
            body(title = "제목", content = "") to "content: ",
            body(title = "제목", content = " \n ") to "content: ",
            body(title = "제목", content = "나".repeat(4001)) to "content: ",
            """{"title":"제목","content":""" to "",
        ).forEach { (requestBody, expectedMessage) ->
            listOf(
                mockMvc.post("/notices") { bearer(adminToken); json(requestBody) },
                mockMvc.patch("/notices/${saveNotice().id}") { bearer(adminToken); json(requestBody) },
            ).forEach {
                it.andExpect {
                    status { isBadRequest() }
                    jsonPath("$.code") { value("INVALID_INPUT") }
                    jsonPath("$.message") { value(containsString(expectedMessage)) }
                }
            }
        }
        assertEquals(11, noticeRepository.count())
        assertFalse(noticeRepository.findAll().any { it.updatedAt != null })
    }

    @Test
    fun `토큰은 유효한데 사용자가 없으면 INVALID_TOKEN`() {
        val deletedUserToken = jwtProvider.generateAccessToken(Long.MAX_VALUE, Role.ADMIN)

        mockMvc.post("/notices") {
            bearer(deletedUserToken)
            json(body())
        }.andExpect {
            status { isUnauthorized() }
            jsonPath("$.code") { value("INVALID_TOKEN") }
        }
        assertEquals(0, noticeRepository.count())
    }

    // 권한

    @Test
    fun `토큰이 없으면 모든 공지 API가 INVALID_TOKEN`() {
        val id = saveNotice().id
        listOf(
            mockMvc.get("/notices"),
            mockMvc.post("/notices") { json(body()) },
            mockMvc.get("/notices/$id"),
            mockMvc.patch("/notices/$id") { json(body()) },
            mockMvc.delete("/notices/$id"),
        ).forEach {
            it.andExpect {
                status { isUnauthorized() }
                jsonPath("$.code") { value("INVALID_TOKEN") }
            }
        }
    }

    @Test
    fun `USER는 조회만 되고 등록, 수정, 삭제는 FORBIDDEN`() {
        val notice = saveNotice()

        mockMvc.get("/notices") { bearer(userToken) }.andExpect { status { isOk() } }
        mockMvc.get("/notices/${notice.id}") { bearer(userToken) }.andExpect { status { isOk() } }
        listOf(
            mockMvc.post("/notices") { bearer(userToken); json(body()) },
            mockMvc.patch("/notices/${notice.id}") { bearer(userToken); json(body(title = "바뀐 제목")) },
            mockMvc.delete("/notices/${notice.id}") { bearer(userToken) },
        ).forEach {
            it.andExpect {
                status { isForbidden() }
                jsonPath("$.code") { value("FORBIDDEN") }
            }
        }
        val saved = noticeRepository.findAll().single()
        assertEquals("제목", saved.title)
        assertNull(saved.updatedAt)
    }

    @Test
    fun `ADMIN도 조회할 수 있다`() {
        val notice = saveNotice()

        mockMvc.get("/notices") { bearer(adminToken) }.andExpect { status { isOk() } }
        mockMvc.get("/notices/${notice.id}") { bearer(adminToken) }.andExpect { status { isOk() } }
    }

    // 목록 조회

    @Test
    fun `목록은 작성일시 최신순이고 같은 시간이면 나중에 등록한 것부터 주며 본문은 뺀다`() {
        val first = saveNotice("A", at = LocalDateTime.of(2026, 9, 1, 9, 0))
        val latest = saveNotice("B", at = LocalDateTime.of(2026, 9, 1, 10, 0))
        val sameTime = saveNotice("C", at = LocalDateTime.of(2026, 9, 1, 9, 0))
        saveNotice("D", at = LocalDateTime.of(2026, 8, 31, 23, 59, 59))

        mockMvc.get("/notices") {
            bearer(userToken)
        }.andExpect {
            status { isOk() }
            jsonPath("$.notices[*].title") { value(contains("B", "C", "A", "D")) }
            jsonPath("$.notices[0].id") { value(latest.id!!) }
            jsonPath("$.notices[0].author") { value("김관리") }
            jsonPath("$.notices[0].createdAt") { value("2026-09-01T10:00:00") }
            jsonPath("$.notices[0].updatedAt") { value(null as Any?) }
            jsonPath("$.notices[0].content") { doesNotExist() }
            jsonPath("$.notices[1].id") { value(sameTime.id!!) }
            jsonPath("$.notices[2].id") { value(first.id!!) }
            jsonPath("$.page") { value(0) }
            jsonPath("$.size") { value(20) }
            jsonPath("$.totalCount") { value(4) }
            jsonPath("$.totalPage") { value(1) }
        }
    }

    @Test
    fun `공지가 없으면 빈 목록을 준다`() {
        mockMvc.get("/notices") {
            bearer(userToken)
        }.andExpect {
            status { isOk() }
            jsonPath("$.notices") { isEmpty() }
            jsonPath("$.totalCount") { value(0) }
            jsonPath("$.totalPage") { value(0) }
        }
    }

    @Test
    fun `기본 크기는 20개다`() {
        (1..21).forEach { saveNotice("$it", at = LocalDateTime.of(2026, 9, 1, 9, 0).plusMinutes(it.toLong())) }

        mockMvc.get("/notices") {
            bearer(userToken)
        }.andExpect {
            jsonPath("$.notices.length()") { value(20) }
            jsonPath("$.notices[0].title") { value("21") }
            jsonPath("$.totalCount") { value(21) }
            jsonPath("$.totalPage") { value(2) }
        }
    }

    @Test
    fun `page와 size로 원하는 페이지를 준다`() {
        (1..5).forEach { saveNotice("$it", at = LocalDateTime.of(2026, 9, 1, 9, 0).plusMinutes(it.toLong())) }

        mockMvc.get("/notices?page=1&size=2") {
            bearer(userToken)
        }.andExpect {
            status { isOk() }
            jsonPath("$.notices[*].title") { value(contains("3", "2")) }
            jsonPath("$.page") { value(1) }
            jsonPath("$.size") { value(2) }
            jsonPath("$.totalCount") { value(5) }
            jsonPath("$.totalPage") { value(3) }
        }
    }

    @Test
    fun `page나 size가 범위를 벗어나거나 숫자가 아니면 INVALID_INPUT`() {
        listOf(
            "page=-1" to "page: ",
            "size=0" to "size: ",
            "size=101" to "size: ",
            "page=abc" to "page: 형식이 올바르지 않습니다.",
            "size=abc" to "size: 형식이 올바르지 않습니다.",
            // page × size가 int를 넘으면 Spring Data가 offset을 만들지 못해 500이 난다.
            "page=21474837&size=100" to "page: ",
        ).forEach { (query, expectedMessage) ->
            mockMvc.get("/notices?$query") {
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
        mockMvc.get("/notices?page=21474836&size=100") {
            bearer(userToken)
        }.andExpect {
            status { isOk() }
            jsonPath("$.page") { value(21474836) }
            jsonPath("$.size") { value(100) }
            jsonPath("$.notices") { isEmpty() }
        }
    }

    @Test
    fun `목록의 작성자는 공지 수와 상관없이 같은 쿼리 수로 읽는다`() {
        saveNotice("1")
        val one = listQueryCount()
        (2..6).forEach { saveNotice("$it", author = if (it % 2 == 0) otherAdmin else admin) }

        assertEquals(one, listQueryCount())
    }

    // 상세 조회

    @Test
    fun `없는 공지면 NOTICE_NOT_FOUND`() {
        mockMvc.get("/notices/${Long.MAX_VALUE}") {
            bearer(userToken)
        }.andExpectNoticeNotFound()
    }

    @Test
    fun `공지 id가 숫자가 아니면 INVALID_INPUT`() {
        listOf(
            mockMvc.get("/notices/abc") { bearer(userToken) },
            mockMvc.patch("/notices/abc") { bearer(adminToken); json(body()) },
            mockMvc.delete("/notices/abc") { bearer(adminToken) },
        ).forEach {
            it.andExpect {
                status { isBadRequest() }
                jsonPath("$.code") { value("INVALID_INPUT") }
            }
        }
    }

    // 수정

    @Test
    fun `다른 ADMIN도 수정할 수 있고 작성자와 작성일시는 그대로 두고 수정일시를 남긴다`() {
        val notice = saveNotice()
        clock.set(LocalDateTime.of(2026, 9, 1, 10, 30, 15, 123_000_000))

        mockMvc.patch("/notices/${notice.id}") {
            bearer(otherAdminToken)
            json(body(title = "바뀐 제목", content = "바뀐 본문"))
        }.andExpect {
            status { isOk() }
            jsonPath("$.message") { value("수정되었습니다.") }
        }

        mockMvc.get("/notices/${notice.id}") {
            bearer(userToken)
        }.andExpect {
            jsonPath("$.title") { value("바뀐 제목") }
            jsonPath("$.content") { value("바뀐 본문") }
            jsonPath("$.author") { value("김관리") }
            jsonPath("$.createdAt") { value("2026-09-01T09:00:00") }
            jsonPath("$.updatedAt") { value("2026-09-01T10:30:15") }
        }
        mockMvc.get("/notices") {
            bearer(userToken)
        }.andExpect { jsonPath("$.notices[0].updatedAt") { value("2026-09-01T10:30:15") } }
    }

    @Test
    fun `수정해도 목록 순서는 작성일시 기준이다`() {
        val old = saveNotice("old", at = LocalDateTime.of(2026, 9, 1, 8, 0))
        saveNotice("new", at = LocalDateTime.of(2026, 9, 1, 9, 0))
        clock.set(LocalDateTime.of(2026, 9, 2, 9, 0))

        mockMvc.patch("/notices/${old.id}") {
            bearer(adminToken)
            json(body(title = "old"))
        }.andExpect { status { isOk() } }

        mockMvc.get("/notices") {
            bearer(userToken)
        }.andExpect { jsonPath("$.notices[*].title") { value(contains("new", "old")) } }
    }

    @Test
    fun `같은 값으로 수정해도 200이고 수정일시를 남긴다`() {
        val notice = saveNotice()

        mockMvc.patch("/notices/${notice.id}") {
            bearer(adminToken)
            json(body())
        }.andExpect { status { isOk() } }

        assertEquals(LocalDateTime.of(2026, 9, 1, 9, 0), noticeRepository.findAll().single().updatedAt)
    }

    @Test
    fun `없는 공지를 수정하면 NOTICE_NOT_FOUND`() {
        mockMvc.patch("/notices/${Long.MAX_VALUE}") {
            bearer(adminToken)
            json(body())
        }.andExpectNoticeNotFound()
    }

    // 삭제

    @Test
    fun `다른 ADMIN도 삭제할 수 있고 삭제한 공지는 조회, 수정, 삭제 모두 NOTICE_NOT_FOUND`() {
        val notice = saveNotice()
        val remaining = saveNotice("남는 공지")

        mockMvc.delete("/notices/${notice.id}") {
            bearer(otherAdminToken)
        }.andExpect {
            status { isOk() }
            jsonPath("$.message") { value("삭제되었습니다.") }
        }

        assertEquals(listOf(remaining.id), noticeRepository.findAll().map { it.id })
        mockMvc.get("/notices/${notice.id}") { bearer(userToken) }.andExpectNoticeNotFound()
        mockMvc.patch("/notices/${notice.id}") { bearer(adminToken); json(body()) }.andExpectNoticeNotFound()
        mockMvc.delete("/notices/${notice.id}") { bearer(adminToken) }.andExpectNoticeNotFound()
    }

    // 작성자 삭제

    @Test
    fun `작성자 사용자를 지우면 공지는 남고 작성자는 null이다`() {
        val notice = saveNotice()
        saveNotice("다른 공지", author = otherAdmin)

        userRepository.delete(admin)

        mockMvc.get("/notices/${notice.id}") {
            bearer(userToken)
        }.andExpect {
            status { isOk() }
            jsonPath("$.title") { value("제목") }
            jsonPath("$.author") { value(null as Any?) }
        }
        mockMvc.get("/notices") {
            bearer(userToken)
        }.andExpect {
            // 같은 시각이라 나중에 등록한 다른 공지가 먼저다.
            jsonPath("$.notices[*].author") { value(contains("이관리", null)) }
        }
        mockMvc.patch("/notices/${notice.id}") { bearer(otherAdminToken); json(body(title = "고친 제목")) }.andExpect { status { isOk() } }
        mockMvc.delete("/notices/${notice.id}") { bearer(otherAdminToken) }.andExpect { status { isOk() } }
    }

    private fun saveNotice(title: String = "제목", author: User = admin, at: LocalDateTime = LocalDateTime.of(2026, 9, 1, 9, 0)) =
        noticeRepository.save(Notice(title, "본문", author, at))

    private fun listQueryCount(): Long {
        val statistics = entityManagerFactory.unwrap(SessionFactory::class.java).statistics
        statistics.clear()
        mockMvc.get("/notices") { bearer(userToken) }.andExpect { status { isOk() } }
        return statistics.prepareStatementCount
    }

    private fun ResultActionsDsl.andExpectNoticeNotFound() = andExpect {
        status { isNotFound() }
        jsonPath("$.code") { value("NOTICE_NOT_FOUND") }
        jsonPath("$.message") { value("이미 삭제되었거나 존재하지 않는 공지입니다.") }
    }

    private fun MockHttpServletRequestDsl.bearer(token: String) = header("Authorization", "Bearer $token")

    private fun MockHttpServletRequestDsl.json(body: String) {
        contentType = MediaType.APPLICATION_JSON
        content = body
    }

    private fun body(title: String = "제목", content: String = "본문") =
        """{"title":"${title.replace("\n", "\\n")}","content":"${content.replace("\n", "\\n")}"}"""

    // 테스트마다 원하는 시각을 넣는다. 서울 시간대는 ClockConfig와 같다.
    class MutableClock(private var instant: Instant, private val zone: ZoneId) : Clock() {
        fun set(dateTime: LocalDateTime) {
            instant = dateTime.atZone(zone).toInstant()
        }

        override fun instant(): Instant = instant
        override fun getZone(): ZoneId = zone
        override fun withZone(zone: ZoneId): Clock = MutableClock(instant, zone)
    }

    @TestConfiguration
    class MutableClockConfig {
        @Bean
        @Primary
        fun mutableClock() = MutableClock(Instant.EPOCH, ZoneId.of("Asia/Seoul"))
    }
}
