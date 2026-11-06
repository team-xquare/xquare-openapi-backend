package app.xquare.xquareopenapi.domain.meal.usecase

import app.xquare.xquareopenapi.domain.meal.Meal
import app.xquare.xquareopenapi.domain.meal.MealRepository
import app.xquare.xquareopenapi.domain.meal.neis.NeisMeal
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class SyncNeisMealsUseCase(
    private val mealRepository: MealRepository,
) {

    // 나이스에서 가져온 급식을 날짜별로 저장한다. 없는 날짜는 새로 만들고, 나이스에서 온 급식은 메뉴를 바꾼다.
    // 관리자가 등록하거나 수정한 날짜는 관리자가 고친 내용이 지워지지 않게 건너뛴다.
    @Transactional
    fun execute(neisMeals: List<NeisMeal>): MealSyncResult {
        val existing = mealRepository.findAllByDateIn(neisMeals.map(NeisMeal::date)).associateBy(Meal::date)
        val (editedByAdmin, notEdited) = neisMeals.filter { it.date in existing }.partition { existing.getValue(it.date).editedByAdmin }
        notEdited.forEach { existing.getValue(it.date).updateFromNeis(it.breakfast, it.lunch, it.dinner) }
        val created = neisMeals.filter { it.date !in existing }
        mealRepository.saveAll(created.map { Meal(it.date, it.breakfast, it.lunch, it.dinner, editedByAdmin = false) })
        return MealSyncResult(created = created.size, updated = notEdited.size, skipped = editedByAdmin.size)
    }
}

data class MealSyncResult(
    val created: Int,
    val updated: Int,
    val skipped: Int,
)
