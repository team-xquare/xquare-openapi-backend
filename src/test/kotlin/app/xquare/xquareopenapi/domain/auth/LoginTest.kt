package app.xquare.xquareopenapi.domain.auth

import app.xquare.xquareopenapi.domain.user.User
import app.xquare.xquareopenapi.domain.user.UserRepository
import app.xquare.xquareopenapi.global.jwt.JwtProperties
import com.jayway.jsonpath.JsonPath
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.eq
import org.mockito.ArgumentMatchers.startsWith
import org.mockito.Mockito.clearInvocations
import org.mockito.Mockito.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActionsDsl
import org.springframework.test.web.servlet.post
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@SpringBootTest
@AutoConfigureMockMvc
@Import(FakeAuthRepositories::class)
class LoginTest @Autowired constructor(
    private val mockMvc: MockMvc,
    private val userRepository: UserRepository,
    private val refreshTokens: FakeRefreshTokenRepository,
    private val jwtProperties: JwtProperties,
) {
    // 없는 아이디에도 비밀번호 비교를 하는지 확인하려고 감싼다.
    @MockitoSpyBean
    private lateinit var passwordEncoder: PasswordEncoder

    private var userId: Long = 0

    @BeforeEach
    fun setUp() {
        userId = userRepository.save(
            User("Jun227", passwordEncoder.encode(PASSWORD)!!, 2315, "장윤호", "example@dsm.hs.kr"),
        ).id!!
        clearInvocations(passwordEncoder)
    }

    @AfterEach
    fun tearDown() {
        userRepository.deleteAll()
        refreshTokens.clear()
    }

    @Test
    fun `로그인하면 자기 id가 담긴 토큰을 발급하고 리프레시 토큰을 저장한다`() {
        val response = login(body()).andExpect {
            status { isOk() }
            jsonPath("$.accessToken") { isNotEmpty() }
        }.andReturn().response.contentAsString

        val accessToken = JsonPath.read<String>(response, "$.accessToken")
        val refreshToken = JsonPath.read<String>(response, "$.refreshToken")
        val claims = Jwts.parser()
            .verifyWith(Keys.hmacShaKeyFor(jwtProperties.secret.toByteArray()))
            .build()
            .parseSignedClaims(accessToken)
            .payload
        assertEquals(userId.toString(), claims.subject)
        assertEquals("USER", claims["role"])
        assertEquals(refreshToken, refreshTokens.tokens[userId])
        assertEquals(jwtProperties.refreshExpiration, refreshTokens.ttls[userId])
    }

    @Test
    fun `비밀번호가 틀리면 INVALID_CREDENTIALS`() {
        login(body(password = "Wrong1234!")).andExpectInvalidCredentials()

        assertTrue(refreshTokens.tokens.isEmpty())
    }

    @Test
    fun `없는 아이디면 비밀번호가 틀렸을 때와 같은 INVALID_CREDENTIALS`() {
        login(body(username = "nobody")).andExpectInvalidCredentials()
    }

    @Test
    fun `없는 아이디여도 비밀번호 비교를 거쳐 응답 시간으로 아이디 존재 여부가 드러나지 않는다`() {
        login(body(username = "nobody")).andExpectInvalidCredentials()

        verify(passwordEncoder).matches(eq(PASSWORD), startsWith("$2a$10$"))
    }

    @Test
    fun `BCrypt 한도인 72바이트를 넘는 비밀번호도 500이 아니라 INVALID_CREDENTIALS`() {
        login(body(password = "a".repeat(73))).andExpectInvalidCredentials()
    }

    @Test
    fun `아이디나 비밀번호가 비어 있으면 400`() {
        listOf(
            body(username = " "),
            body(password = ""),
            """{"username":"Jun227"}""",
        ).forEach { invalid ->
            login(invalid).andExpect {
                status { isBadRequest() }
                jsonPath("$.code") { value("INVALID_INPUT") }
            }
        }
    }

    private fun login(body: String) =
        mockMvc.post("/auth/login") {
            contentType = MediaType.APPLICATION_JSON
            content = body
        }

    private fun ResultActionsDsl.andExpectInvalidCredentials() =
        andExpect {
            status { isUnauthorized() }
            jsonPath("$.code") { value("INVALID_CREDENTIALS") }
            jsonPath("$.message") { value("아이디 또는 비밀번호가 올바르지 않습니다.") }
        }

    private fun body(username: String = "Jun227", password: String = PASSWORD) =
        """{"username":"$username","password":"$password"}"""

    companion object {
        private const val PASSWORD = "Password1!"
    }
}
