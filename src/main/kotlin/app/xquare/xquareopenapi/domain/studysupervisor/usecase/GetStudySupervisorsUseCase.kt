package app.xquare.xquareopenapi.domain.studysupervisor.usecase

import app.xquare.xquareopenapi.domain.studysupervisor.presentation.dto.StudySupervisorRangeResponse
import app.xquare.xquareopenapi.global.error.ErrorCode
import app.xquare.xquareopenapi.global.error.XquareException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.temporal.ChronoUnit

@Service
class GetStudySupervisorsUseCase(
    private val studySupervisorReader: StudySupervisorReader,
) {

    // 급식 월간 조회와 같은 기준으로 기간을 검사한다.
    @Transactional(readOnly = true)
    fun execute(startDate: LocalDate, finishDate: LocalDate): StudySupervisorRangeResponse {
        if (finishDate.isBefore(startDate) || ChronoUnit.DAYS.between(startDate, finishDate) >= MAX_DAYS) {
            throw XquareException(ErrorCode.INVALID_DATE_RANGE)
        }
        return StudySupervisorRangeResponse(startDate, finishDate, studySupervisorReader.read(startDate, finishDate))
    }

    companion object {
        // 시작일과 종료일을 포함해 92일(석 달)까지 조회한다. 급식 월간 조회와 같은 값이다.
        private const val MAX_DAYS = 92
    }
}
