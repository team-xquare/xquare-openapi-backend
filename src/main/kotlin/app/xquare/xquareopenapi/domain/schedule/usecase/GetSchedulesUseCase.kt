package app.xquare.xquareopenapi.domain.schedule.usecase

import app.xquare.xquareopenapi.domain.schedule.ScheduleRepository
import app.xquare.xquareopenapi.domain.schedule.presentation.dto.ScheduleResponse
import app.xquare.xquareopenapi.global.error.ErrorCode
import app.xquare.xquareopenapi.global.error.XquareException
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Limit
import org.springframework.stereotype.Service
import org.springframework.transaction.CannotCreateTransactionException
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionSystemException
import org.springframework.transaction.support.TransactionTemplate
import java.time.LocalDate
import java.time.temporal.ChronoUnit

@Service
class GetSchedulesUseCase(
    private val scheduleRepository: ScheduleRepository,
    transactionManager: PlatformTransactionManager,
) {
    private val readTransaction = TransactionTemplate(transactionManager).apply {
        isReadOnly = true
        timeout = 5
    }

    fun execute(startDate: LocalDate, finishDate: LocalDate): List<ScheduleResponse> {
        if (finishDate.isBefore(startDate) || ChronoUnit.DAYS.between(startDate, finishDate) >= MAX_DAYS) {
            throw XquareException(ErrorCode.INVALID_DATE_RANGE)
        }

        // 한 건만 더 읽어 초과를 감지한다. 잘린 목록을 성공으로 반환하지 않는다.
        val schedules = try {
            requireNotNull(readTransaction.execute {
                scheduleRepository.findAllByDateBetweenOrderByDateAscIdAsc(startDate, finishDate, Limit.of(MAX_RESULTS + 1))
            })
        } catch (_: CannotCreateTransactionException) {
            storageUnavailable()
        } catch (_: TransactionSystemException) {
            storageUnavailable()
        }
        if (schedules.size > MAX_RESULTS) throw XquareException(ErrorCode.SCHEDULE_LIST_LIMIT_EXCEEDED)
        return schedules.map(ScheduleResponse::from)
    }

    private fun storageUnavailable(): Nothing {
        log.error("SCHEDULE_LIST_READ_UNAVAILABLE")
        throw XquareException(ErrorCode.INTERNAL_SERVER_ERROR)
    }

    companion object {
        private val log = LoggerFactory.getLogger(GetSchedulesUseCase::class.java)
        const val MAX_DAYS = 92
        const val MAX_RESULTS = 500
    }
}