package app.xquare.xquareopenapi.domain.apikey.presentation

import app.xquare.xquareopenapi.domain.apikey.presentation.dto.ApiKeyApplicationRequest
import app.xquare.xquareopenapi.domain.apikey.presentation.dto.ApiKeyApplicationResponse
import app.xquare.xquareopenapi.domain.apikey.presentation.dto.ApiKeyResponse
import app.xquare.xquareopenapi.domain.apikey.presentation.dto.MyApiKeyResponse
import app.xquare.xquareopenapi.domain.apikey.presentation.dto.ReissueApiKeyRequest
import app.xquare.xquareopenapi.domain.apikey.usecase.ApplyApiKeyUseCase
import app.xquare.xquareopenapi.domain.apikey.usecase.DeleteApiKeyUseCase
import app.xquare.xquareopenapi.domain.apikey.usecase.GetMyApiKeyUseCase
import app.xquare.xquareopenapi.domain.apikey.usecase.ReissueApiKeyUseCase
import app.xquare.xquareopenapi.global.dto.MessageResponse
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

// 로그인한 사용자 본인의 키만 다룬다. principal은 JwtAuthenticationFilter가 넣은 userId다.
@RestController
@RequestMapping("/api-keys")
class ApiKeyController(
    private val applyApiKeyUseCase: ApplyApiKeyUseCase,
    private val reissueApiKeyUseCase: ReissueApiKeyUseCase,
    private val deleteApiKeyUseCase: DeleteApiKeyUseCase,
    private val getMyApiKeyUseCase: GetMyApiKeyUseCase,
) {

    // 첫 신청이든 분야 추가든 신청 기록 하나가 생기므로 201이다.
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun apply(
        @AuthenticationPrincipal userId: Long,
        @RequestBody @Valid request: ApiKeyApplicationRequest,
    ): ApiKeyApplicationResponse =
        applyApiKeyUseCase.execute(userId, request)

    @PostMapping("/reissue")
    fun reissue(@AuthenticationPrincipal userId: Long, @RequestBody @Valid request: ReissueApiKeyRequest): ApiKeyResponse =
        reissueApiKeyUseCase.execute(userId, request)

    // 액세스 토큰으로 여는 GET이라 API 키 체인 대상이 아니다(ApiKeyReadEndpointCoverageTest의 예외 목록).
    @GetMapping("/me")
    fun getMine(@AuthenticationPrincipal userId: Long): MyApiKeyResponse =
        getMyApiKeyUseCase.execute(userId)

    // 키와 모든 신청 기록을 지운다. 명세가 이메일 인증을 신청·재발급에만 요구해서 여기서는 묻지 않는다.
    @DeleteMapping("/me")
    fun delete(@AuthenticationPrincipal userId: Long): MessageResponse {
        deleteApiKeyUseCase.execute(userId)
        return MessageResponse("삭제되었습니다.")
    }
}
