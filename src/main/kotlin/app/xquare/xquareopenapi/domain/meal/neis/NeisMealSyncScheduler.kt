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
                // 동기화가 빈 날짜를 읽은 뒤 그 날짜에 관리자 등록이 먼저 커밋되면, 새로 만들던 급식이 unique 제약에 걸려 그 달 전체가 롤백된다.
                // 이때 한 번만 다시 동기화한다. 다시 할 때는 관리자 급식이 보여서 그 날짜를 건너뛴다.
                // 운영 DB(MySQL, 기본 REPEATABLE READ)에서는 이 경로를 거의 타지 않는다. findAllByDateIn의 FOR UPDATE가 행이 없는 날짜에도
                // gap lock을 걸어서, 관리자 등록이 동기화 커밋까지 기다렸다가 409 MEAL_ALREADY_EXISTS를 받기 때문이다(#24 리뷰, MySQL로는 확인 안 함).
                // 이 경로는 빈 날짜를 잠그지 않는 H2에서만 확인했다. 격리 수준이 바뀌는 경우를 위한 안전장치로 남겨 둔다.
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
