package app.xquare.xquareopenapi.domain.meal.neis

import app.xquare.xquareopenapi.domain.meal.usecase.SyncNeisMealsUseCase
import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
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
            val neisMeals = neisMealClient.fetchMonth(month)
            val result = try {
                syncNeisMealsUseCase.execute(neisMeals)
            } catch (e: DataIntegrityViolationException) {
                // 비어 있던 날짜에 관리자 등록이 먼저 커밋되면 새로 만들던 급식이 unique 제약에 걸려 그 달 전체가 롤백된다.
                // 한 번만 다시 동기화한다. 다시 할 때는 관리자 급식이 보여서 그 날짜를 건너뛴다.
                log.info("{} 나이스 급식 동기화가 같은 날짜의 다른 저장과 겹쳐 다시 시도합니다.", month, e)
                syncNeisMealsUseCase.execute(neisMeals)
            }
            log.info("{} 나이스 급식 동기화: {}", month, result)
        } catch (e: RuntimeException) {
            log.warn("{} 나이스 급식 동기화에 실패했습니다.", month, e)
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(NeisMealSyncScheduler::class.java)
    }
}
