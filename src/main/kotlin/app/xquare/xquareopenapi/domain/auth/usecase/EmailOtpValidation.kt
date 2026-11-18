package app.xquare.xquareopenapi.domain.auth.usecase

import app.xquare.xquareopenapi.global.error.ErrorCode
import app.xquare.xquareopenapi.global.error.XquareException

internal fun validateOtpEmail(email: String) {
    if (!Regex("""^[^@\s]+@dsm\.hs\.kr$""").matches(email)) {
        throw XquareException(ErrorCode.INVALID_EMAIL_FORMAT)
    }
}
