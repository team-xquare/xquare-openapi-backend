package app.xquare.xquareopenapi.domain.user

import app.xquare.xquareopenapi.global.error.ErrorCode
import org.hibernate.exception.ConstraintViolationException
import org.springframework.dao.DataIntegrityViolationException

// 동시 가입처럼 중복 검사를 통과한 요청이 unique 제약에 걸렸을 때, 어떤 값이 겹쳤는지 제약 이름으로 구분한다.
enum class UserUniqueConstraint(
    val constraintName: String,
    val errorCode: ErrorCode,
) {
    USERNAME(User.USERNAME_UNIQUE_CONSTRAINT, ErrorCode.USERNAME_ALREADY_EXISTS),
    EMAIL(User.EMAIL_UNIQUE_CONSTRAINT, ErrorCode.EMAIL_ALREADY_EXISTS),
    ;

    companion object {
        // DB마다 제약 이름에 스키마·테이블 접두어나 대문자를 붙여 돌려주므로 포함 여부로 비교한다.
        fun violatedBy(e: DataIntegrityViolationException): UserUniqueConstraint? {
            val violated = (e.cause as? ConstraintViolationException)?.constraintName ?: return null
            return entries.firstOrNull { violated.contains(it.constraintName, ignoreCase = true) }
        }
    }
}
