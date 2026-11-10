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
    // 바뀐 행 수가 0이면 없는 공지로 본다. 이 판정은 MySQL Connector/J 기본값(useAffectedRows=false)에 기댄다.
    // 기본값에서는 UPDATE가 조건에 맞은 행 수를 돌려줘서 값이 그대로여도 1이다. DB_URL에 useAffectedRows=true를 붙이면
    // 실제로 값이 바뀐 행 수만 돌려줘서, 같은 초에 같은 값으로 두 번 고칠 때 두 번째가 404가 된다. 그래서 그 옵션을 쓰면 안 된다(#30 리뷰).
    @Transactional
    fun execute(noticeId: Long, request: NoticeRequest) {
        if (noticeRepository.updateByIdReturningCount(noticeId, request.title, request.content, now(clock)) == 0) {
            throw XquareException(ErrorCode.NOTICE_NOT_FOUND)
        }
    }
}
