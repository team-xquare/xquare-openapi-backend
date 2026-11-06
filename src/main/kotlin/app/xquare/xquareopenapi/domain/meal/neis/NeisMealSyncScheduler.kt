package app.xquare.xquareopenapi.domain.meal.neis

import app.xquare.xquareopenapi.domain.meal.usecase.SyncNeisMealsUseCase
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.YearMonth

// 이번 달과 다음 달 급식을 나이스에서 가져와 저장한다. 실행 시각은 neis.meal-sync-cron(서울 시간)이다.
@Component
class NeisMealSyncScheduler(
    private val neisMealClient: NeisMealClient,
    private val syncNeisMealsUseCase: SyncNeisMealsUseCase,
    private val neisProperties: NeisProperties,
    private val clock: Clock,
) {

    @Scheduled(cron = "\${neis.meal-sync-cron}", zone = "Asia/Seoul")
    fun sync() {
        // 키 없이 부르면 나이스가 샘플 5건만 주므로, 키가 없으면 아예 부르지 않는다. 앱은 그대로 뜬다.
        if (neisProperties.apiKey.isBlank()) {
            log.warn("NEIS_API_KEY가 없어 나이스 급식 동기화를 건너뜁니다.")
            return
        }
        val thisMonth = YearMonth.now(clock)
        syncMonth(thisMonth)
        syncMonth(thisMonth.plusMonths(1))
    }

    // 한 달이 실패해도 다른 달은 동기화한다. 실패한 달은 다음 실행 때 다시 가져온다.
    private fun syncMonth(month: YearMonth) {
        try {
            val result = syncNeisMealsUseCase.execute(neisMealClient.fetchMonth(month))
            log.info("{} 나이스 급식 동기화: {}", month, result)
        } catch (e: RuntimeException) {
            log.warn("{} 나이스 급식 동기화에 실패했습니다.", month, e)
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(NeisMealSyncScheduler::class.java)
    }
}
