package app.xquare.xquareopenapi.domain.meal

import jakarta.persistence.Column
import jakarta.persistence.Convert
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.LocalDate

@Entity
// 제약 이름은 동시 등록·수정으로 날짜가 겹쳤는지 구분하는 데 쓰므로 직접 정한다.
@Table(
    name = "meals",
    uniqueConstraints = [UniqueConstraint(name = Meal.DATE_UNIQUE_CONSTRAINT, columnNames = ["meal_date"])],
)
class Meal(
    date: LocalDate,
    breakfast: List<String>,
    lunch: List<String>,
    dinner: List<String>,
    editedByAdmin: Boolean,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null

    @Column(name = "meal_date", nullable = false)
    var date: LocalDate = date
        protected set

    // 메뉴 목록은 JSON 배열 문자열로 저장한다.
    @Convert(converter = MenuListConverter::class)
    @Column(nullable = false, length = MENU_COLUMN_LENGTH)
    var breakfast: List<String> = breakfast
        protected set

    @Convert(converter = MenuListConverter::class)
    @Column(nullable = false, length = MENU_COLUMN_LENGTH)
    var lunch: List<String> = lunch
        protected set

    @Convert(converter = MenuListConverter::class)
    @Column(nullable = false, length = MENU_COLUMN_LENGTH)
    var dinner: List<String> = dinner
        protected set

    // 관리자가 등록하거나 수정한 급식이면 true다. 나이스 동기화는 이 급식을 덮어쓰지 않는다.
    @Column(nullable = false)
    var editedByAdmin: Boolean = editedByAdmin
        protected set

    // 세 끼가 모두 비면 '급식 없음' 표시다. 관리자가 나이스 급식을 없애려고 남긴 행이라 조회에서 빼고 없는 급식으로 본다.
    // editedByAdmin이 true라서 나이스 동기화가 그 날짜를 다시 만들지 않는다. 나이스 동기화는 세 끼가 모두 빈 날을 저장하지 않는다.
    val isNoMeal: Boolean
        get() = breakfast.isEmpty() && lunch.isEmpty() && dinner.isEmpty()

    // 관리자가 수정한다. 이후 나이스 동기화가 이 급식을 덮어쓰지 않는다. 세 끼를 모두 비우면 '급식 없음' 표시가 된다.
    fun update(date: LocalDate, breakfast: List<String>, lunch: List<String>, dinner: List<String>) {
        this.date = date
        this.breakfast = breakfast
        this.lunch = lunch
        this.dinner = dinner
        this.editedByAdmin = true
    }

    // 나이스에서 가져온 메뉴로 바꾼다. 관리자가 손대지 않은 급식에만 쓴다.
    fun updateFromNeis(breakfast: List<String>, lunch: List<String>, dinner: List<String>) {
        this.breakfast = breakfast
        this.lunch = lunch
        this.dinner = dinner
    }

    companion object {
        const val DATE_UNIQUE_CONSTRAINT = "uk_meals_meal_date"

        fun noMeal(date: LocalDate) = Meal(date, emptyList(), emptyList(), emptyList(), editedByAdmin = true)

        // 메뉴 50자 × 20개를 JSON으로 바꿔도 들어가는 길이다(MealRequest 참고).
        private const val MENU_COLUMN_LENGTH = 3000
    }
}
