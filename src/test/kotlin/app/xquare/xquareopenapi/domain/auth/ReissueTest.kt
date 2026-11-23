package app.xquare.xquareopenapi.domain.auth

import app.xquare.xquareopenapi.domain.user.Role
import app.xquare.xquareopenapi.domain.user.User
import app.xquare.xquareopenapi.domain.user.UserRepository
import app.xquare.xquareopenapi.global.jwt.JwtProperties
import app.xquare.xquareopenapi.global.jwt.JwtProvider
import com.jayway.jsonpath.JsonPath
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActionsDsl
import org.springframework.test.web.servlet.post
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

@SpringBootTest
@AutoConfigureMockMvc
@Import(FakeAuthRepositories::class)
class ReissueTest @Autowired constructor(
    private val mockMvc: MockMvc,
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
    private val refreshTokens: FakeRefreshTokenRepository,
    private val jwtProperties: JwtProperties,
    private val jwtProvider: JwtProvider,
) {
    private var userId: Long = 0

    @BeforeEach
    fun setUp() {
        userId = userRepository.save(
            User("Jun227", passwordEncoder.encode(PASSWORD)!!, 2315, "장윤호", "example@dsm.hs.kr"),
        ).id!!
    }

    @AfterEach
    fun tearDown() {
        userRepository.deleteAll()
        refreshTokens.clear()
    }

    @Test
    fun `재발급하면 새 토큰 쌍을 주고 저장된 리프레시 토큰을 새 것으로 바꾼다`() {
        val oldRefreshToken = loginAndGetRefreshToken()

        val response = reissue(oldRefreshToken).andExpect {
            status { isOk() }
        }.andReturn().response.contentAsString

        val newRefreshToken = JsonPath.read<String>(response, "$.refreshToken")
        assertNotEquals(oldRefreshToken, newRefreshToken)
        assertEquals(newRefreshToken, refreshTokens.tokens[userId])
        assertEquals(jwtProperties.refreshExpiration, refreshTokens.ttls[userId])
        val claims = Jwts.parser()
            .verifyWith(Keys.hmacShaKeyFor(jwtProperties.secret.toByteArray()))
            .build()
            .parseSignedClaims(JsonPath.read<String>(response, "$.accessToken"))
            .payload
        assertEquals(userId.toString(), claims.subject)
        assertEquals("USER", claims["role"])
    }

    @Test
    fun `재발급에 쓴 리프레시 토큰은 다시 쓸 수 없다`() {
        val oldRefreshToken = loginAndGetRefreshToken()
        reissue(oldRefreshToken).andExpect { status { isOk() } }

        reissue(oldRefreshToken).andExpectInvalidRefreshToken()
    }

    @Test
    fun `다시 로그인하면 이전 리프레시 토큰으로는 재발급할 수 없다`() {
        val firstRefreshToken = loginAndGetRefreshToken()
        loginAndGetRefreshToken()

        reissue(firstRefreshToken).andExpectInvalidRefreshToken()
    }

    @Test
    fun `저장된 적 없는 리프레시 토큰은 INVALID_REFRESH_TOKEN`() {
        val passwordHash = userRepository.findById(userId).get().password
        reissue(jwtProvider.generateRefreshToken(userId, passwordHash).value).andExpectInvalidRefreshToken()
    }

    @Test
    fun `발급 뒤 비밀번호가 바뀌었으면 저장된 리프레시 토큰이어도 재발급할 수 없다`() {
        val refreshToken = loginAndGetRefreshToken()
        val user = userRepository.findById(userId).get()
        user.changePassword(passwordEncoder.encode("NewPassword1!")!!)
        userRepository.save(user)

        reissue(refreshToken).andExpectInvalidRefreshToken()
        // 지문 검사에서 걸려 저장된 토큰은 바뀌지 않는다.
        assertEquals(refreshToken, refreshTokens.tokens[userId])
    }

    @Test
    fun `재발급한 리프레시 토큰도 지금 비밀번호의 지문을 담아 다시 재발급할 수 있다`() {
        val first = loginAndGetRefreshToken()
        val second = JsonPath.read<String>(reissue(first).andReturn().response.contentAsString, "$.refreshToken")

        val passwordHash = userRepository.findById(userId).get().password
        assertEquals(jwtProvider.passwordFingerprint(passwordHash), jwtProvider.parseRefreshToken(second)!!.passwordFingerprint)
        reissue(second).andExpect { status { isOk() } }
    }

    @Test
    fun `리프레시 토큰이 아니거나 형식이 깨졌으면 INVALID_REFRESH_TOKEN`() {
        reissue(jwtProvider.generateAccessToken(userId, Role.USER)).andExpectInvalidRefreshToken()
        reissue("not-a-jwt").andExpectInvalidRefreshToken()
    }

    @Test
    fun `탈퇴한 사용자의 리프레시 토큰은 INVALID_REFRESH_TOKEN`() {
        val refreshToken = loginAndGetRefreshToken()
        userRepository.deleteAll()

        reissue(refreshToken).andExpectInvalidRefreshToken()
    }

    @Test
    fun `리프레시 토큰이 비어 있으면 400`() {
        listOf("""{"refreshToken":" "}""", "{}").forEach { invalid ->
            mockMvc.post("/auth/reissue") {
                contentType = MediaType.APPLICATION_JSON
                content = invalid
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.code") { value("INVALID_INPUT") }
            }
        }
    }

    private fun loginAndGetRefreshToken(): String {
        val response = mockMvc.post("/auth/login") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"username":"Jun227","password":"$PASSWORD"}"""
        }.andReturn().response.contentAsString
        return JsonPath.read(response, "$.refreshToken")
    }

    private fun reissue(refreshToken: String) =
        mockMvc.post("/auth/reissue") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"refreshToken":"$refreshToken"}"""
        }

    private fun ResultActionsDsl.andExpectInvalidRefreshToken() =
        andExpect {
            status { isUnauthorized() }
            jsonPath("$.code") { value("INVALID_REFRESH_TOKEN") }
            jsonPath("$.message") { value("리프레시 토큰이 없거나 만료 또는 폐기되었습니다. 재로그인이 필요합니다.") }
        }

    companion object {
        private const val PASSWORD = "Password1!"
    }
}
