package app.xquare.xquareopenapi.domain.user

import app.xquare.xquareopenapi.global.jwt.JwtProvider
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get

@SpringBootTest
@AutoConfigureMockMvc
class MyInfoTest @Autowired constructor(
    private val mockMvc: MockMvc,
    private val userRepository: UserRepository,
    private val jwtProvider: JwtProvider,
) {

    @AfterEach
    fun tearDown() {
        userRepository.deleteAll()
    }

    @Test
    fun `로그인한 사용자는 자기 정보를 받고 비밀번호는 응답에 없다`() {
        val user = save(username = "Jun227", email = "jun@dsm.hs.kr")
        // 다른 사용자의 정보가 섞이지 않는지 확인하려고 한 명 더 둔다.
        save(username = "other", email = "other@dsm.hs.kr")

        mockMvc.get("/users/me") {
            header("Authorization", "Bearer ${jwtProvider.generateAccessToken(user.id!!, Role.USER)}")
        }.andExpect {
            status { isOk() }
            jsonPath("$.id") { value(user.id!!) }
            jsonPath("$.username") { value("Jun227") }
            jsonPath("$.name") { value("장윤호") }
            jsonPath("$.studentNumber") { value(2315) }
            jsonPath("$.email") { value("jun@dsm.hs.kr") }
            jsonPath("$.role") { value("USER") }
            jsonPath("$.password") { doesNotExist() }
        }
    }

    @Test
    fun `ADMIN도 자기 정보를 받는다`() {
        val admin = save(username = "admin", email = "admin@dsm.hs.kr", role = Role.ADMIN)

        mockMvc.get("/users/me") {
            header("Authorization", "Bearer ${jwtProvider.generateAccessToken(admin.id!!, Role.ADMIN)}")
        }.andExpect {
            status { isOk() }
            jsonPath("$.username") { value("admin") }
            jsonPath("$.role") { value("ADMIN") }
        }
    }

    @Test
    fun `토큰이 없으면 401 INVALID_TOKEN`() {
        mockMvc.get("/users/me").andExpect {
            status { isUnauthorized() }
            jsonPath("$.code") { value("INVALID_TOKEN") }
        }
    }

    @Test
    fun `토큰은 유효하지만 사용자가 없으면 401 INVALID_TOKEN`() {
        mockMvc.get("/users/me") {
            header("Authorization", "Bearer ${jwtProvider.generateAccessToken(999_999L, Role.USER)}")
        }.andExpect {
            status { isUnauthorized() }
            jsonPath("$.code") { value("INVALID_TOKEN") }
            jsonPath("$.message") { value("토큰이 없거나 유효하지 않습니다.") }
        }
    }

    private fun save(username: String, email: String, role: Role = Role.USER): User =
        userRepository.save(
            User(
                username = username,
                password = "\$2a\$10\$a6wfPHMLhYVTckAbxAd5WO2x3.p1./7F8M2qxp0sOaRVs7izLkgwK",
                studentNumber = 2315,
                name = "장윤호",
                email = email,
                role = role,
            ),
        )
}
