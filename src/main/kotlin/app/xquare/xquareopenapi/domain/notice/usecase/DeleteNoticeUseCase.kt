package app.xquare.xquareopenapi.domain.notice.usecase

import app.xquare.xquareopenapi.domain.notice.NoticeRepository
import app.xquare.xquareopenapi.global.error.ErrorCode
import app.xquare.xquareopenapi.global.error.XquareException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class DeleteNoticeUseCase(
    private val noticeRepository: NoticeRepository,
) {

    // 작성자가 아니어도 ADMIN이면 지울 수 있다. 실제로 지운다(하드 삭제).
    @Transactional
    fun execute(noticeId: Long) {
        if (noticeRepository.deleteByIdReturningCount(noticeId) == 0) {
            throw XquareException(ErrorCode.NOTICE_NOT_FOUND)
        }
    }
}
