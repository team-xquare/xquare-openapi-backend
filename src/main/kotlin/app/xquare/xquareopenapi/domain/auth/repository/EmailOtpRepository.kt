package app.xquare.xquareopenapi.domain.auth.repository

import java.time.Duration

interface EmailOtpRepository {
    fun save(email: String, otp: String, ttl: Duration)
    fun consume(email: String, otp: String): OtpVerification
    fun delete(email: String, otp: String)
}

enum class OtpVerification { NOT_FOUND, MISMATCH, MATCHED }
