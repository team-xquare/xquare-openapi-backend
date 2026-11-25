package app.xquare.xquareopenapi.domain.user.presentation

import app.xquare.xquareopenapi.domain.user.presentation.dto.MyInfoResponse
import app.xquare.xquareopenapi.domain.user.usecase.GetMyInfoUseCase
import app.xquare.xquareopenapi.global.error.ErrorResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.extensions.Extension
import io.swagger.v3.oas.annotations.extensions.ExtensionProperty
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.MediaType
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

// 로그인한 사용자(USER, ADMIN) 모두 쓸 수 있다. SecurityConfig의 anyRequest authenticated에 걸린다.
@Tag(name = "사용자")
@RestController
@RequestMapping("/users")
class UserController(
    private val getMyInfoUseCase: GetMyInfoUseCase,
) {

    // principal은 JwtAuthenticationFilter가 넣은 사용자 id다.
    @GetMapping("/me", produces = [MediaType.APPLICATION_JSON_VALUE])
    @Operation(
        operationId = "getMyInfo",
        summary = "내 정보 조회",
        description = "액세스 토큰의 사용자 id(sub)로 로그인한 사용자 자신의 정보를 조회합니다. 토큰은 유효하지만 해당 사용자가 없으면 401 INVALID_TOKEN입니다. 권한: USER, ADMIN.",
        extensions = [Extension(properties = [ExtensionProperty(name = "required-role", value = "USER")])],
    )
    @ApiResponse(responseCode = "200", description = "내 정보")
    @ApiResponse(responseCode = "401", description = "INVALID_TOKEN", content = [Content(schema = Schema(implementation = ErrorResponse::class))])
    @ApiResponse(responseCode = "500", description = "INTERNAL_SERVER_ERROR", content = [Content(schema = Schema(implementation = ErrorResponse::class))])
    fun getMyInfo(@AuthenticationPrincipal userId: Long): MyInfoResponse =
        getMyInfoUseCase.execute(userId)
}
