package app.xquare.xquareopenapi.domain.meal.presentation.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import java.time.LocalDate

// 등록과 수정이 같이 쓴다. 수정도 모든 필드를 받아 통째로 바꾼다.
// 급식이 없는 끼니는 빈 배열로 보낸다. 메뉴는 끼니마다 20개, 하나에 50자까지이고 줄바꿈 같은 제어 문자는 받지 않는다.
// 세 끼를 모두 빈 배열로 보내면 그 날짜를 '급식 없음'으로 표시한다(Meal.isNoMeal).
@Schema(requiredProperties = ["date", "breakfast", "lunch", "dinner"])
data class MealRequest(
    @field:Schema(description = "급식 날짜", example = "2026-09-01")
    val date: LocalDate,

    @field:Size(max = MAX_MENUS)
    @field:Schema(description = "조식 메뉴(최대 20개, 각 1~50자)", example = """["볶음밥", "미역국"]""")
    val breakfast: List<@NotBlank @Size(max = MAX_MENU_LENGTH) @Pattern(regexp = NO_CONTROL_CHARACTERS) String>,

    @field:Size(max = MAX_MENUS)
    @field:Schema(description = "중식 메뉴(최대 20개, 각 1~50자)", example = """["백미밥", "된장찌개"]""")
    val lunch: List<@NotBlank @Size(max = MAX_MENU_LENGTH) @Pattern(regexp = NO_CONTROL_CHARACTERS) String>,

    @field:Size(max = MAX_MENUS)
    @field:Schema(description = "석식 메뉴(최대 20개, 각 1~50자)", example = """["카레라이스"]""")
    val dinner: List<@NotBlank @Size(max = MAX_MENU_LENGTH) @Pattern(regexp = NO_CONTROL_CHARACTERS) String>,
) {
    companion object {
        private const val MAX_MENUS = 20
        private const val MAX_MENU_LENGTH = 50
        private const val NO_CONTROL_CHARACTERS = "\\P{Cntrl}*"
    }
}
