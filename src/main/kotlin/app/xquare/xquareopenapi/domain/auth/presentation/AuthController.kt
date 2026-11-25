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
import app.xquare.xquareopenapi.domain.auth.presentation.dto.PasswordResetRequest
import app.xquare.xquareopenapi.domain.auth.usecase.ResetPasswordUseCase
import app.xquare.xquareopenapi.global.error.ErrorResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.headers.Header
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.security.SecurityRequirements
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@Tag(name = "인증/인가")
@RestController
@RequestMapping("/auth")
class AuthController(
    private val sendEmailOtpUseCase: SendEmailOtpUseCase,
    private val verifyEmailOtpUseCase: VerifyEmailOtpUseCase,
    private val signupUseCase: SignupUseCase,
    private val loginUseCase: LoginUseCase,
    private val reissueUseCase: ReissueUseCase,
    private val resetPasswordUseCase: ResetPasswordUseCase,
) {

    @SecurityRequirements
    @PostMapping("/email/otp", produces = [MediaType.APPLICATION_JSON_VALUE])
    @Operation(
        operationId = "sendEmailOtp",
        summary = "이메일 OTP 발송",
        description = "DSM 이메일(@dsm.hs.kr)로 6자리 인증코드를 발송합니다. 코드는 300초 TTL로 저장됩니다. " +
            "같은 이메일은 60초 쿨다운 및 1시간 5회, 같은 IP는 1시간 30회 제한이 있으며 5회 오입력 시 창 만료까지 차단됩니다. 권한: ALL.",
    )
    @ApiResponse(responseCode = "200", description = "인증코드 발송 성공")
    @ApiResponse(responseCode = "400", description = "INVALID_EMAIL_FORMAT / INVALID_INPUT", content = [Content(schema = Schema(implementation = ErrorResponse::class))])
    @ApiResponse(
        responseCode = "429",
        description = "OTP_RESEND_TOO_SOON / OTP_SEND_LIMIT_EXCEEDED / OTP_ATTEMPTS_EXCEEDED",
        headers = [Header(name = "Retry-After", description = "다시 요청할 수 있을 때까지 남은 시간(초, 올림)", schema = Schema(type = "integer", minimum = "1"))],
        content = [Content(schema = Schema(implementation = ErrorResponse::class))],
    )
    @ApiResponse(responseCode = "500", description = "EMAIL_SEND_FAILED / INTERNAL_SERVER_ERROR", content = [Content(schema = Schema(implementation = ErrorResponse::class))])
    // IP는 X-Forwarded-For를 직접 읽지 않고 remoteAddr를 쓴다. server.forward-headers-strategy=native(기본값)이면
    // Tomcat이 internal-proxies/trusted-proxies(SERVER_TOMCAT_REMOTEIP_*)에 든 프록시가 준 값만 remoteAddr에 반영한다.
    // 앞단에서 SNAT가 일어나거나 클라이언트가 보낸 X-Forwarded-For를 이어 붙이면 IP 상한이 깨지므로 앞단 조건은
    // docs/api-spec-notes.md("OTP 발송 제한")를 지켜야 한다.
    fun sendEmailOtp(@RequestBody @Valid request: EmailOtpSendRequest, httpRequest: HttpServletRequest): MessageResponse =
        sendEmailOtpUseCase.execute(request, httpRequest.remoteAddr)

    @SecurityRequirements
    @PostMapping("/email/otp/verify", produces = [MediaType.APPLICATION_JSON_VALUE])
    @Operation(
        operationId = "verifyEmailOtp",
        summary = "이메일 OTP 검증",
        description = "이메일로 받은 6자리 인증코드를 검증하고 600초 유효한 emailVerifiedToken을 발급합니다. 권한: ALL.",
    )
    @ApiResponse(responseCode = "200", description = "인증 성공")
    @ApiResponse(responseCode = "400", description = "OTP_NOT_FOUND / OTP_MISMATCH / INVALID_EMAIL_FORMAT / INVALID_INPUT", content = [Content(schema = Schema(implementation = ErrorResponse::class))])
    @ApiResponse(responseCode = "500", description = "INTERNAL_SERVER_ERROR", content = [Content(schema = Schema(implementation = ErrorResponse::class))])
    fun verifyEmailOtp(@RequestBody @Valid request: EmailOtpVerifyRequest): EmailVerifiedTokenResponse =
        verifyEmailOtpUseCase.execute(request)

    @SecurityRequirements
    @PostMapping("/signup", produces = [MediaType.APPLICATION_JSON_VALUE])
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
        operationId = "signup",
        summary = "회원가입",
        description = "이메일 OTP 검증으로 받은 emailVerifiedToken으로 계정을 생성하고 토큰을 발급합니다. 권한: ALL.",
    )
    @ApiResponse(responseCode = "201", description = "회원가입 성공")
    @ApiResponse(responseCode = "400", description = "INVALID_INPUT", content = [Content(schema = Schema(implementation = ErrorResponse::class))])
    @ApiResponse(responseCode = "403", description = "EMAIL_NOT_VERIFIED", content = [Content(schema = Schema(implementation = ErrorResponse::class))])
    @ApiResponse(responseCode = "409", description = "USERNAME_ALREADY_EXISTS / EMAIL_ALREADY_EXISTS", content = [Content(schema = Schema(implementation = ErrorResponse::class))])
    @ApiResponse(responseCode = "500", description = "INTERNAL_SERVER_ERROR", content = [Content(schema = Schema(implementation = ErrorResponse::class))])
    fun signup(@RequestBody @Valid request: SignupRequest): TokenResponse =
        signupUseCase.execute(request)

    @SecurityRequirements
    @PostMapping("/login", produces = [MediaType.APPLICATION_JSON_VALUE])
    @Operation(
        operationId = "login",
        summary = "로그인",
        description = "username과 password로 로그인하고 accessToken과 refreshToken을 발급합니다. 다시 로그인하면 이전 refreshToken은 폐기됩니다. 권한: ALL.",
    )
    @ApiResponse(responseCode = "200", description = "로그인 성공")
    @ApiResponse(responseCode = "400", description = "INVALID_INPUT", content = [Content(schema = Schema(implementation = ErrorResponse::class))])
    @ApiResponse(responseCode = "401", description = "INVALID_CREDENTIALS", content = [Content(schema = Schema(implementation = ErrorResponse::class))])
    @ApiResponse(responseCode = "500", description = "INTERNAL_SERVER_ERROR", content = [Content(schema = Schema(implementation = ErrorResponse::class))])
    fun login(@RequestBody @Valid request: LoginRequest): TokenResponse =
        loginUseCase.execute(request)

    @SecurityRequirements
    @PostMapping("/reissue", produces = [MediaType.APPLICATION_JSON_VALUE])
    @Operation(
        operationId = "reissueToken",
        summary = "토큰 재발급",
        description = "refreshToken으로 새 accessToken과 refreshToken을 발급합니다. 사용된 refreshToken은 폐기됩니다. 권한: ALL.",
    )
    @ApiResponse(responseCode = "200", description = "재발급 성공")
    @ApiResponse(responseCode = "400", description = "INVALID_INPUT", content = [Content(schema = Schema(implementation = ErrorResponse::class))])
    @ApiResponse(responseCode = "401", description = "INVALID_REFRESH_TOKEN", content = [Content(schema = Schema(implementation = ErrorResponse::class))])
    @ApiResponse(responseCode = "500", description = "INTERNAL_SERVER_ERROR", content = [Content(schema = Schema(implementation = ErrorResponse::class))])
    fun reissue(@RequestBody @Valid request: ReissueRequest): TokenResponse =
        reissueUseCase.execute(request)

    @SecurityRequirements
    @PostMapping("/password/reset", produces = [MediaType.APPLICATION_JSON_VALUE])
    @Operation(
        operationId = "resetPassword",
        summary = "비밀번호 재설정",
        description = "emailVerifiedToken(1회용)과 새 비밀번호로 비밀번호를 변경하고 기존 refreshToken을 폐기합니다. 권한: ALL.",
    )
    @ApiResponse(responseCode = "200", description = "재설정 성공")
    @ApiResponse(responseCode = "400", description = "INVALID_INPUT", content = [Content(schema = Schema(implementation = ErrorResponse::class))])
    @ApiResponse(responseCode = "403", description = "EMAIL_NOT_VERIFIED", content = [Content(schema = Schema(implementation = ErrorResponse::class))])
    @ApiResponse(responseCode = "404", description = "USER_NOT_FOUND", content = [Content(schema = Schema(implementation = ErrorResponse::class))])
    @ApiResponse(responseCode = "500", description = "INTERNAL_SERVER_ERROR", content = [Content(schema = Schema(implementation = ErrorResponse::class))])
    fun resetPassword(@RequestBody @Valid request: PasswordResetRequest): MessageResponse =
        resetPasswordUseCase.execute(request)
}
