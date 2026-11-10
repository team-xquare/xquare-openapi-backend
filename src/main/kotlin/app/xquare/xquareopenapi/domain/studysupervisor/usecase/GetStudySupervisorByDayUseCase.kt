package app.xquare.xquareopenapi.domain.studysupervisor.usecase

import app.xquare.xquareopenapi.domain.studysupervisor.presentation.dto.StudySupervisorDayResponse
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

@Service
class GetStudySupervisorByDayUseCase(
    private val studySupervisorReader: StudySupervisorReader,
) {

    @Transactional(readOnly = true)
    fun execute(date: LocalDate): StudySupervisorDayResponse =
        studySupervisorReader.read(date, date).single()
}
