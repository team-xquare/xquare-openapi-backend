package app.xquare.xquareopenapi.global.jwt

import app.xquare.xquareopenapi.domain.user.Role
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.security.core.Authentication
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActionsDsl
import org.springframework.test.web.servlet.get
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Duration

@SpringBootTest
@AutoConfigureMockMvc
@Import(AccessTokenAuthenticationTest.ProtectedEndpoint::class)
class AccessTokenAuthenticationTest @Autowired constructor(
    private val mockMvc: MockMvc,
    private val jwtProvider: JwtProvider,
    private val jwtProperties: JwtProperties,
) {

    @Test
    fun `유효한 액세스 토큰이면 사용자 id와 권한으로 인증한다`() {
        request("Bearer ${jwtProvider.generateAccessToken(7L, Role.USER)}").andExpect {
            status { isOk() }
            content { string("7 [ROLE_USER]") }
        }
        request("Bearer ${jwtProvider.generateAccessToken(8L, Role.ADMIN)}").andExpect {
            status { isOk() }
            content { string("8 [ROLE_ADMIN]") }
        }
    }

    @Test
    fun `Bearer는 대소문자를 구분하지 않는다`() {
        request("bearer ${jwtProvider.generateAccessToken(7L, Role.USER)}").andExpect {
            status { isOk() }
        }
    }

    @Test
    fun `토큰이 없으면 INVALID_TOKEN`() {
        request(null).andExpectInvalidToken()
    }

    @Test
    fun `유효하지 않은 토큰이면 INVALID_TOKEN`() {
        val accessToken = jwtProvider.generateAccessToken(7L, Role.USER)
        val expired = JwtProvider(jwtProperties.copy(accessExpiration = Duration.ofSeconds(-1)))
            .generateAccessToken(7L, Role.USER)
        val otherKey = JwtProvider(jwtProperties.copy(secret = "another-secret-key-must-be-32-bytes-long!"))
            .generateAccessToken(7L, Role.USER)

        listOf(
            "Bearer ${jwtProvider.generateRefreshToken(7L).value}",
            "Bearer $expired",
            "Bearer $otherKey",
            "Bearer not-a-jwt",
            "Bearer ",
            "Bearer",
            accessToken,
            "Basic $accessToken",
            "XBearer $accessToken",
        ).forEach { request(it).andExpectInvalidToken() }
    }

    private fun request(authorization: String?) = mockMvc.get("/test/me") {
        if (authorization != null) header("Authorization", authorization)
    }

    private fun ResultActionsDsl.andExpectInvalidToken() = andExpect {
        status { isUnauthorized() }
        content { contentType("application/json;charset=UTF-8") }
        jsonPath("$.code") { value("INVALID_TOKEN") }
        jsonPath("$.message") { value("토큰이 없거나 유효하지 않습니다.") }
    }

    // 이 PR에는 아직 인증이 필요한 API가 없어서, 인증 결과를 그대로 돌려주는 테스트용 API를 둔다.
    @TestConfiguration
    class ProtectedEndpoint {
        @Bean
        fun meController() = MeController()
    }

    @RestController
    class MeController {
        @GetMapping("/test/me")
        fun me(authentication: Authentication) = "${authentication.principal} ${authentication.authorities}"
    }
}
