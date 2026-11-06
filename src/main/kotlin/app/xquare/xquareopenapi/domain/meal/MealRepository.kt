package app.xquare.xquareopenapi.domain.meal

import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import java.time.LocalDate

interface MealRepository : JpaRepository<Meal, Long> {
    fun existsByDate(date: LocalDate): Boolean

    fun findAllByDateBetweenOrderByDateAsc(startDate: LocalDate, finishDate: LocalDate): List<Meal>

    // 나이스 동기화가 쓴다. 관리자 수정과 겹쳐도 관리자 수정이 지워지지 않게 행을 잠그고 읽는다.
    // 먼저 수정한 쪽이 커밋할 때까지 기다린 뒤 최신 editedByAdmin을 보고, 동기화가 먼저 잠그면 관리자 수정이 그 뒤에 덮어쓴다.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    fun findAllByDateIn(dates: Collection<LocalDate>): List<Meal>
}
