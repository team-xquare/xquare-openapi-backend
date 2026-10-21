package app.xquare.xquareopenapi.domain.auth.usecase

import app.xquare.xquareopenapi.domain.auth.presentation.dto.LoginRequest
import app.xquare.xquareopenapi.domain.auth.presentation.dto.TokenResponse
import app.xquare.xquareopenapi.domain.user.UserRepository
import app.xquare.xquareopenapi.global.error.ErrorCode
import app.xquare.xquareopenapi.global.error.XquareException
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service

@Service
class LoginUseCase(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
    private val tokenIssuer: TokenIssuer,
) {

    fun execute(request: LoginRequest): TokenResponse {
        val user = userRepository.findByUsername(request.username)
        // 아이디가 없을 때도 BCrypt 비교를 해서, 응답 시간 차이로 아이디 존재 여부가 드러나지 않게 한다.
        val passwordMatches = passwordEncoder.matches(request.password, user?.password ?: DUMMY_PASSWORD_HASH)
        if (user == null || !passwordMatches) {
            throw XquareException(ErrorCode.INVALID_CREDENTIALS)
        }
        return tokenIssuer.issue(user)
    }

    companion object {
        // 아무 비밀번호와도 일치하지 않는 해시. 비교 시간이 실제 해시와 같도록 같은 cost(10)로 만들었다.
        private const val DUMMY_PASSWORD_HASH = "\$2a\$10\$3Wbng7j45KpxzAlR7FCvcuvQenIPMHZnPB2O4r8pc/Iju9amrq5aK"
    }
}
