package app.xquare.xquareopenapi.global.validation

import jakarta.validation.Constraint
import jakarta.validation.ConstraintValidator
import jakarta.validation.ConstraintValidatorContext
import jakarta.validation.Payload
import java.time.LocalDate
import kotlin.reflect.KClass

// MySQL DATE 컬럼이 담을 수 있는 날짜(1000-01-01 ~ 9999-12-31)만 받는다. 어기면 400 INVALID_INPUT. null은 @NotNull이 따로 본다.
// ISO 날짜는 +999999999-12-31 같은 확장 연도도 읽혀서, 막지 않으면 다음 날 계산(plusDays)이 DateTimeException으로 500이 되거나
// 범위 밖 날짜를 저장할 때 MySQL이 거부해 500이 된다(#28 리뷰).
@Target(AnnotationTarget.FIELD)
@Retention(AnnotationRetention.RUNTIME)
@Constraint(validatedBy = [DbDateRangeValidator::class])
annotation class DbDateRange(
    val message: String = "1000-01-01부터 9999-12-31까지의 날짜여야 합니다.",
    val groups: Array<KClass<*>> = [],
    val payload: Array<KClass<out Payload>> = [],
)

class DbDateRangeValidator : ConstraintValidator<DbDateRange, LocalDate> {
    override fun isValid(value: LocalDate?, context: ConstraintValidatorContext): Boolean =
        value == null || value in MIN..MAX

    companion object {
        val MIN: LocalDate = LocalDate.of(1000, 1, 1)
        val MAX: LocalDate = LocalDate.of(9999, 12, 31)
    }
}
