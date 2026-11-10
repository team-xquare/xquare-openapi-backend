package app.xquare.xquareopenapi.domain.auth.repository

import java.time.Duration

interface EmailOtpRepository {
    // 제한에 걸리면 저장하지 않고 이유와 남은 시간을 돌려준다. 저장했으면 null.
    fun save(email: String, ip: String, otp: String, ttl: Duration): OtpSendLimit?
    fun consume(email: String, otp: String): OtpVerification
    fun delete(email: String, otp: String)
}

enum class OtpVerification { NOT_FOUND, MISMATCH, MATCHED }

enum class OtpSendRejection { ATTEMPTS_EXCEEDED, EMAIL_LIMIT, IP_LIMIT, COOLDOWN }

data class OtpSendLimit(val reason: OtpSendRejection, val retryAfter: Duration)
