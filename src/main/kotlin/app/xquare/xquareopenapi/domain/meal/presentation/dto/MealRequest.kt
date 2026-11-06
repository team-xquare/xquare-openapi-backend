package app.xquare.xquareopenapi.domain.meal.presentation.dto

import jakarta.validation.constraints.AssertTrue
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import java.time.LocalDate

// 등록과 수정이 같이 쓴다. 수정도 모든 필드를 받아 통째로 바꾼다.
// 급식이 없는 끼니는 빈 배열로 보낸다. 메뉴는 끼니마다 20개, 하나에 50자까지이고 줄바꿈 같은 제어 문자는 받지 않는다.
data class MealRequest(
    val date: LocalDate,

    @field:Size(max = MAX_MENUS)
    val breakfast: List<@NotBlank @Size(max = MAX_MENU_LENGTH) @Pattern(regexp = NO_CONTROL_CHARACTERS) String>,

    @field:Size(max = MAX_MENUS)
    val lunch: List<@NotBlank @Size(max = MAX_MENU_LENGTH) @Pattern(regexp = NO_CONTROL_CHARACTERS) String>,

    @field:Size(max = MAX_MENUS)
    val dinner: List<@NotBlank @Size(max = MAX_MENU_LENGTH) @Pattern(regexp = NO_CONTROL_CHARACTERS) String>,
) {
    // 세 끼가 모두 비면 급식이 없는 날이다. 그런 날은 레코드를 만들지 않는다.
    @get:AssertTrue(message = "breakfast, lunch, dinner 중 하나에는 메뉴가 있어야 합니다.")
    val hasMenu: Boolean
        get() = breakfast.isNotEmpty() || lunch.isNotEmpty() || dinner.isNotEmpty()

    companion object {
        private const val MAX_MENUS = 20
        private const val MAX_MENU_LENGTH = 50
        private const val NO_CONTROL_CHARACTERS = "\\P{Cntrl}*"
    }
}
