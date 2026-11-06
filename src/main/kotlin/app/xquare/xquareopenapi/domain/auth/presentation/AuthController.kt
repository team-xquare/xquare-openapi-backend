package app.xquare.xquareopenapi.domain.auth.presentation

import app.xquare.xquareopenapi.domain.auth.presentation.dto.LoginRequest
import app.xquare.xquareopenapi.domain.auth.presentation.dto.ReissueRequest
import app.xquare.xquareopenapi.domain.auth.presentation.dto.SignupRequest
import app.xquare.xquareopenapi.domain.auth.presentation.dto.TokenResponse
import app.xquare.xquareopenapi.domain.auth.usecase.LoginUseCase
import app.xquare.xquareopenapi.domain.auth.usecase.ReissueUseCase
import app.xquare.xquareopenapi.domain.auth.usecase.SignupUseCase
import app.xquare.xquareopenapi.domain.auth.presentation.dto.EmailOtpSendRequest
import app.xquare.xquareopenapi.domain.auth.presentation.dto.EmailOtpVerifyRequest
import app.xquare.xquareopenapi.domain.auth.presentation.dto.EmailVerifiedTokenResponse
import app.xquare.xquareopenapi.domain.auth.presentation.dto.MessageResponse
import app.xquare.xquareopenapi.domain.auth.usecase.SendEmailOtpUseCase
import app.xquare.xquareopenapi.domain.auth.usecase.VerifyEmailOtpUseCase
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/auth")
class AuthController(
    private val sendEmailOtpUseCase: SendEmailOtpUseCase,
    private val verifyEmailOtpUseCase: VerifyEmailOtpUseCase,
    private val signupUseCase: SignupUseCase,
    private val loginUseCase: LoginUseCase,
    private val reissueUseCase: ReissueUseCase,
) {

    @PostMapping("/email/otp")
    // IP는 X-Forwarded-For를 직접 읽지 않고 remoteAddr를 쓴다. server.forward-headers-strategy=native(기본값)이면
    // Tomcat이 내부망 프록시가 준 값만 remoteAddr에 반영한다.
    fun sendEmailOtp(@RequestBody @Valid request: EmailOtpSendRequest, httpRequest: HttpServletRequest): MessageResponse =
        sendEmailOtpUseCase.execute(request, httpRequest.remoteAddr)

    @PostMapping("/email/otp/verify")
    fun verifyEmailOtp(@RequestBody @Valid request: EmailOtpVerifyRequest): EmailVerifiedTokenResponse =
        verifyEmailOtpUseCase.execute(request)

    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    fun signup(@RequestBody @Valid request: SignupRequest): TokenResponse =
        signupUseCase.execute(request)

    @PostMapping("/login")
    fun login(@RequestBody @Valid request: LoginRequest): TokenResponse =
        loginUseCase.execute(request)

    @PostMapping("/reissue")
    fun reissue(@RequestBody @Valid request: ReissueRequest): TokenResponse =
        reissueUseCase.execute(request)
}
