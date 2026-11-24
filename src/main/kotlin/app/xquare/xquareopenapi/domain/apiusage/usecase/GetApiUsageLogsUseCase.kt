package app.xquare.xquareopenapi.domain.apiusage.usecase

import app.xquare.xquareopenapi.domain.apiusage.ApiUsageLog
import app.xquare.xquareopenapi.domain.apiusage.ApiUsageLogRepository
import app.xquare.xquareopenapi.domain.apiusage.presentation.API_USAGE_ROUTE_MAX_LENGTH
import app.xquare.xquareopenapi.domain.apiusage.presentation.ApiUsageLogQuery
import app.xquare.xquareopenapi.domain.apiusage.presentation.ApiUsageLogResponse
import app.xquare.xquareopenapi.global.dto.PageResponse
import app.xquare.xquareopenapi.global.error.ErrorCode
import app.xquare.xquareopenapi.global.error.XquareException
import app.xquare.xquareopenapi.global.jpa.escapeLike
import org.slf4j.LoggerFactory
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.data.jpa.domain.Specification
import org.springframework.stereotype.Service
import org.springframework.transaction.CannotCreateTransactionException
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionSystemException
import org.springframework.transaction.support.TransactionTemplate
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

@Service
class GetApiUsageLogsUseCase(
    private val repository: ApiUsageLogRepository,
    private val clock: Clock,
    transactionManager: PlatformTransactionManager,
) {
    private val readTransaction = TransactionTemplate(transactionManager).apply {
        isReadOnly = true
        timeout = 3
    }

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

        val routePattern = request.api?.let {
            if (it.length > API_USAGE_ROUTE_MAX_LENGTH || it.isBlank()) invalidInput()
            "%${it.escapeLike()}%"
        }
        val start = toStorageBoundary(from)
        val end = toStorageBoundary(to)
        val filters = Specification<ApiUsageLog> { root, _, cb ->
            val time = root.get<LocalDateTime>("occurredAt")
            val conditions = mutableListOf(cb.greaterThanOrEqualTo(time, start), cb.lessThan(time, end))
            request.method?.let { conditions += cb.equal(root.get<String>("method"), it.name) }
            request.status?.let { conditions += cb.equal(root.get<Int>("status"), it) }
            if (routePattern != null) {
                val route = root.get<String>("route")
                conditions += cb.notEqual(route, "UNMAPPED")
                conditions += cb.like(route, routePattern, '!')
            }
            cb.and(*conditions.toTypedArray())
        }
        val page = PageRequest.of(request.page, request.size, Sort.by(Sort.Direction.DESC, "occurredAt", "id"))
        return try {
            requireNotNull(readTransaction.execute {
                PageResponse.of(repository.findAll(filters, page).map(ApiUsageLogResponse::from))
            })
        } catch (_: CannotCreateTransactionException) {
            storageUnavailable()
        } catch (_: TransactionSystemException) {
            storageUnavailable()
        }
    }

    // DATETIME(6)에 저장 가능한 시각만 대상으로 하므로 양 끝을 올려도 [from, to)의 포함 관계가 같다.
    private fun toStorageBoundary(instant: Instant): LocalDateTime {
        val truncated = instant.truncatedTo(ChronoUnit.MICROS)
        val boundary = if (truncated == instant) truncated else truncated.plus(1, ChronoUnit.MICROS)
        return LocalDateTime.ofInstant(boundary, ZoneOffset.UTC)
    }

    private fun storageUnavailable(): Nothing {
        // 번역되지 않은 연결 획득·트랜잭션 종료 실패만 매핑한다. 기존 DataAccessException의 409/500은 그대로 둔다.
        log.error("API_USAGE_LOG_READ_UNAVAILABLE")
        throw XquareException(ErrorCode.INTERNAL_SERVER_ERROR)
    }

    private fun invalidInput(): Nothing = throw XquareException(ErrorCode.INVALID_INPUT)

    companion object {
        private val log = LoggerFactory.getLogger(GetApiUsageLogsUseCase::class.java)
        private val MIN_INSTANT = Instant.parse("1970-01-01T00:00:00Z")
        private val MAX_INSTANT = Instant.parse("9999-01-01T00:00:00Z")
        private val MAX_INTERVAL = Duration.ofDays(31)
    }
}
