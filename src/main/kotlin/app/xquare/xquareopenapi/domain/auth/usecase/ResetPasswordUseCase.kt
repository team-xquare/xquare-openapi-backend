package app.xquare.xquareopenapi.domain.auth.usecase

import app.xquare.xquareopenapi.domain.auth.presentation.dto.MessageResponse
import app.xquare.xquareopenapi.domain.auth.presentation.dto.PasswordResetRequest
import app.xquare.xquareopenapi.domain.auth.repository.EmailVerifiedTokenRepository
import app.xquare.xquareopenapi.domain.auth.repository.RefreshTokenRepository
import app.xquare.xquareopenapi.domain.user.UserRepository
import app.xquare.xquareopenapi.global.error.ErrorCode
import app.xquare.xquareopenapi.global.error.XquareException
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

// 이메일 OTP로 받은 인증 토큰으로 비밀번호를 바꾼다. 토큰은 회원가입과 같은 것을 쓴다(용도 구분 없음).
@Service
class ResetPasswordUseCase(
    private val userRepository: UserRepository,
    private val emailVerifiedTokenRepository: EmailVerifiedTokenRepository,
    private val refreshTokenRepository: RefreshTokenRepository,
    private val passwordEncoder: PasswordEncoder,
) {

    @Transactional
    fun execute(request: PasswordResetRequest): MessageResponse {
        val email = emailVerifiedTokenRepository.findEmailByToken(request.emailVerifiedToken)
            ?: throw XquareException(ErrorCode.EMAIL_NOT_VERIFIED)
        // 가입하지 않은 이메일이면 토큰을 소비하지 않아 회원가입에 쓸 수 있다.
        val user = userRepository.findByEmail(email) ?: throw XquareException(ErrorCode.USER_NOT_FOUND)
        // 비밀번호를 바꾸기 전에 원자적으로 소비한다. 같은 토큰으로 동시에 들어온 요청은 하나만 여기를 지나고 나머지는 403이다.
        // 이 뒤에 실패해 롤백돼도 토큰은 되살리지 않는다(1회성 우선). 다시 하려면 OTP 인증부터 다시 받는다.
        emailVerifiedTokenRepository.consume(request.emailVerifiedToken)
            ?: throw XquareException(ErrorCode.EMAIL_NOT_VERIFIED)

        user.changePassword(passwordEncoder.encode(request.newPassword)!!)
        // 커밋 전에 지운다. 지우기에 실패하면 비밀번호 변경도 롤백되어, 기존 세션이 살아 있는 채로 비밀번호만 바뀌는 일이 없다.
        // 이 삭제와 커밋 사이에 옛 비밀번호로 검증을 마친 로그인이 저장하는 토큰은 재발급에서 막는다(ReissueUseCase).
        refreshTokenRepository.delete(user.id!!)

        return MessageResponse("비밀번호가 변경되었습니다.")
    }
}
