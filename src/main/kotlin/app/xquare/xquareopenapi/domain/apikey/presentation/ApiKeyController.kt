package app.xquare.xquareopenapi.domain.apikey.presentation

import app.xquare.xquareopenapi.domain.apikey.presentation.dto.ApiKeyRequest
import app.xquare.xquareopenapi.domain.apikey.presentation.dto.ApiKeyResponse
import app.xquare.xquareopenapi.domain.apikey.usecase.IssueApiKeyUseCase
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

// 로그인한 사용자 본인의 키만 다룬다. principal은 JwtAuthenticationFilter가 넣은 userId다.
@RestController
@RequestMapping("/api-keys")
class ApiKeyController(
    private val issueApiKeyUseCase: IssueApiKeyUseCase,
) {

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun issue(@AuthenticationPrincipal userId: Long, @RequestBody @Valid request: ApiKeyRequest): ApiKeyResponse =
        issueApiKeyUseCase.execute(userId, request)
}
