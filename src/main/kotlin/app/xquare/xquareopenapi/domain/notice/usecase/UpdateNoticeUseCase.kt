package app.xquare.xquareopenapi.domain.notice.usecase

import app.xquare.xquareopenapi.domain.notice.NoticeRepository
import app.xquare.xquareopenapi.domain.notice.presentation.dto.NoticeRequest
import app.xquare.xquareopenapi.global.error.ErrorCode
import app.xquare.xquareopenapi.global.error.XquareException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock

@Service
class UpdateNoticeUseCase(
    private val noticeRepository: NoticeRepository,
    private val clock: Clock,
) {

    // 작성자가 아니어도 ADMIN이면 고칠 수 있다. 작성자는 그대로 두고 수정일시만 바꾼다. 같은 값으로 고쳐도 수정한 것으로 본다.
    @Transactional
    fun execute(noticeId: Long, request: NoticeRequest) {
        if (noticeRepository.updateByIdReturningCount(noticeId, request.title, request.content, now(clock)) == 0) {
            throw XquareException(ErrorCode.NOTICE_NOT_FOUND)
        }
    }
}
