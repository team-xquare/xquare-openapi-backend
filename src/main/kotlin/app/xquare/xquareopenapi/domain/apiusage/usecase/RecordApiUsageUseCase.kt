package app.xquare.xquareopenapi.domain.apiusage.usecase

import app.xquare.xquareopenapi.domain.apiusage.ApiUsageLog
import app.xquare.xquareopenapi.domain.apiusage.ApiUsageLogRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

@Service
class RecordApiUsageUseCase(private val repository: ApiUsageLogRepository) {
    @Transactional(propagation = Propagation.REQUIRES_NEW, timeout = 2)
    fun execute(log: ApiUsageLog) {
        repository.save(log)
    }
}
