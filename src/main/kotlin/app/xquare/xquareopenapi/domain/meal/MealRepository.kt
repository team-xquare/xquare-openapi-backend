package app.xquare.xquareopenapi.domain.meal

import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import java.time.LocalDate

interface MealRepository : JpaRepository<Meal, Long> {
    fun findByDate(date: LocalDate): Meal?

    // 급식 등록이 '급식 없음' 표시를 잠그기 전에 id만 읽는다. 엔티티를 읽지 않아야 잠근 뒤 읽은 값을 쓴다.
    @Query("select m.id from Meal m where m.date = :date")
    fun findIdByDate(date: LocalDate): Long?

    // 급식 등록이 '급식 없음' 표시 행을 고치기 전에 잠근다. 같은 표시에 동시에 등록하면 늦은 쪽이 먼저 고친 값을 보고 409가 된다.
    // 날짜가 아니라 id로 잠근다. MySQL에서 없는 날짜를 FOR UPDATE로 읽으면 gap lock이 걸려, 같은 날짜 동시 등록끼리 데드락이 난다.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from Meal m where m.id = :id")
    fun findByIdForUpdate(id: Long): Meal?

    // '급식 없음' 표시도 함께 나오므로 호출하는 쪽에서 뺀다.
    fun findAllByDateBetweenOrderByDateAsc(startDate: LocalDate, finishDate: LocalDate): List<Meal>

    // 나이스 동기화가 쓴다. 관리자 수정과 겹쳐도 관리자 수정이 지워지지 않게 행을 잠그고 읽는다.
    // 먼저 수정한 쪽이 커밋할 때까지 기다린 뒤 최신 editedByAdmin을 보고, 동기화가 먼저 잠그면 관리자 수정이 그 뒤에 덮어쓴다.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    fun findAllByDateIn(dates: Collection<LocalDate>): List<Meal>
}
