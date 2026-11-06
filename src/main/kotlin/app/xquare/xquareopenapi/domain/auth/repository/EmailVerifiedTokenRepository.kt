package app.xquare.xquareopenapi.domain.auth.repository

import java.time.Duration

interface EmailVerifiedTokenRepository {
    fun save(token: String, email: String, ttl: Duration)
    fun findEmailByToken(token: String): String?
    fun delete(token: String)
}
