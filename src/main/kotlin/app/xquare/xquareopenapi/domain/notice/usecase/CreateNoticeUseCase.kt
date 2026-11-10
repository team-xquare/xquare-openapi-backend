package app.xquare.xquareopenapi.domain.notice.usecase

import app.xquare.xquareopenapi.domain.notice.Notice
import app.xquare.xquareopenapi.domain.notice.NoticeRepository
import app.xquare.xquareopenapi.domain.notice.presentation.dto.NoticeRequest
import app.xquare.xquareopenapi.domain.user.UserRepository
import app.xquare.xquareopenapi.global.error.ErrorCode
import app.xquare.xquareopenapi.global.error.XquareException
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock

@Service
class CreateNoticeUseCase(
    private val noticeRepository: NoticeRepository,
    private val userRepository: UserRepository,
    private val clock: Clock,
) {

    // 등록한 공지의 id를 돌려준다. 작성자는 토큰의 사용자다.
    // 토큰은 유효한데 사용자가 지워졌으면 작성자를 정할 수 없어서 토큰이 유효하지 않은 것으로 본다.
    @Transactional
    fun execute(userId: Long, request: NoticeRequest): Long {
        val author = userRepository.findByIdOrNull(userId) ?: throw XquareException(ErrorCode.INVALID_TOKEN)
        val notice = noticeRepository.save(Notice(request.title, request.content, author, now(clock)))
        return notice.id!!
    }
}
