package app.xquare.xquareopenapi.domain.auth

import app.xquare.xquareopenapi.domain.auth.repository.EmailVerifiedTokenRepository
import app.xquare.xquareopenapi.domain.auth.repository.RefreshTokenRepository
import app.xquare.xquareopenapi.domain.user.Role
import app.xquare.xquareopenapi.domain.user.UserRepository
import app.xquare.xquareopenapi.global.jwt.JwtProperties
import com.jayway.jsonpath.JsonPath
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.hamcrest.Matchers.containsString
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.http.MediaType
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@SpringBootTest
@AutoConfigureMockMvc
class SignupTest @Autowired constructor(
    private val mockMvc: MockMvc,
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
    private val emailVerifiedTokens: FakeEmailVerifiedTokenRepository,
    private val refreshTokens: FakeRefreshTokenRepository,
    private val jwtProperties: JwtProperties,
) {

    @AfterEach
    fun tearDown() {
        userRepository.deleteAll()
        emailVerifiedTokens.tokens.clear()
        refreshTokens.tokens.clear()
        refreshTokens.ttls.clear()
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
    fun `형식에 맞지 않는 입력은 400`() {
        emailVerifiedTokens.tokens[TOKEN] = EMAIL
        val invalidBodies = listOf(
            body(username = "abc") to "username",
            body(username = "has space") to "username",
            body(password = "Password1") to "password",
            body(password = "Pw1!") to "password",
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

    @TestConfiguration
    class FakeRepositories {
        @Bean
        @Primary
        fun fakeEmailVerifiedTokenRepository() = FakeEmailVerifiedTokenRepository()

        @Bean
        @Primary
        fun fakeRefreshTokenRepository() = FakeRefreshTokenRepository()
    }

    class FakeEmailVerifiedTokenRepository : EmailVerifiedTokenRepository {
        val tokens = mutableMapOf<String, String>()

        override fun findEmailByToken(token: String): String? = tokens[token]

        override fun delete(token: String) {
            tokens.remove(token)
        }
    }

    class FakeRefreshTokenRepository : RefreshTokenRepository {
        val tokens = mutableMapOf<Long, String>()
        val ttls = mutableMapOf<Long, Duration>()

        override fun save(userId: Long, refreshToken: String, ttl: Duration) {
            tokens[userId] = refreshToken
            ttls[userId] = ttl
        }
    }

    companion object {
        private const val EMAIL = "example@dsm.hs.kr"
        private const val TOKEN = "3f2a9c1e-7b4d-4e8a-9c2f-1d5e6a7b8c9d"
    }
}
