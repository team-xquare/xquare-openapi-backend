package app.xquare.xquareopenapi.domain.auth

import app.xquare.xquareopenapi.domain.user.User
import app.xquare.xquareopenapi.domain.user.UserRepository
import org.junit.jupiter.api.AfterEach
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
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@SpringBootTest
@AutoConfigureMockMvc
@Import(FakeAuthRepositories::class)
class PasswordResetTest @Autowired constructor(
    private val mockMvc: MockMvc,
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
    private val emailVerifiedTokens: FakeEmailVerifiedTokenRepository,
    private val refreshTokens: FakeRefreshTokenRepository,
) {

    @AfterEach
    fun tearDown() {
        userRepository.deleteAll()
        emailVerifiedTokens.tokens.clear()
        emailVerifiedTokens.failOnDelete = false
        refreshTokens.clear()
    }

    @Test
    fun `인증 토큰의 이메일로 가입한 사용자의 비밀번호를 바꾸고 토큰을 지운다`() {
        val user = saveUser()
        emailVerifiedTokens.tokens[TOKEN] = EMAIL

        reset().andExpect {
            status { isOk() }
            jsonPath("$.message") { value("비밀번호가 변경되었습니다.") }
        }

        val saved = userRepository.findById(user.id!!).get()
        assertTrue(passwordEncoder.matches(NEW_PASSWORD, saved.password))
        assertFalse(passwordEncoder.matches(OLD_PASSWORD, saved.password))
        assertNull(emailVerifiedTokens.tokens[TOKEN])
    }

    @Test
    fun `바꾼 비밀번호로 로그인되고 이전 비밀번호로는 안 된다`() {
        saveUser()
        emailVerifiedTokens.tokens[TOKEN] = EMAIL

        reset().andExpect { status { isOk() } }

        login(NEW_PASSWORD).andExpect { status { isOk() } }
        login(OLD_PASSWORD).andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `재설정하면 그 사용자의 리프레시 토큰만 지워져 기존 세션은 재발급하지 못한다`() {
        val user = saveUser()
        val other = saveUser(username = "other", email = "other@dsm.hs.kr")
        refreshTokens.tokens[user.id!!] = "old-refresh"
        refreshTokens.tokens[other.id!!] = "other-refresh"
        emailVerifiedTokens.tokens[TOKEN] = EMAIL

        reset().andExpect { status { isOk() } }

        assertNull(refreshTokens.tokens[user.id!!])
        assertEquals("other-refresh", refreshTokens.tokens[other.id!!])
    }

    @Test
    fun `인증 토큰이 없거나 만료되면 403 EMAIL_NOT_VERIFIED이고 비밀번호는 그대로다`() {
        val user = saveUser()

        reset().andExpect {
            status { isForbidden() }
            jsonPath("$.code") { value("EMAIL_NOT_VERIFIED") }
        }

        assertTrue(passwordEncoder.matches(OLD_PASSWORD, userRepository.findById(user.id!!).get().password))
    }

    @Test
    fun `토큰의 이메일로 가입한 사용자가 없으면 404 USER_NOT_FOUND이고 토큰은 남는다`() {
        emailVerifiedTokens.tokens[TOKEN] = EMAIL

        reset().andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value("USER_NOT_FOUND") }
            jsonPath("$.message") { value("가입되지 않은 이메일입니다.") }
        }

        assertEquals(EMAIL, emailVerifiedTokens.tokens[TOKEN])
    }

    @Test
    fun `리프레시 토큰 삭제에 실패하면 500이고 비밀번호와 인증 토큰은 그대로다`() {
        val user = saveUser()
        refreshTokens.tokens[user.id!!] = "old-refresh"
        refreshTokens.failOnDelete = true
        emailVerifiedTokens.tokens[TOKEN] = EMAIL

        reset().andExpect {
            status { isInternalServerError() }
            jsonPath("$.code") { value("INTERNAL_SERVER_ERROR") }
        }

        assertTrue(passwordEncoder.matches(OLD_PASSWORD, userRepository.findById(user.id!!).get().password))
        assertEquals(EMAIL, emailVerifiedTokens.tokens[TOKEN])
    }

    @Test
    fun `인증 토큰 삭제에 실패해도 재설정은 성공한다`() {
        val user = saveUser()
        emailVerifiedTokens.tokens[TOKEN] = EMAIL
        emailVerifiedTokens.failOnDelete = true

        reset().andExpect { status { isOk() } }

        assertTrue(passwordEncoder.matches(NEW_PASSWORD, userRepository.findById(user.id!!).get().password))
    }

    @Test
    fun `새 비밀번호는 회원가입과 같은 규칙을 지켜야 한다`() {
        saveUser()
        emailVerifiedTokens.tokens[TOKEN] = EMAIL
        // 특수문자 없음, 7자, 21자, 공백 포함, 비ASCII
        listOf("Password12", "Pass1!a", "Password1!Password1!a", "Pass word1!", "비밀번호1234!").forEach { password ->
            reset(newPassword = password).andExpect {
                status { isBadRequest() }
                jsonPath("$.code") { value("INVALID_INPUT") }
            }
        }
        assertEquals(EMAIL, emailVerifiedTokens.tokens[TOKEN])
    }

    @Test
    fun `새 비밀번호는 8자와 20자를 받는다`() {
        listOf("Passw1!a", "Password1!Password1!").forEachIndexed { i, password ->
            saveUser(username = "user$i", email = "user$i@dsm.hs.kr")
            emailVerifiedTokens.tokens["token-$i"] = "user$i@dsm.hs.kr"

            reset(token = "token-$i", newPassword = password).andExpect { status { isOk() } }
        }
    }

    @Test
    fun `인증 토큰이 비어 있으면 400 INVALID_INPUT`() {
        reset(token = " ").andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("INVALID_INPUT") }
        }
    }

    private fun reset(token: String = TOKEN, newPassword: String = NEW_PASSWORD): ResultActionsDsl =
        mockMvc.post("/auth/password/reset") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"emailVerifiedToken":"$token","newPassword":"$newPassword"}"""
        }

    private fun login(password: String): ResultActionsDsl =
        mockMvc.post("/auth/login") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"username":"Jun227","password":"$password"}"""
        }

    private fun saveUser(username: String = "Jun227", email: String = EMAIL): User =
        userRepository.save(
            User(
                username = username,
                password = passwordEncoder.encode(OLD_PASSWORD)!!,
                studentNumber = 2315,
                name = "장윤호",
                email = email,
            ),
        )

    companion object {
        private const val TOKEN = "verified-token"
        private const val EMAIL = "jun@dsm.hs.kr"
        private const val OLD_PASSWORD = "OldPassword1!"
        private const val NEW_PASSWORD = "NewPassword1!"
    }
}
