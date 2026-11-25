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
import app.xquare.xquareopenapi.global.error.ErrorResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.extensions.Extension
import io.swagger.v3.oas.annotations.extensions.ExtensionProperty
import io.swagger.v3.oas.annotations.headers.Header
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.security.SecurityRequirements
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springdoc.core.annotations.ParameterObject
import org.springframework.http.MediaType
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
@Tag(name = "급식")
@RestController
@RequestMapping("/meals")
class MealController(
    private val getMealsUseCase: GetMealsUseCase,
    private val createMealUseCase: CreateMealUseCase,
    private val getMealUseCase: GetMealUseCase,
    private val updateMealUseCase: UpdateMealUseCase,
) {

    @SecurityRequirements
    @GetMapping(produces = [MediaType.APPLICATION_JSON_VALUE])
    @Operation(
        operationId = "getMonthlyMeals",
        summary = "월간 급식 조회",
        description = "startDate~finishDate(최대 92일) 기간의 급식 목록을 날짜 순으로 반환합니다. 둘 다 생략하면 이번 달(서울 시간) 1일~말일입니다. 권한: ALL.",
    )
    @ApiResponse(responseCode = "200", description = "기간 내 급식 목록")
    @ApiResponse(responseCode = "400", description = "INVALID_DATE_RANGE / INVALID_INPUT", content = [Content(schema = Schema(implementation = ErrorResponse::class))])
    @ApiResponse(responseCode = "500", description = "INTERNAL_SERVER_ERROR", content = [Content(schema = Schema(implementation = ErrorResponse::class))])
    fun getMeals(@ParameterObject @ModelAttribute request: MealSearchRequest): MealListResponse =
        getMealsUseCase.execute(request)

    @PostMapping(produces = [MediaType.APPLICATION_JSON_VALUE])
    @Operation(
        operationId = "createMeal",
        summary = "급식 등록",
        description = "날짜별 급식을 등록합니다. 세 끼 모두 빈 배열이면 '급식 없음'으로 표시합니다. 권한: ADMIN.",
        extensions = [Extension(properties = [ExtensionProperty(name = "required-role", value = "ADMIN")])],
    )
    @ApiResponse(
        responseCode = "201",
        description = "생성됨",
        headers = [Header(name = "Location", description = "생성된 리소스를 상세조회하는 URI", schema = Schema(type = "string", format = "uri-reference", example = "/meals/1"))],
        content = [Content(schema = Schema(implementation = MessageResponse::class))],
    )
    @ApiResponse(responseCode = "400", description = "INVALID_INPUT", content = [Content(schema = Schema(implementation = ErrorResponse::class))])
    @ApiResponse(responseCode = "401", description = "INVALID_TOKEN", content = [Content(schema = Schema(implementation = ErrorResponse::class))])
    @ApiResponse(responseCode = "403", description = "FORBIDDEN", content = [Content(schema = Schema(implementation = ErrorResponse::class))])
    @ApiResponse(responseCode = "409", description = "MEAL_ALREADY_EXISTS / CONCURRENT_UPDATE", content = [Content(schema = Schema(implementation = ErrorResponse::class))])
    @ApiResponse(responseCode = "500", description = "INTERNAL_SERVER_ERROR", content = [Content(schema = Schema(implementation = ErrorResponse::class))])
    fun createMeal(@RequestBody @Valid request: MealRequest): ResponseEntity<MessageResponse> {
        val mealId = createMealUseCase.execute(request)
        return ResponseEntity.created(URI.create("/meals/$mealId")).body(MessageResponse("생성되었습니다."))
    }

    @SecurityRequirements
    @GetMapping("/{mealId}", produces = [MediaType.APPLICATION_JSON_VALUE])
    @Operation(
        operationId = "getMeal",
        summary = "급식 조회",
        description = "급식 정보 1건을 id로 조회합니다. '급식 없음'으로 표시된 급식은 404입니다. 권한: ALL.",
    )
    @ApiResponse(responseCode = "200", description = "급식 상세")
    @ApiResponse(responseCode = "400", description = "INVALID_INPUT", content = [Content(schema = Schema(implementation = ErrorResponse::class))])
    @ApiResponse(responseCode = "404", description = "MEAL_NOT_FOUND", content = [Content(schema = Schema(implementation = ErrorResponse::class))])
    @ApiResponse(responseCode = "500", description = "INTERNAL_SERVER_ERROR", content = [Content(schema = Schema(implementation = ErrorResponse::class))])
    fun getMeal(@Parameter(description = "조회할 급식 정보 id", example = "1") @PathVariable mealId: Long): MealResponse =
        getMealUseCase.execute(mealId)

    @PatchMapping("/{mealId}", produces = [MediaType.APPLICATION_JSON_VALUE])
    @Operation(
        operationId = "updateMeal",
        summary = "급식 수정",
        description = "급식 정보를 수정합니다. 등록과 같은 모든 필드를 받아 통째로 바꿉니다. 권한: ADMIN.",
        extensions = [Extension(properties = [ExtensionProperty(name = "required-role", value = "ADMIN")])],
    )
    @ApiResponse(responseCode = "200", description = "수정됨")
    @ApiResponse(responseCode = "400", description = "INVALID_INPUT", content = [Content(schema = Schema(implementation = ErrorResponse::class))])
    @ApiResponse(responseCode = "401", description = "INVALID_TOKEN", content = [Content(schema = Schema(implementation = ErrorResponse::class))])
    @ApiResponse(responseCode = "403", description = "FORBIDDEN", content = [Content(schema = Schema(implementation = ErrorResponse::class))])
    @ApiResponse(responseCode = "404", description = "MEAL_NOT_FOUND", content = [Content(schema = Schema(implementation = ErrorResponse::class))])
    @ApiResponse(responseCode = "409", description = "MEAL_ALREADY_EXISTS / CONCURRENT_UPDATE", content = [Content(schema = Schema(implementation = ErrorResponse::class))])
    @ApiResponse(responseCode = "500", description = "INTERNAL_SERVER_ERROR", content = [Content(schema = Schema(implementation = ErrorResponse::class))])
    fun updateMeal(
        @Parameter(description = "수정할 급식 정보 id", example = "1") @PathVariable mealId: Long,
        @RequestBody @Valid request: MealRequest,
    ): MessageResponse {
        updateMealUseCase.execute(mealId, request)
        return MessageResponse("수정되었습니다.")
    }
}
