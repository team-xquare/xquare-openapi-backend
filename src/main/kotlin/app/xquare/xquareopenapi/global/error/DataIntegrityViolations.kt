package app.xquare.xquareopenapi.global.error

import org.hibernate.exception.ConstraintViolationException
import org.springframework.dao.DataIntegrityViolationException

// DB마다 제약 이름에 스키마·테이블 접두어나 대문자를 붙여 돌려주므로 포함 여부로 비교한다.
fun DataIntegrityViolationException.violates(constraintName: String): Boolean {
    val violated = (cause as? ConstraintViolationException)?.constraintName ?: return false
    return violated.contains(constraintName, ignoreCase = true)
}
