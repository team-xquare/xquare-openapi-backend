package app.xquare.xquareopenapi.domain.auth.presentation.dto

import app.xquare.xquareopenapi.domain.auth.usecase.isSchoolEmail
import jakarta.validation.Constraint
import jakarta.validation.ConstraintValidator
import jakarta.validation.ConstraintValidatorContext
import jakarta.validation.Payload
import kotlin.reflect.KClass

// 회원가입 이메일 검증. OTP 발송·검증과 같은 함수(isSchoolEmail: 앞뒤 공백 제거·소문자화 후 허용 목록 검사)를 쓴다.
// @Pattern은 원래 값에 정규식을 적용해 trim 여부가 OTP API와 달라지므로 쓰지 않는다.
@Target(AnnotationTarget.FIELD)
@Retention(AnnotationRetention.RUNTIME)
@Constraint(validatedBy = [SchoolEmailValidator::class])
annotation class SchoolEmail(
    val message: String = "@dsm.hs.kr 이메일만 사용할 수 있습니다.",
    val groups: Array<KClass<*>> = [],
    val payload: Array<KClass<out Payload>> = [],
)

class SchoolEmailValidator : ConstraintValidator<SchoolEmail, String> {
    override fun isValid(value: String?, context: ConstraintValidatorContext): Boolean =
        value != null && isSchoolEmail(value)
}
