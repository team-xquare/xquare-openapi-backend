package app.xquare.xquareopenapi.domain.notice.usecase

import app.xquare.xquareopenapi.domain.notice.NoticeRepository
import app.xquare.xquareopenapi.domain.notice.presentation.dto.NoticeListResponse
import app.xquare.xquareopenapi.domain.notice.presentation.dto.NoticeSearchRequest
import app.xquare.xquareopenapi.domain.notice.presentation.dto.NoticeSummaryResponse
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class GetNoticesUseCase(
    private val noticeRepository: NoticeRepository,
) {

    // 최신순은 작성일시 내림차순이다. 작성일시는 초 단위라 겹칠 수 있어서 id 내림차순을 더해 페이지를 넘겨도 순서가 흔들리지 않게 한다.
    @Transactional(readOnly = true)
    fun execute(request: NoticeSearchRequest): NoticeListResponse {
        val pageable = PageRequest.of(request.page, request.size, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")))
        return NoticeListResponse.of(noticeRepository.findPage(pageable).map(NoticeSummaryResponse::from))
    }
}
