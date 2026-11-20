package app.xquare.xquareopenapi.domain.apiusage.usecase

import app.xquare.xquareopenapi.domain.apiusage.ApiUsageLog
import app.xquare.xquareopenapi.domain.apiusage.ApiUsageLogRepository
import app.xquare.xquareopenapi.domain.apiusage.presentation.ApiUsageLogQuery
import app.xquare.xquareopenapi.domain.apiusage.presentation.ApiUsageLogResponse
import app.xquare.xquareopenapi.global.dto.PageResponse
import app.xquare.xquareopenapi.global.error.ErrorCode
import app.xquare.xquareopenapi.global.error.XquareException
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.data.jpa.domain.Specification
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset

@Service
class GetApiUsageLogsUseCase(private val repository: ApiUsageLogRepository, private val clock: Clock) {
    @Transactional(readOnly = true, timeout = 3)
    fun execute(request: ApiUsageLogQuery): PageResponse<ApiUsageLogResponse> {
        val from: Instant
        val to: Instant
        if (request.from == null && request.to == null) {
            to = clock.instant()
            from = to.minus(Duration.ofDays(1))
        } else {
            from = request.from ?: invalidInput()
            to = request.to ?: invalidInput()
        }
        if (from < MIN_INSTANT || to > MAX_INSTANT || from >= to || Duration.between(from, to) > MAX_INTERVAL) {
            invalidInput()
        }

        val start = LocalDateTime.ofInstant(from, ZoneOffset.UTC)
        val end = LocalDateTime.ofInstant(to, ZoneOffset.UTC)
        val filters = Specification<ApiUsageLog> { root, _, cb ->
            val time = root.get<LocalDateTime>("occurredAt")
            val conditions = mutableListOf(cb.greaterThanOrEqualTo(time, start), cb.lessThan(time, end))
            request.method?.let { conditions += cb.equal(root.get<String>("method"), it.name) }
            request.status?.let { conditions += cb.equal(root.get<Int>("status"), it) }
            cb.and(*conditions.toTypedArray())
        }
        val page = PageRequest.of(request.page, request.size, Sort.by(Sort.Direction.DESC, "occurredAt", "id"))
        return PageResponse.of(repository.findAll(filters, page).map(ApiUsageLogResponse::from))
    }

    private fun invalidInput(): Nothing = throw XquareException(ErrorCode.INVALID_INPUT)

    companion object {
        private val MIN_INSTANT = Instant.parse("1970-01-01T00:00:00Z")
        private val MAX_INSTANT = Instant.parse("9999-01-01T00:00:00Z")
        private val MAX_INTERVAL = Duration.ofDays(31)
    }
}
