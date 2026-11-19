package app.xquare.xquareopenapi.domain.notice.usecase

import app.xquare.xquareopenapi.domain.notice.NoticeRepository
import app.xquare.xquareopenapi.domain.notice.presentation.dto.NoticeDetailResponse
import app.xquare.xquareopenapi.global.error.ErrorCode
import app.xquare.xquareopenapi.global.error.XquareException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class GetNoticeUseCase(
    private val noticeRepository: NoticeRepository,
) {

    @Transactional(readOnly = true)
    fun execute(noticeId: Long): NoticeDetailResponse {
        val notice = noticeRepository.findWithAuthorById(noticeId) ?: throw XquareException(ErrorCode.NOTICE_NOT_FOUND)
        return NoticeDetailResponse.from(notice)
    }
}
