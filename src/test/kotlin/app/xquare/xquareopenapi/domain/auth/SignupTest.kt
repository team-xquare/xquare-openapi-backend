package app.xquare.xquareopenapi.domain.auth

import app.xquare.xquareopenapi.domain.user.Role
import app.xquare.xquareopenapi.domain.user.User
import app.xquare.xquareopenapi.domain.user.UserRepository
import app.xquare.xquareopenapi.global.jwt.JwtProperties
import com.jayway.jsonpath.JsonPath
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import jakarta.servlet.ServletException
import org.hamcrest.Matchers.containsString
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.doThrow
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.MediaType
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

@SpringBootTest
@AutoConfigureMockMvc
@Import(FakeAuthRepositories::class)
class SignupTest @Autowired constructor(
    private val mockMvc: MockMvc,
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
    private val emailVerifiedTokens: FakeEmailVerifiedTokenRepository,
    private val refreshTokens: FakeRefreshTokenRepository,
    private val jwtProperties: JwtProperties,
) {
    // 동시 가입을 흉내 내려고 중복 검사만 통과시킬 때 쓴다.
    @MockitoSpyBean
    private lateinit var spiedUserRepository: UserRepository

    @AfterEach
    fun tearDown() {
        userRepository.deleteAll()
        emailVerifiedTokens.tokens.clear()
        emailVerifiedTokens.failOnDelete = false
        refreshTokens.clear()
    }

    @Test
    fun `가입하면 USER 권한으로 저장하고 토큰을 발급한다`() {
        emailVerifiedTokens.tokens[TOKEN] = EMAIL

        val response = mockMvc.post("/auth/signup") {
            contentType = MediaType.APPLICATION_JSON
            content = body()
        }.andExpect {
            status { isCreated() }
            jsonPath("$.accessToken") { isNotEmpty() }
            jsonPath("$.refreshToken") { isNotEmpty() }
        }.andReturn().response.contentAsString

        val user = userRepository.findAll().single()
        assertEquals("Jun227", user.username)
        assertEquals(2315, user.studentNumber)
        assertEquals("장윤호", user.name)
        assertEquals(EMAIL, user.email)
        assertEquals(Role.USER, user.role)
        assertTrue(passwordEncoder.matches("Password1!", user.password))
        assertTrue(response.contains(refreshTokens.tokens.getValue(user.id!!)))
        assertEquals(jwtProperties.refreshExpiration, refreshTokens.ttls.getValue(user.id!!))
    }

    @Test
    fun `사용자마다 자기 id가 담긴 토큰을 받는다`() {
        emailVerifiedTokens.tokens[TOKEN] = EMAIL
        emailVerifiedTokens.tokens["other-token"] = "other@dsm.hs.kr"

        val first = signupAndGetAccessTokenSubject(body())
        val second = signupAndGetAccessTokenSubject(
            body(username = "other", email = "other@dsm.hs.kr", emailVerifiedToken = "other-token"),
        )

        assertNotEquals(first, second)
        assertEquals(setOf(first, second), refreshTokens.tokens.keys.map { it.toString() }.toSet())
    }

    private fun signupAndGetAccessTokenSubject(body: String): String {
        val response = mockMvc.post("/auth/signup") {
            contentType = MediaType.APPLICATION_JSON
            content = body
        }.andReturn().response.contentAsString
        val accessToken = JsonPath.read<String>(response, "$.accessToken")
        return Jwts.parser()
            .verifyWith(Keys.hmacShaKeyFor(jwtProperties.secret.toByteArray()))
            .build()
            .parseSignedClaims(accessToken)
            .payload
            .subject
    }

    @Test
    fun `가입에 쓴 이메일 인증 토큰은 다시 쓸 수 없다`() {
        emailVerifiedTokens.tokens[TOKEN] = EMAIL

        mockMvc.post("/auth/signup") {
            contentType = MediaType.APPLICATION_JSON
            content = body()
        }.andExpect { status { isCreated() } }

        assertNull(emailVerifiedTokens.tokens[TOKEN])
    }

    @Test
    fun `가입이 롤백되면 이메일 인증 토큰을 남겨 두어 다시 시도할 수 있다`() {
        emailVerifiedTokens.tokens[TOKEN] = EMAIL
        refreshTokens.failOnSave = true

        assertFailsWith<Exception> {
            mockMvc.post("/auth/signup") {
                contentType = MediaType.APPLICATION_JSON
                content = body()
            }
        }

        assertEquals(0, userRepository.count())
        assertEquals(EMAIL, emailVerifiedTokens.tokens[TOKEN])
    }

    @Test
    fun `이메일 인증 토큰 삭제에 실패해도 가입은 성공한다`() {
        emailVerifiedTokens.tokens[TOKEN] = EMAIL
        emailVerifiedTokens.failOnDelete = true

        mockMvc.post("/auth/signup") {
            contentType = MediaType.APPLICATION_JSON
            content = body()
        }.andExpect { status { isCreated() } }

        assertEquals(1, userRepository.count())
    }

    @Test
    fun `이메일 인증 토큰이 없으면 EMAIL_NOT_VERIFIED`() {
        mockMvc.post("/auth/signup") {
            contentType = MediaType.APPLICATION_JSON
            content = body()
        }.andExpect {
            status { isForbidden() }
            jsonPath("$.code") { value("EMAIL_NOT_VERIFIED") }
            jsonPath("$.message") { value("이메일 인증이 완료되지 않았습니다.") }
        }
    }

    @Test
    fun `토큰의 이메일과 요청 이메일이 다르면 EMAIL_NOT_VERIFIED`() {
        emailVerifiedTokens.tokens[TOKEN] = "other@dsm.hs.kr"

        mockMvc.post("/auth/signup") {
            contentType = MediaType.APPLICATION_JSON
            content = body()
        }.andExpect {
            status { isForbidden() }
            jsonPath("$.code") { value("EMAIL_NOT_VERIFIED") }
        }
    }

    @Test
    fun `이미 쓰는 아이디면 USERNAME_ALREADY_EXISTS`() {
        emailVerifiedTokens.tokens[TOKEN] = EMAIL
        mockMvc.post("/auth/signup") {
            contentType = MediaType.APPLICATION_JSON
            content = body()
        }
        emailVerifiedTokens.tokens["other-token"] = "other@dsm.hs.kr"

        mockMvc.post("/auth/signup") {
            contentType = MediaType.APPLICATION_JSON
            content = body(email = "other@dsm.hs.kr", emailVerifiedToken = "other-token")
        }.andExpect {
            status { isConflict() }
            jsonPath("$.code") { value("USERNAME_ALREADY_EXISTS") }
            jsonPath("$.message") { value("이미 사용 중인 아이디입니다.") }
        }
        // 중복 검사에서 막아야 운영 DB의 제약 이름이 달라도 409가 난다. 제약에 맡기면 이름을 구분하지 못해 500이 된다.
        verify(spiedUserRepository, times(1)).save(any())
    }

    @Test
    fun `이미 쓰는 이메일이면 EMAIL_ALREADY_EXISTS`() {
        emailVerifiedTokens.tokens[TOKEN] = EMAIL
        mockMvc.post("/auth/signup") {
            contentType = MediaType.APPLICATION_JSON
            content = body()
        }
        emailVerifiedTokens.tokens["other-token"] = EMAIL

        mockMvc.post("/auth/signup") {
            contentType = MediaType.APPLICATION_JSON
            content = body(username = "other", emailVerifiedToken = "other-token")
        }.andExpect {
            status { isConflict() }
            jsonPath("$.code") { value("EMAIL_ALREADY_EXISTS") }
            jsonPath("$.message") { value("이미 사용 중인 이메일입니다.") }
        }
        assertEquals(1, userRepository.count())
        verify(spiedUserRepository, times(1)).save(any())
    }

    @Test
    fun `이름은 10자까지 받는다`() {
        emailVerifiedTokens.tokens[TOKEN] = EMAIL

        mockMvc.post("/auth/signup") {
            contentType = MediaType.APPLICATION_JSON
            content = body(name = "가".repeat(10))
        }.andExpect { status { isCreated() } }
    }

    @Test
    fun `동시 가입으로 중복 검사를 통과해도 같은 아이디는 USERNAME_ALREADY_EXISTS`() {
        userRepository.save(User("Jun227", "encoded", 2316, "다른사람", "other@dsm.hs.kr"))
        doReturn(false).`when`(spiedUserRepository).existsByUsername(anyString())
        emailVerifiedTokens.tokens[TOKEN] = EMAIL

        mockMvc.post("/auth/signup") {
            contentType = MediaType.APPLICATION_JSON
            content = body()
        }.andExpect {
            status { isConflict() }
            jsonPath("$.code") { value("USERNAME_ALREADY_EXISTS") }
        }
        assertEquals(EMAIL, emailVerifiedTokens.tokens[TOKEN])
    }

    @Test
    fun `동시 가입으로 중복 검사를 통과해도 같은 이메일은 EMAIL_ALREADY_EXISTS`() {
        userRepository.save(User("other", "encoded", 2316, "다른사람", EMAIL))
        doReturn(false).`when`(spiedUserRepository).existsByEmail(anyString())
        emailVerifiedTokens.tokens[TOKEN] = EMAIL

        mockMvc.post("/auth/signup") {
            contentType = MediaType.APPLICATION_JSON
            content = body()
        }.andExpect {
            status { isConflict() }
            jsonPath("$.code") { value("EMAIL_ALREADY_EXISTS") }
        }
    }

    @Test
    fun `어느 unique 제약인지 구분할 수 없는 무결성 위반은 원래 예외를 그대로 던진다`() {
        val violation = DataIntegrityViolationException("다른 제약 위반")
        doThrow(violation).`when`(spiedUserRepository).save(any())
        emailVerifiedTokens.tokens[TOKEN] = EMAIL

        val thrown = assertFailsWith<ServletException> {
            mockMvc.post("/auth/signup") {
                contentType = MediaType.APPLICATION_JSON
                content = body()
            }
        }
        assertSame(violation, thrown.cause)
    }

    @Test
    fun `형식에 맞지 않는 입력은 400`() {
        emailVerifiedTokens.tokens[TOKEN] = EMAIL
        val invalidBodies = listOf(
            body(username = "abc") to "username",
            body(username = "has space") to "username",
            body(password = "Password1") to "password",
            body(password = "Pw1!") to "password",
            body(password = "Password1!한") to "password",
            body(password = "Pass word1!") to "password",
            body(password = "\uD83D\uDE00".repeat(20)) to "password",
            body(studentNumber = 999) to "studentNumber",
            body(studentNumber = 4000) to "studentNumber",
            body(name = " ") to "name",
            body(name = "가".repeat(11)) to "name",
            body(email = "example@gmail.com") to "email",
            body(email = "") to "email",
            body(email = ".example@dsm.hs.kr") to "email",
            """{"username":"Jun227"}""" to "요청 값이 올바르지 않습니다.",
        )

        invalidBodies.forEach { (invalid, expectedMessage) ->
            mockMvc.post("/auth/signup") {
                contentType = MediaType.APPLICATION_JSON
                content = invalid
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.code") { value("INVALID_INPUT") }
                jsonPath("$.message") { value(containsString(expectedMessage)) }
            }
        }
        assertEquals(0, userRepository.count())
    }

    private fun body(
        username: String = "Jun227",
        password: String = "Password1!",
        studentNumber: Int = 2315,
        name: String = "장윤호",
        email: String = EMAIL,
        emailVerifiedToken: String = TOKEN,
    ) = """
        {
          "username": "$username",
          "password": "$password",
          "studentNumber": $studentNumber,
          "name": "$name",
          "email": "$email",
          "emailVerifiedToken": "$emailVerifiedToken"
        }
    """.trimIndent()

    companion object {
        private const val EMAIL = "example@dsm.hs.kr"
        private const val TOKEN = "3f2a9c1e-7b4d-4e8a-9c2f-1d5e6a7b8c9d"
    }
}
