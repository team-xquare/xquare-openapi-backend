package app.xquare.xquareopenapi.domain.meal.presentation

import app.xquare.xquareopenapi.domain.meal.presentation.dto.MealListResponse
import app.xquare.xquareopenapi.domain.meal.presentation.dto.MealRequest
import app.xquare.xquareopenapi.domain.meal.presentation.dto.MealResponse
import app.xquare.xquareopenapi.domain.meal.presentation.dto.MealSearchRequest
import app.xquare.xquareopenapi.domain.meal.usecase.CreateMealUseCase
import app.xquare.xquareopenapi.domain.meal.usecase.GetMealUseCase
import app.xquare.xquareopenapi.domain.meal.usecase.GetMealsUseCase
import app.xquare.xquareopenapi.domain.meal.usecase.UpdateMealUseCase
import app.xquare.xquareopenapi.global.dto.MessageResponse
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.net.URI

// 권한(조회는 공개, 등록·수정은 ADMIN)은 SecurityConfig에서 검사한다.
@RestController
@RequestMapping("/meals")
class MealController(
    private val getMealsUseCase: GetMealsUseCase,
    private val createMealUseCase: CreateMealUseCase,
    private val getMealUseCase: GetMealUseCase,
    private val updateMealUseCase: UpdateMealUseCase,
) {

    @GetMapping
    fun getMeals(@ModelAttribute request: MealSearchRequest): MealListResponse =
        getMealsUseCase.execute(request)

    @PostMapping
    fun createMeal(@RequestBody @Valid request: MealRequest): ResponseEntity<MessageResponse> {
        val mealId = createMealUseCase.execute(request)
        return ResponseEntity.created(URI.create("/meals/$mealId")).body(MessageResponse("생성되었습니다."))
    }

    @GetMapping("/{mealId}")
    fun getMeal(@PathVariable mealId: Long): MealResponse =
        getMealUseCase.execute(mealId)

    @PatchMapping("/{mealId}")
    fun updateMeal(@PathVariable mealId: Long, @RequestBody @Valid request: MealRequest): MessageResponse {
        updateMealUseCase.execute(mealId, request)
        return MessageResponse("수정되었습니다.")
    }
}
