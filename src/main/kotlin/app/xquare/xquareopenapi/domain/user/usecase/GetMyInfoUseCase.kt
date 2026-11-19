package app.xquare.xquareopenapi.domain.user.usecase

import app.xquare.xquareopenapi.domain.user.UserRepository
import app.xquare.xquareopenapi.domain.user.presentation.dto.MyInfoResponse
import app.xquare.xquareopenapi.global.error.ErrorCode
import app.xquare.xquareopenapi.global.error.XquareException
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class GetMyInfoUseCase(
    private val userRepository: UserRepository,
) {

    // 토큰은 유효하지만 사용자가 DB에서 지워진 경우, 토큰의 주인이 없으므로 유효하지 않은 토큰과 같이 401로 응답한다.
    @Transactional(readOnly = true)
    fun execute(userId: Long): MyInfoResponse {
        val user = userRepository.findByIdOrNull(userId) ?: throw XquareException(ErrorCode.INVALID_TOKEN)
        return MyInfoResponse.from(user)
    }
}
