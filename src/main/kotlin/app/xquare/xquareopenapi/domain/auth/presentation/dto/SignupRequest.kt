package app.xquare.xquareopenapi.domain.auth.presentation.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

@Schema(requiredProperties = ["username", "password", "studentNumber", "name", "email", "emailVerifiedToken"])
data class SignupRequest(
    @field:Pattern(regexp = "^[A-Za-z0-9_-]{4,15}$", message = "4~15자 영문/숫자/하이픈/언더스코어만 사용할 수 있습니다.")
    @field:Schema(description = "로그인 아이디(4~15자 영문/숫자/하이픈/언더스코어)", example = "Jun227")
    val username: String,

    @field:Pattern(regexp = PASSWORD_PATTERN, message = PASSWORD_MESSAGE)
    @field:Schema(description = "비밀번호(8~20자, 특수문자 포함, 공백 제외 ASCII)", minLength = 8, maxLength = 20, example = "Password1!")
    val password: String,

    @field:Min(1000)
    @field:Max(3999)
    @field:Schema(description = "학번(1000~3999)", example = "2315")
    val studentNumber: Int,

    @field:NotBlank
    @field:Size(max = 10)
    @field:Schema(description = "이름(1~10자)", example = "장윤호")
    val name: String,

    // OTP API와 같은 규칙으로 검사한다(앞뒤 공백 제거·소문자화 후 로컬 파트 허용 목록). 저장은 SignupUseCase가 정규화한 값으로 한다.
    @field:SchoolEmail
    @field:Schema(description = "인증을 완료한 이메일(@dsm.hs.kr)", format = "email", example = "example@dsm.hs.kr")
    val email: String,

    @field:NotBlank
    @field:Schema(description = "이메일 OTP 검증 API에서 받은 토큰", example = "3f2a9c1e-7b4d-4e8a-9c2f-1d5e6a7b8c9d")
    val emailVerifiedToken: String,
)
